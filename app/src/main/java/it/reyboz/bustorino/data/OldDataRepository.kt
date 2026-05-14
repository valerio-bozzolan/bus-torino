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
import android.util.Log
import androidx.sqlite.SQLiteException
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.StopFavoritesData
import it.reyboz.bustorino.backend.utils
import java.util.ArrayList
import java.util.concurrent.Executor

class OldDataRepository(private val executor: Executor,
                        private val nextGenDB: NextGenDB,
            private val userDB: UserDB
    ) {

    constructor(executor: Executor, context: Context): this(executor, NextGenDB.getInstance(context), UserDB.getInstance(context))
    fun requestStopsWithGtfsIDs(
        gtfsIDs: List<String>,
        callback: Callback<ArrayList<Stop>>
    ) {
        executor.execute {
                //final NextGenDB dbHelper = new NextGenDB(context);
            val db = nextGenDB.readableDatabase
            val stopResult= NextGenDB.queryAllStopsWithGtfsIDs(db, gtfsIDs)
            //Result<List<Stop>> result = Result.success;
            callback.onComplete(stopResult)
        }
    }
    fun requestStopsWithIds(ids: List<String>, callback: Callback<ArrayList<Stop>>) {
        executor.execute {
            try {
                //final NextGenDB dbHelper = new NextGenDB(context);
                val db = nextGenDB.readableDatabase
                val stopsResult= NextGenDB.queryStopsWithStopIds(db, ids)
                //Result<List<Stop>> result = Result.success;
                callback.onComplete(stopsResult);
            } catch (e: Exception) {
                callback.onComplete(Result.failure(e))
            }
        }

    }

    fun getFavoritesData(ids: List<String>, callback: Callback<ArrayList<StopFavoritesData>>){
        executor.execute {
            try {
                val data =userDB.queryDataForStopIds(ids)
                Log.d(DEBUG_TAG, "received favorites data: $data")
                if(data != null){
                    val res = Result.success(data)
                    callback.onComplete(res)
                }
                else{
                    callback.onComplete(Result.failure(android.database.sqlite.SQLiteException()))
                }
            } catch (e: Exception) {
                callback.onComplete(Result.failure(e))
            }
        }
    }
    fun getFavoritesLiveData(): QueryLiveData<List<StopFavoritesData>> {
        return userDB.favoritesLiveData
    }
    fun getFavoritesLiveDataByStopId(ids: List<String>) = userDB.getLiveDataForStopIds(ids)

    fun getStopsForIdsLiveData(ids: List<String>): QueryLiveData<ArrayList<Stop>> {
        return nextGenDB.queryStopsWithStopIdsLiveData(ids)
    }

    fun requestStopsInArea(
        latitFrom: Double,
        latitTo: Double,
        longitFrom: Double,
        longitTo: Double,
        callback: Callback<ArrayList<Stop>>
    ){
        //Log.d(DEBUG_TAG, "Async Stop Fetcher started working");
        executor.execute {
            //var result = ArrayList<Stop>()
                callback.onComplete(nextGenDB.queryAllInsideMapView(
                    latitFrom, latitTo,
                    longitFrom, longitTo
                ))

        }

    }

    fun requestStopsInAreaLiveData(minLat: Double,
                                   maxLat: Double,
                                   minLong: Double,
                                   maxLong: Double): QueryLiveData<ArrayList<Stop>> {
        return nextGenDB.queryAllInsideMapViewLiveData(minLat, maxLat, minLong, maxLong)
    }

    /**
     * Request all the stops in position [latitude], [longitude], in the "square" with radius [distanceMeters]
     * Returns nothing, [callback] will be called if the query succeeds
     */
    fun requestStopsWithinDistance(latitude: Double, longitude: Double, distanceMeters: Int, callback: Callback<ArrayList<Stop>>){

        val latDelta = utils.latitudeDelta(distanceMeters.toDouble())
        val longDelta = utils.longitudeDelta(distanceMeters.toDouble(), latitude)

        requestStopsInArea(latitude-latDelta,
            latitude+latDelta, longitude-longDelta, longitude+longDelta, callback)
    }



    fun interface Callback<T> {
        fun onComplete(result: Result<T>)
    }

    companion object {
        private const val DEBUG_TAG = "BusTO-OldDataRepo"
    }
}
