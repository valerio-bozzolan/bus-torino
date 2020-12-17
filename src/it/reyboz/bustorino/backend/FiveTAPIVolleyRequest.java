/*
	BusTO  - Backend components
    Copyright (C) 2019 Fabio Mazza

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

import androidx.annotation.Nullable;
import android.util.Log;
import com.android.volley.*;
import com.android.volley.toolbox.HttpHeaderParser;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class to handle request with the Volley Library
 */
public class FiveTAPIVolleyRequest extends Request<Palina> {
    private static final String LOG_TAG = "BusTO-FiveTAPIVolleyReq";

    private ResponseListener listener;
    final private String url,stopID;
    final private AtomicReference<Fetcher.result> resultRef;
    final private FiveTAPIFetcher fetcher;
    final private FiveTAPIFetcher.QueryType type;



    private FiveTAPIVolleyRequest(int method, String url, String stopID, FiveTAPIFetcher.QueryType kind,
                                 ResponseListener listener,
                                  @Nullable Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.url = url;
        this.resultRef = new AtomicReference<>();
        this.fetcher = new FiveTAPIFetcher();
        this.listener = listener;
        this.stopID = stopID;
        this.type = kind;
    }

    @Nullable
    public static FiveTAPIVolleyRequest getNewRequest(FiveTAPIFetcher.QueryType type, String stopID,
                                                      ResponseListener listener, @Nullable Response.ErrorListener errorListener){
        String url;
        try {
             url = FiveTAPIFetcher.getURLForOperation(type,stopID);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e(LOG_TAG,"Cannot get an URL for the operation");
            return null;
        }
        return new FiveTAPIVolleyRequest(Method.GET,url,stopID,type,listener,errorListener);
    }

    @Override
    protected Response<Palina> parseNetworkResponse(NetworkResponse response) {
        if(response.statusCode != 200)
            return Response.error(new VolleyError("Response Error Code "+response.statusCode));
        final String stringResponse = new String(response.data);
        List<Route> routeList;
        try{
            switch (type){
                case ARRIVALS:
                    routeList = fetcher.parseArrivalsServerResponse(stringResponse,resultRef);
                    break;
                case DETAILS:
                    routeList = fetcher.parseDirectionsFromResponse(stringResponse);
                    break;
                default:
                    //empty
                    return Response.error(new VolleyError("Invalid query type"));
            }
        } catch (JSONException e) {
            resultRef.set(Fetcher.result.PARSER_ERROR);
            //e.printStackTrace();
            Log.w("FivetVolleyParser","JSON Exception in parsing response of: "+url);
            return Response.error(new ParseError(response));
        }
        if(resultRef.get()== Fetcher.result.PARSER_ERROR){
            return Response.error(new ParseError(response));
        }
        final Palina p = new Palina(stopID);
        p.setRoutes(routeList);
        p.sortRoutes();
        return Response.success(p, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(Palina p) {
        listener.onResponse(p,type);
    }

    @Override
    public Map<String, String> getHeaders() {
       return  FiveTAPIFetcher.getDefaultHeaders();

    }
    //from https://stackoverflow.com/questions/21867929/android-how-handle-message-error-from-the-server-using-volley
    @Override
    protected VolleyError parseNetworkError(VolleyError volleyError){
        if(volleyError.networkResponse != null && volleyError.networkResponse.data != null){
            volleyError = new NetworkError(volleyError.networkResponse);
        }

        return volleyError;
    }

    public interface ResponseListener{
        void onResponse(Palina result, FiveTAPIFetcher.QueryType type);
    }

    //public interface ErrorListener extends Response.ErrorListener{}
}
