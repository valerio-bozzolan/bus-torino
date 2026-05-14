package it.reyboz.bustorino.data

import android.util.Log
import it.reyboz.bustorino.BuildConfig

/**
 * Invalidation tracker to use to make auto-updating LiveData from SQLite database
 */
class InvalidationTracker {
    private val tableObservers = mutableMapOf<String, MutableSet<Observer>>()

    fun addObserver(table: String, onInvalidate: Observer) {
        tableObservers.getOrPut(table) { mutableSetOf() }.add(onInvalidate)
    }

    fun removeObserver(table: String, onInvalidate: Observer) {
        tableObservers[table]?.remove(onInvalidate)
    }

    fun notifyInvalidation(vararg tables: String) {
        if(BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "gitpull")
            Log.d(DEBUG_TAG, "invalidating tables: ${tables.contentToString()}")
        tables.forEach { table ->
            tableObservers[table]?.forEach { it.onInvalidate() }
        }
    }

    fun interface Observer {
        fun onInvalidate()
    }
    companion object {
        const val DEBUG_TAG = "BusTO-InvalidTracker"
    }
}