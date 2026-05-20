package it.reyboz.bustorino.middleware

import android.util.Log
import it.reyboz.bustorino.backend.Fetcher
import it.reyboz.bustorino.backend.FiveTStopsFetcher
import it.reyboz.bustorino.backend.GTTStopsFetcher
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.StopsFinderByName
import it.reyboz.bustorino.fragments.FragmentHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

class StopSearcher(
    fragmentHelper: FragmentHelper,
) {

    private val helperRef = WeakReference(fragmentHelper)
    private val resultRef = AtomicReference<Fetcher.Result>(Fetcher.Result.PARSER_ERROR)

    private val finders = arrayOf(GTTStopsFetcher(), FiveTStopsFetcher())
    private var lastJob : Job? = null

    private suspend fun getData(query: String, r: RecursionHelper<StopsFinderByName>): List<Stop>? {
        if (helperRef.get() == null) return null
        if(query.isEmpty()) {
            resultRef.set(Fetcher.Result.QUERY_TOO_SHORT)
            return null
        }
        resultRef.set(Fetcher.Result.OK)
        Log.d(DEBUG_TAG, "Running with query " + query)

        //val results = ArrayList<Fetcher.Result>()
        //var resultsList: List<Stop?>
        val queryOk = query.trim { it <= ' ' }
        while (r.valid()) {

            val finder = r.getAndMoveForward()
            val resultsList = finder.FindByName(queryOk, resultRef)
            Log.d(DEBUG_TAG, "Result: " + resultRef.get() + ", " + resultsList.size + " stops")

            if (resultRef.get() == Fetcher.Result.OK) {
                return resultsList
            }
            //results.add(resultRef.get())
        }
        /*var emptyResults = true
        for (re in results) {
            if (re != Fetcher.Result.EMPTY_RESULT_SET) {
                emptyResults = false
                break
            }
        }
        if (emptyResults) {
            showResultAsync(Fetcher.Result.EMPTY_RESULT_SET)
        }

         */
        return listOf<Stop>()
    }


    private fun showError(result: Fetcher.Result) {
        val helper = helperRef.get() ?: return
        helper.showErrorMessage(result, SearchRequestType.STOPS)
    }

    fun runRequest(query: String, fetchers: Array<StopsFinderByName>?) {
        //start spinner

        helperRef.get()?.toggleSpinner(true)
        lastJob = CoroutineScope(Dispatchers.IO).launch{
            try {
                val r = RecursionHelper<StopsFinderByName>(fetchers ?: finders)
                val stopList = getData(query, r)
                if(stopList == null) {
                    withContext(Dispatchers.Main) {
                        showError(resultRef.get())
                    }
                }
                else if(stopList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showError(Fetcher.Result.EMPTY_RESULT_SET)
                    }
                } else{
                    //list of stops, non-null and not empty
                    withContext(Dispatchers.Main) {
                        showResult(stopList, query)
                    }
                }

            }catch (e: CancellationException) {
                Log.d(DEBUG_TAG, "Request cancelled")
                /*withContext(Dispatchers.Main) {
                    helperRef.get()?.toggleSpinner(false)
                }

                 */
            }
            withContext(Dispatchers.Main) {
                helperRef.get()?.toggleSpinner(false)
            }

        }
    }
    fun runRequest(query: String) {
        runRequest(query, null)
    }

    fun cancelLastRequest() {
        lastJob?.let{
            if(!it.isCompleted)
                it.cancel()
        }
    }
    fun showResult(stops:List<Stop>, query: String) {
        val helper = helperRef.get() ?: return
        helper.createStopListFragment(stops,query, true)
    }


    companion object {
        const val DEBUG_TAG = "BusTO-StopSearcher"
    }
}