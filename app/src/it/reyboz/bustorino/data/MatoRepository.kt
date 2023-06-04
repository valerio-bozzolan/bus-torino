/*
	BusTO  - Data components
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
package it.reyboz.bustorino.data

import android.content.Context
import android.util.Log
import com.android.volley.Response
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.mato.MatoQueries
import it.reyboz.bustorino.backend.mato.MatoVolleyJSONRequest
import it.reyboz.bustorino.backend.mato.ResponseParsing
import it.reyboz.bustorino.data.gtfs.GtfsTrip
import org.json.JSONException
import org.json.JSONObject

class MatoRepository(val mContext: Context) {
    private val netVolleyManager = NetworkVolleyManager.getInstance(mContext)
    fun requestTripUpdate(tripId: String, errorListener: Response.ErrorListener?,  callback: Callback<GtfsTrip>){
        val params = JSONObject()
        params.put("field",tripId)
        Log.i(DEBUG_TAG, "Requesting info for trip id: $tripId")
        netVolleyManager.addToRequestQueue(MatoVolleyJSONRequest(
            MatoQueries.QueryType.TRIP,params,{
                try {
                    val result = Result.success(ResponseParsing.parseTripInfo(it))
                    callback.onResultAvailable(result)
                } catch (e: JSONException){
                    // this might happen when the json is "{'data': {'trip': None}}"
                    callback.onResultAvailable(Result.failure(e))
                }
            },
            errorListener
        ))
    }

    fun interface Callback<T> {
        fun onResultAvailable(result: Result<T>)
    }
    companion object{
        final val DEBUG_TAG ="BusTO:MatoRepository"
    }
}