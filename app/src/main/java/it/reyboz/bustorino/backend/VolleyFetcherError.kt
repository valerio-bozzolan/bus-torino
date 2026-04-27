package it.reyboz.bustorino.backend

import com.android.volley.VolleyError

class VolleyFetcherError(
    val reason: Fetcher.Result
) : VolleyError()