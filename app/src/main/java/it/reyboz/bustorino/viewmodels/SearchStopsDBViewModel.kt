package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.util.StopSorterSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class SearchStopsDBViewModel(application: Application): AndroidViewModel(application) {

    private data class Request(val q: String, val byId: Boolean)

    private val nextGenDB = NextGenDB.getInstance(application)

    var showingSearchSuggestions = false


    private val queryLiveData = MutableLiveData(Request("", true))
    private val numberLimitStop = MutableLiveData(20)
    val queryResultsLiveData = queryLiveData.distinctUntilChanged().switchMap { r->
        liveData(viewModelScope.coroutineContext + Dispatchers.IO, timeoutInMs = 0) {
            if (r.q.isEmpty()) {
                //showQueryResult.postValue(false)
                emit(emptyList<Stop>())
            }
            else {
                delay(250.milliseconds)
                //showQueryResult.postValue(true)
                val list = nextGenDB.searchStopsByCodeOrName(r.q, r.byId)
                /*if (r.byId)
                    emit(list.sortedBy { it.ID })
                else
                    emit(list.sortedBy { it.numRoutesStopping }.reversed())

                 */
                emit(list.sortedWith(StopSorterSearch(r.byId)))
            }
        }
    }

    val showQueryResult = queryResultsLiveData.map { it.isNotEmpty() }

    fun searchStops(query: String, byId: Boolean){
        //Log.d(TAG, "setting query $query  with id $byId, was previously ${queryLiveData.value}")
        this.queryLiveData.value = Request(query, byId)
    }
    fun getQueryStops(): String?{
        return queryLiveData.value?.q
    }
    fun setLimitSearchStops(num: Int){
        numberLimitStop.value = num
    }


    val filteredStopsSearch = MediatorLiveData<List<Stop>>()
    private fun setFilteredStops(stops:List<Stop>, num: Int){
        filteredStopsSearch.postValue(stops.subList(0, min(num, stops.size)))
    }

    init {
        filteredStopsSearch.addSource(queryResultsLiveData) {
            setFilteredStops(it, numberLimitStop.value!!)
        }
        filteredStopsSearch.addSource(numberLimitStop) {
            queryResultsLiveData.value?.let{ stops ->
                setFilteredStops(stops, it)
            }
        }
    }

    fun saveOpenSearchSuggestions(){
        showingSearchSuggestions = showQueryResult.value?: false
    }

    companion object{
        private const val TAG = "BusTO-SearchStopsVM"
    }
}