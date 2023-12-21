/*
	BusTO  - Backend components
    Copyright (C) 2023 Fabio Mazza

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
package it.reyboz.bustorino.backend.gtfs

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.google.transit.realtime.GtfsRealtime

class GtfsRtPositionsRequest(
                             errorListener: Response.ErrorListener?,
                             val listener: RequestListener) :
    Request<ArrayList<LivePositionUpdate>>(Method.GET, URL_POSITION, errorListener) {

    override fun parseNetworkResponse(response: NetworkResponse?): Response<ArrayList<LivePositionUpdate>> {
        if (response == null){
            return Response.error(VolleyError("Null response"))
        }

        if (response.statusCode != 200)
            return Response.error(VolleyError("Error code is ${response.statusCode}"))

        val gtfsreq = GtfsRealtime.FeedMessage.parseFrom(response.data)

        val positionList = ArrayList<LivePositionUpdate>()

        if (gtfsreq.hasHeader() && gtfsreq.entityCount>0){
            for (i in 0 until gtfsreq.entityCount){
                val entity = gtfsreq.getEntity(i)
                if (entity.hasVehicle()){
                    positionList.add(LivePositionUpdate(entity.vehicle))
                }
            }
        }

        return Response.success(positionList, HttpHeaderParser.parseCacheHeaders(response))
    }

    override fun deliverResponse(response: ArrayList<LivePositionUpdate>?) {
        listener.onResponse(response)
    }

    companion object{
        const val URL_POSITION =  "http://percorsieorari.gtt.to.it/das_gtfsrt/vehicle_position.aspx"

        const val URL_TRIP_UPDATES ="http://percorsieorari.gtt.to.it/das_gtfsrt/trip_update.aspx"
        const val URL_ALERTS = "http://percorsieorari.gtt.to.it/das_gtfsrt/alerts.aspx"

        public interface RequestListener{
            fun onResponse(response: ArrayList<LivePositionUpdate>?)
        }
    }


}