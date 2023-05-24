package it.reyboz.bustorino.backend.mato

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import org.json.JSONException
import org.json.JSONObject

class MatoVolleyJSONRequest(type: MatoQueries.QueryType,
                            val variables: JSONObject,
                            listener: Response.Listener<JSONObject>,
                            errorListener: Response.ErrorListener?)
    : MapiVolleyRequest<JSONObject>(type, listener, errorListener) {
    protected val requestName:String
    protected val requestQuery:String
    init {
        val dd = MatoQueries.getNameAndRequest(type)
        requestName = dd.first
        requestQuery = dd.second
    }

    override fun getBody(): ByteArray {

        val data = MatoAPIFetcher.makeRequestParameters(requestName, variables, requestQuery)

        return data.toString().toByteArray()
    }

    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        if (response==null)
            return Response.error(VolleyError("Null response"))
        else if(response.statusCode != 200)
            return Response.error(VolleyError("Response not ready, status "+response.statusCode))
        val obj:JSONObject
        try {
            obj = JSONObject(String(response.data)).getJSONObject("data")
        }catch (ex: JSONException){
            Log.e("BusTO-VolleyJSON","Cannot parse response as JSON")
            ex.printStackTrace()
            return Response.error(VolleyError("Error parsing JSON"))
        }

        return Response.success(obj, HttpHeaderParser.parseCacheHeaders(response))
    }

}