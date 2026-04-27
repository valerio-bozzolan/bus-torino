/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi
    Copyright (C) 2026 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.reyboz.bustorino.backend;

import android.util.Log;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import com.android.volley.*;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.RequestFuture;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

public class GTTJSONFetcher extends ArrivalsFetcherContext  {
    private final String DEBUG_TAG = "GTTJSONFetcher-BusTO";
    @Override @NonNull
    public Palina ReadArrivalTimesAll(String stopID, AtomicReference<Result> res) {
        URL url;
        Palina p = new Palina(stopID);

        try {
            url = new URL("https://www.gtt.to.it/cms/index.php?option=com_gtt&task=palina.getTransitiOld&palina=" + URLEncoder.encode(stopID, "utf-8") + "&bacino=U&realtime=true&get_param=value");
        } catch (Exception e) {
            res.set(Result.PARSER_ERROR);
            return p;
        }


        /*content = networkTools.queryURL(url, res, headers);
        if(content == null) {
            Log.w("GTTJSONFetcher", "NULL CONTENT");
            return p;
        }

        try {
            json = new JSONArray(content);
        } catch(JSONException e) {
            Log.w(DEBUG_TAG, "Error parsing JSON: \n"+content);
            Log.w(DEBUG_TAG, e);
            res.set(Result.PARSER_ERROR);

            return p;
        }

         */
        if (appContext == null) {
            Log.w(DEBUG_TAG, "appContext is null");
            res.set(Result.PARSER_ERROR);
            return p;
        }

        boolean retry = true;
        RequestQueue queue = NetworkVolleyManager.getInstance(appContext).getRequestQueue();
        //use the volley class, max 5 tries
        RequestFuture<Palina> future;
        Request<Palina> request;
        Response.ErrorListener responder = error -> {
            //Log.w(DEBUG_TAG, "onErrorResponse: " + volleyError.getMessage());
            if(error instanceof VolleyFetcherError){
                Log.w(DEBUG_TAG, "Actual error: " + ((VolleyFetcherError) error).getReason());
            }
        };

        for (int i = 0; i < 2; i++) {
            future = RequestFuture.newFuture();
            request = new GTTRequest(stopID, url.toString(), responder, future, res);

            queue.add(request);

            try {
                p = future.get(10, SECONDS);
                retry = false;
            } catch (TimeoutException e) {
                Log.d(DEBUG_TAG, "Request timed out: " + res.get());
                retry = false;
                res.set(Result.CONNECTION_ERROR);
            } catch (InterruptedException | ExecutionException e) {
                Log.w(DEBUG_TAG, "Error: " + e + " status: " + res.get());
                res.set(Result.PARSER_ERROR);
            }

            if(!retry){
                break;
            }
        }

        return p;
    }


    @Override
    public Passaggio.Source getSourceForFetcher() {
        return Passaggio.Source.GTTJSON;
    }


    private final class GTTRequest extends Request<Palina> {
        private final String stopID;
        private final AtomicReference<Result> res;
        private final  Response.Listener<Palina> responder;

        public GTTRequest(String stopID, String URL,
                          @Nullable Response.ErrorListener errorListener,
                          Response.Listener<Palina> resp,
                          AtomicReference<Result> resu) {
            super(Method.GET, URL, errorListener);
            this.stopID = stopID;
            this.res = resu;
            responder = resp;
        }
        @Override
        protected Response<Palina> parseNetworkResponse(NetworkResponse networkResponse) {
            if (networkResponse == null) {
                return Response.error(new VolleyFetcherError(Result.PARSER_ERROR));
            }

            String data = new String(networkResponse.data);
            JSONArray json;
            try {
                json = new JSONArray(data);
                // returns [{"PassaggiRT":[],"Passaggi":[]}] for non existing stops!
                json.getJSONObject(0).getString("Linea"); // if we can get this, then there's something useful in the array.
            } catch(JSONException e) {
                Log.w(DEBUG_TAG, "No existing lines");
                res.set(Result.NOT_FOUND);
                return Response.error(new VolleyFetcherError(Result.NOT_FOUND));
            }

            int howManyRoutes = json.length();
            if(howManyRoutes == 0) {
                res.set(Result.EMPTY_RESULT_SET);
                return Response.error(new VolleyFetcherError(Result.EMPTY_RESULT_SET));
            }

            try {
                JSONObject thisroute;
                String routename, bacino;
                JSONArray passaggi;
                int howManyPassaggi;
                Palina p = new Palina(stopID);
                for(int i = 0; i < howManyRoutes; i++) {
                    thisroute = json.getJSONObject(i);
                    routename = thisroute.getString("Linea");
                    try {
                        bacino = thisroute.getString("Bacino");
                    } catch (JSONException ignored) { // if "Bacino" gets removed...
                        bacino = "U";
                    }
                    final Route r = new Route(routename, thisroute.getString("Direzione"),
                            "",
                            FiveTNormalizer.decodeType(routename, bacino));

                    passaggi = thisroute.getJSONArray("PassaggiRT");
                    howManyPassaggi = passaggi.length();
                    for(int j = 0; j < howManyPassaggi; j++) {
                        String mPassaggio = passaggi.getString(j);
                        if (mPassaggio.contains("__")){
                            mPassaggio = mPassaggio.replace("_", "");
                        }
                        r.addPassaggio(mPassaggio.concat("*"), Passaggio.Source.GTTJSON);
                    }


                    passaggi = thisroute.getJSONArray("PassaggiPR"); // now the non-real-time ones
                    howManyPassaggi = passaggi.length();
                    for(int j = 0; j < howManyPassaggi; j++) {
                        r.addPassaggio(passaggi.getString(j), Passaggio.Source.GTTJSON);
                    }
                    p.addRoute(r);
                }
                p.sortRoutes();
                res.set(Result.OK);

                return Response.success(p, HttpHeaderParser.parseCacheHeaders(networkResponse));
            } catch (JSONException e) {
                res.set(Result.PARSER_ERROR);
                Log.d(DEBUG_TAG, "Failed to parse response into JSON: " + e.getMessage());
                return Response.error(new VolleyFetcherError(Result.PARSER_ERROR));
            }
        }

        @Override
        public Map<String, String> getHeaders() {
            HashMap<String, String> headers = new HashMap<>();
            //headers.put("Referer","https://www.gtt.to.it/cms/percorari/urbano?view=percorsi&bacino=U&linea=15&Regol=GE");
            headers.put("Host", "www.gtt.to.it");
            return headers;
        }

        @Override
        protected void deliverResponse(Palina palina) {
            responder.onResponse(palina);
        }
    }
}
