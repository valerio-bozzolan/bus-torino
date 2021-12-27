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
package it.reyboz.bustorino.backend.mato

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import it.reyboz.bustorino.backend.Palina
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class VolleyAllStopsRequest(
    listener: Response.Listener<List<Palina>>,
    errorListener: Response.ErrorListener,
) : MapiVolleyRequest<List<Palina>>(
    MatoAPIFetcher.QueryType.ALL_STOPS,listener, errorListener) {
    private val FEEDS = JSONArray()
    init {

        FEEDS.put("gtt")
    }
    override fun getBody(): ByteArray {
        val variables = JSONObject()
        variables.put("feeds", FEEDS)

        val data = MatoAPIFetcher.makeRequestParameters("AllStops", variables, MatoQueries.ALL_STOPS_BY_FEEDS)

        return data.toString().toByteArray()
    }

    override fun parseNetworkResponse(response: NetworkResponse?): Response<List<Palina>> {
        if (response==null)
            return Response.error(VolleyError("Null response"))
        else if(response.statusCode != 200)
            return Response.error(VolleyError("Response not ready, status "+response.statusCode))

        val stringResponse = String(response.data)
        val palinas = ArrayList<Palina>()

        try {
            val allData = JSONObject(stringResponse).getJSONObject("data")
            val allStops = allData.getJSONArray("stops")

            for (i in 0 until allStops.length()){
                val jsonStop = allStops.getJSONObject(i)
                palinas.add(MatoAPIFetcher.parseStopJSON(jsonStop))
            }

        } catch (e: JSONException){
            Log.e("VolleyBusTO","Cannot parse response as JSON")
            e.printStackTrace()
            return Response.error(VolleyError("Error parsing JSON"))

        }
        return Response.success(palinas, HttpHeaderParser.parseCacheHeaders(response))
    }
    companion object{
        val FEEDS_STR = arrayOf("gtt")

    }
}