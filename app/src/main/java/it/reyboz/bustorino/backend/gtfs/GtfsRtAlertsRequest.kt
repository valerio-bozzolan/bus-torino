package it.reyboz.bustorino.backend.gtfs

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.google.transit.realtime.GtfsRealtime
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import it.reyboz.bustorino.backend.Fetcher
import it.reyboz.bustorino.backend.gtfs.GtfsRtPositionsRequest.RequestError

class GtfsRtAlertsRequest(
    errorListener: Response.ErrorListener,
    val listener:  Response.Listener<ArrayList<FeedEntity>>) :
    Request<ArrayList<FeedEntity>>(Method.GET, GtfsUtils.GTFSRT_URL_ALERTS, errorListener) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<ArrayList<FeedEntity>> {
        if (response == null){
            return Response.error(VolleyError("Response null"))
        }
        if (response.statusCode == 404){
            return Response.error(VolleyError("404"))
        }
        else if (response.statusCode != 200){
            return Response.error(VolleyError("200"))
        }

        val gtfsreq = GtfsRealtime.FeedMessage.parseFrom(response.data)

        val alerts = ArrayList<FeedEntity>()
        if(gtfsreq.hasHeader() && gtfsreq.entityCount>0){
            for (i in 0 until gtfsreq.entityCount) {
                val entity = gtfsreq.getEntity(i)

                if (entity.hasAlert()){
                    alerts.add(entity)
                }
            }
        }
        return Response.success(alerts, HttpHeaderParser.parseCacheHeaders(response))
    }

    override fun deliverResponse(p0: ArrayList<FeedEntity>) {
        listener.onResponse(p0)
    }

}