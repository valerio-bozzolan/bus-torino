package it.reyboz.bustorino.backend.gtfs

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.google.transit.realtime.GtfsRealtime

class GtfsRealtimeRequest(url: String?,
                          errorListener: Response.ErrorListener?,
                          val listener: RequestListener) :
    Request<GtfsRealtime.FeedMessage>(Method.GET, url, errorListener) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<GtfsRealtime.FeedMessage> {
        if (response == null){
            return Response.error(VolleyError("Null response"))
        }

        if (response.statusCode != 200)
            return Response.error(VolleyError("Error code is ${response.statusCode}"))

        val gtfsreq = GtfsRealtime.FeedMessage.parseFrom(response.data)

        return Response.success(gtfsreq, HttpHeaderParser.parseCacheHeaders(response))
    }

    override fun deliverResponse(response: GtfsRealtime.FeedMessage?) {
        listener.onResponse(response)
    }

    companion object{
        const val URL_POSITION =  "http://percorsieorari.gtt.to.it/das_gtfsrt/vehicle_position.aspx"

        const val URL_TRIP_UPDATES ="http://percorsieorari.gtt.to.it/das_gtfsrt/trip_update.aspx"
        const val URL_ALERTS = "http://percorsieorari.gtt.to.it/das_gtfsrt/alerts.aspx"

        public interface RequestListener{
            fun onResponse(response: GtfsRealtime.FeedMessage?)
        }
    }


}