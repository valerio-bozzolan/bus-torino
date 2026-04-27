package it.reyboz.bustorino.backend

import android.util.Log
import com.android.volley.Response
import com.android.volley.VolleyError

interface VolleyFetcherErrorResponder: Response.ErrorListener {

    fun onErrorResponse(error: VolleyFetcherError){

    }

    override fun onErrorResponse(p0: VolleyError?) {
        p0.let {
            if(p0 is VolleyFetcherError){
                onErrorResponse(p0 as VolleyFetcherError)
            }
            else{
                Log.e("VolleyFetcherError", "Error is not instance of VolleyFetcherError, ignoring")
            }
        }
    }
}