package it.reyboz.bustorino.fragments

import android.content.Context
import android.util.Log
import com.android.volley.NetworkError
import com.android.volley.ParseError
import com.android.volley.Response
import com.android.volley.VolleyError
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.Palina
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.mato.MapiArrivalRequest
import java.util.*
import kotlin.math.min

internal class NearbyArrivalsDownloader(context: Context, val listener: ArrivalsListener) : Response.Listener<Palina>,
    Response.ErrorListener {
    //final Map<String,List<Route>> routesToAdd = new HashMap<>();
    val nonEmptyPalinas = ArrayList<Palina>()
    val completedRequests: HashMap<String?, Boolean?> = HashMap<String?, Boolean?>()
    val volleyManager: NetworkVolleyManager = NetworkVolleyManager.getInstance(context)
    var activeRequestCount: Int = 0
    var reqErrorCount: Int = 0
    var reqSuccessCount: Int = 0


    fun requestArrivalsForStops(stops: List<Stop>): Int {
        val currentDate = Date()
        val timeRange = 3600
        val departures = 10
        var numreq = 0
        activeRequestCount = 0
        reqErrorCount = 0
        reqSuccessCount = 0
        nonEmptyPalinas.clear()
        completedRequests.clear()

        for (s in stops.subList(0, min(stops.size, MAX_ARRIVAL_STOPS))) {
            val req = MapiArrivalRequest(s.ID, currentDate, timeRange, departures, this, this)
            req.setTag(REQUEST_TAG)
            volleyManager.addToRequestQueue(req)
            activeRequestCount++
            numreq++
            completedRequests[s.ID] = false
        }
        listener.setProgress(reqErrorCount + reqSuccessCount, activeRequestCount)
        return numreq
    }

    private fun totalRequests(): Int {
        return activeRequestCount + reqSuccessCount + reqErrorCount
    }


    override fun onErrorResponse(error: VolleyError) {
        if (error is ParseError) {
            //TODO
            Log.w(DEBUG_TAG, "Parsing error for stop request")
        } else if (error is NetworkError) {
            val s: String?
            if (error.networkResponse != null) s = String(error.networkResponse.data)
            else s = ""
            Log.w(DEBUG_TAG, "Network error: " + s)
        } else {
            Log.w(DEBUG_TAG, "Volley Error: " + error.message)
        }
        if (error.networkResponse != null) {
            Log.w(DEBUG_TAG, "Error status code: " + error.networkResponse.statusCode)
        }
        //counters
        activeRequestCount--
        reqErrorCount++
        //flatProgressBar.setProgress(reqErrorCount + reqSuccessCount);
        listener.setProgress(reqErrorCount + reqSuccessCount, activeRequestCount)
    }

    override fun onResponse(palinaResult: Palina?) {
        //counter for requests
        activeRequestCount--
        reqSuccessCount++
        listener.setProgress(reqErrorCount + reqSuccessCount, activeRequestCount)

        //add the palina to the successful one
        if (palinaResult != null) {
            val routes = palinaResult.queryAllRoutes()
            if (routes != null && !routes.isEmpty()) {
                nonEmptyPalinas.add(palinaResult)
                listener.showCompletedArrivals(nonEmptyPalinas)
            }
        }
    }

    fun cancelAllRequests() {
        volleyManager.getRequestQueue().cancelAll(REQUEST_TAG)
        //flatProgressBar.setVisibility(View.GONE);
        listener.onAllRequestsCancelled()
    }

    interface ArrivalsListener {
        fun setProgress(completedRequests: Int, pendingRequests: Int)

        fun onAllRequestsCancelled()

        fun showCompletedArrivals(completedPalinas: ArrayList<Palina>)
    }

    companion object {
        private const val DEBUG_TAG: String = "BusTO-NearbyArrivDowns"
        const val REQUEST_TAG: String = "NearbyArrivals"
        private const val MAX_ARRIVAL_STOPS = 35

    }
}
