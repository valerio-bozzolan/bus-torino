package it.reyboz.bustorino.fragments;

import android.content.Context;
import android.util.Log;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import it.reyboz.bustorino.backend.NetworkVolleyManager;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.mato.MapiArrivalRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

class NearbyArrivalsDownloader implements Response.Listener<Palina>, Response.ErrorListener {
    final static String DEBUG_TAG = "BusTO-NearbyArrivDowns";
    //final Map<String,List<Route>> routesToAdd = new HashMap<>();
    final ArrayList<Palina> nonEmptyPalinas = new ArrayList<>();
    final HashMap<String, Boolean> completedRequests = new HashMap<>();
    final static String REQUEST_TAG = "NearbyArrivals";
    final NetworkVolleyManager volleyManager;
    int activeRequestCount = 0, reqErrorCount = 0, reqSuccessCount = 0;

    final ArrivalsListener listener;

    NearbyArrivalsDownloader(Context context, ArrivalsListener arrivalsListener) {
        volleyManager = NetworkVolleyManager.getInstance(context);

        listener = arrivalsListener;
        //flatProgressBar.setMax(numreq);
    }

    public int requestArrivalsForStops(List<Stop> stops){
        int MAX_ARRIVAL_STOPS = 35;
        Date currentDate = new Date();
        int timeRange = 3600;
        int departures = 10;
        int numreq = 0;
        activeRequestCount = 0;
        reqErrorCount = 0;
        reqSuccessCount = 0;
        nonEmptyPalinas.clear();
        completedRequests.clear();

        for (Stop s : stops.subList(0, Math.min(stops.size(), MAX_ARRIVAL_STOPS))) {

            final MapiArrivalRequest req = new MapiArrivalRequest(s.ID, currentDate, timeRange, departures, this, this);
            req.setTag(REQUEST_TAG);
            volleyManager.addToRequestQueue(req);
            activeRequestCount++;
            numreq++;
            completedRequests.put(s.ID, false);
        }
        listener.setProgress(reqErrorCount+reqSuccessCount, activeRequestCount);
        return numreq;
    }

    private int totalRequests(){
        return activeRequestCount + reqSuccessCount + reqErrorCount;
    }


    @Override
    public void onErrorResponse(VolleyError error) {
        if (error instanceof ParseError) {
            //TODO
            Log.w(DEBUG_TAG, "Parsing error for stop request");
        } else if (error instanceof NetworkError) {
            String s;
            if (error.networkResponse != null)
                s = new String(error.networkResponse.data);
            else s = "";
            Log.w(DEBUG_TAG, "Network error: " + s);
        } else {
            Log.w(DEBUG_TAG, "Volley Error: " + error.getMessage());
        }
        if (error.networkResponse != null) {
            Log.w(DEBUG_TAG, "Error status code: " + error.networkResponse.statusCode);
        }
        //counters
        activeRequestCount--;
        reqErrorCount++;
        //flatProgressBar.setProgress(reqErrorCount + reqSuccessCount);
        listener.setProgress(reqErrorCount + reqSuccessCount, activeRequestCount);
    }

    @Override
    public void onResponse(Palina palinaResult) {
        //counter for requests
        activeRequestCount--;
        reqSuccessCount++;
        listener.setProgress(reqErrorCount + reqSuccessCount, activeRequestCount);

        //add the palina to the successful one
        if(palinaResult!=null) {
            final List<Route> routes = palinaResult.queryAllRoutes();
            if (routes != null && !routes.isEmpty()) {
                nonEmptyPalinas.add(palinaResult);
                listener.showCompletedArrivals(nonEmptyPalinas);
            }
        }
    }

    void cancelAllRequests() {
        volleyManager.getRequestQueue().cancelAll(REQUEST_TAG);
        //flatProgressBar.setVisibility(View.GONE);
        listener.onAllRequestsCancelled();
    }

    public interface ArrivalsListener{
        void setProgress(int completedRequests, int pendingRequests);

        void onAllRequestsCancelled();

        void showCompletedArrivals(ArrayList<Palina> completedPalinas);
    }
}
