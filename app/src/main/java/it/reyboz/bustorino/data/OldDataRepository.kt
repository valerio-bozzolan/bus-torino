/*
	BusTO - Data components
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
package it.reyboz.bustorino.data

import android.content.Context
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.Stop
import java.util.concurrent.Executor

class OldDataRepository(private val executor: Executor, private val nextGenDB: NextGenDB) {

    constructor(executor: Executor, context: Context): this(executor, NextGenDB.getInstance(context))
    fun requestStopsWithGtfsIDs(
        gtfsIDs: List<String?>?,
        callback: Callback<List<Stop>>
    ) {
        executor.execute {
            try {
                //final NextGenDB dbHelper = new NextGenDB(context);
                val db = nextGenDB.readableDatabase
                val stops: List<Stop> = NextGenDB.queryAllStopsWithGtfsIDs(db, gtfsIDs)
                //Result<List<Stop>> result = Result.success;
                callback.onComplete(Result.success(stops))
            } catch (e: Exception) {
                callback.onComplete(Result.failure(e))
            }
        }
    }

    fun requestStopsInArea(
        latitFrom: Double,
        latitTo: Double,
        longitFrom: Double,
        longitTo: Double,
        callback: Callback<java.util.ArrayList<Stop>>
    ){
        //Log.d(DEBUG_TAG, "Async Stop Fetcher started working");
        executor.execute {
            val stops = nextGenDB.queryAllInsideMapView(
                latitFrom, latitTo,
                longitFrom, longitTo
            )
            if (stops!=null)
                callback.onComplete(Result.success(stops))
        }

    }



    fun interface Callback<T> {
        fun onComplete(result: Result<T>)
    }
}
