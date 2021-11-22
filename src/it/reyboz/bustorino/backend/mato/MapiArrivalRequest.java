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

import it.reyboz.bustorino.backend.Palina;

public class MapiArrivalRequest extends MapiVolleyRequest<Palina> {

    private final String stopName;
    private final Date startingTime;
    private final int timeRange, numberOfDepartures;

    public MapiArrivalRequest(String stopName, Date startingTime, int timeRange,
                              int numberOfDepartures,
                              Response.Listener<Palina> listener,
                              @Nullable Response.ErrorListener errorListener) {
        super(MatoAPIFetcher.QueryType.ARRIVALS, listener, errorListener);
        this.stopName = stopName;
        this.startingTime = startingTime;
        this.timeRange = timeRange;
        this.numberOfDepartures = numberOfDepartures;
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
            data.put("query", MatoAPIFetcher.QUERY_ARRIVALS);
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
        if(response.statusCode != 200)
            return  Response.error(new VolleyError("Response Error Code "+response.statusCode));
        final String stringResponse = new String(response.data);
        Palina p = null;

        try {
            JSONObject data = new JSONObject(stringResponse).getJSONObject("data");

            JSONArray allStopsFound = data.getJSONArray("stops");

            boolean haveManyResults = allStopsFound.length() > 1;
            for (int i=0; i<allStopsFound.length(); i++){
                final JSONObject currentObj = allStopsFound.getJSONObject(i);

                p = MatoAPIFetcher.Companion.parseStopJSON(currentObj);
                if (haveManyResults){
                    //check we got the right one
                    if (p.gtfsID == null){
                        continue;
                    } else if(p.gtfsID.contains("gtt:")){
                        //valid stop
                        break;
                    }
                }

            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("BusTO:MapiRequest", "Error parsing JSON: "+stringResponse);
            return Response.error(new VolleyError("Error parsing the response in JSON",
                    e));
        }
        return Response.success(p, HttpHeaderParser.parseCacheHeaders(response));
    }


    @Nullable
    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        return new HashMap<>();
    }
}
