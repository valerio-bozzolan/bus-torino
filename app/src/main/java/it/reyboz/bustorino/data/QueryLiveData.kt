package it.reyboz.bustorino.data

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Class to observe the result of queries from database
 */
class QueryLiveData<T>(
    private val tablesToObserve: List<String>,
    private val tracker: InvalidationTracker,
    private val queryRunner: () -> T
) : LiveData<T>() {

    private val liveDataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val invalidationCallback = InvalidationTracker.Observer { fetchData() }

    override fun onActive() {
        tablesToObserve.forEach {
            tracker.addObserver(it, invalidationCallback)
        }
        fetchData()
    }

    override fun onInactive() {
        tablesToObserve.forEach {
            tracker.removeObserver(it, invalidationCallback)
        }
        liveDataScope.coroutineContext.cancelChildren() // Cancel any in-flight queries
    }

    private fun fetchData() {
        liveDataScope.coroutineContext.cancelChildren()
        liveDataScope.launch {
            val newValue = queryRunner()
            if(newValue == null){
                Log.w("BusTO-QueryLiveData", "Attempting to post value but it is null, tables $tablesToObserve")
            }
            postValue(newValue) // postValue is safe to call from a background thread
        }
    }
}