/*
	BusTO  - Backend components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino.backend.mato;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;

public class MapiArrivalRequest extends MapiVolleyRequest<Palina> {

    private final String stopName;
    private final Date startingTime;
    private final int timeRange, numberOfDepartures;
    private final AtomicReference<Fetcher.Result> reqRes;

    public MapiArrivalRequest(String stopName, Date startingTime, int timeRange,
                              int numberOfDepartures,
                              AtomicReference<Fetcher.Result> res,
                              Response.Listener<Palina> listener,
                              @Nullable Response.ErrorListener errorListener) {
        super(MatoAPIFetcher.QueryType.ARRIVALS, listener, errorListener);
        this.stopName = stopName;
        this.startingTime = startingTime;
        this.timeRange = timeRange;
        this.numberOfDepartures = numberOfDepartures;
        this.reqRes = res;
    }
    public MapiArrivalRequest(String stopName, Date startingTime, int timeRange,
                              int numberOfDepartures,
                              Response.Listener<Palina> listener,
                              @Nullable Response.ErrorListener errorListener) {
        this(stopName, startingTime, timeRange, numberOfDepartures,
                new AtomicReference<>(), listener, errorListener);
    }

    @Nullable
    @Override
    public byte[] getBody() throws AuthFailureError {
        JSONObject variables = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("operationName","AllStopsDirect");
            variables.put("name", stopName);
            variables.put("startTime", (long) startingTime.getTime()/1000);
            variables.put("timeRange", timeRange);
            variables.put("numberOfDepartures", numberOfDepartures);


            data.put("variables", variables);
            data.put("query", MatoQueries.QUERY_ARRIVALS);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new AuthFailureError("Error with JSON enconding",e);
        }
        String requestBody = data.toString();
        Log.d("MapiArrivalBusTO", "Request variables: "+ variables);
        return requestBody.getBytes();
    }


    @Override
    protected Response<Palina> parseNetworkResponse(NetworkResponse response) {
        if(response.statusCode != 200) {
            reqRes.set(Fetcher.Result.SERVER_ERROR);
            return Response.error(new VolleyError("Response Error Code " + response.statusCode));
        }
        final String stringResponse = new String(response.data);
        Palina p = null;

        try {
            JSONObject data = new JSONObject(stringResponse).getJSONObject("data");

            JSONArray allStopsFound = data.getJSONArray("stops");

            boolean stopFound = false;
            for (int i=0; i<allStopsFound.length(); i++){
                final JSONObject currentObj = allStopsFound.getJSONObject(i);

                p = MatoAPIFetcher.Companion.parseStopJSON(currentObj);
                if (p.gtfsID != null) {
                        if(p.gtfsID.contains("gtt:")){
                            //valid stop
                            stopFound = true;
                            break;
                        }
                }

            }
            if (!stopFound){
                Log.w("MapiArrival-Busto", "No stop found: "+p);
                reqRes.set(Fetcher.Result.NOT_FOUND);
                return Response.error(new VolleyError("Stop not found"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("BusTO:MapiRequest", "Error parsing JSON: "+stringResponse);
            reqRes.set(Fetcher.Result.PARSER_ERROR);
            return Response.error(new VolleyError("Error parsing the response in JSON",
                    e));
        }
        reqRes.set(Fetcher.Result.OK);
        return Response.success(p, HttpHeaderParser.parseCacheHeaders(response));
    }

    public class StopNotFoundError extends VolleyError{

        public StopNotFoundError(String message) {
            super(message);
        }

        public StopNotFoundError() {
            super();
        }
    }
}
