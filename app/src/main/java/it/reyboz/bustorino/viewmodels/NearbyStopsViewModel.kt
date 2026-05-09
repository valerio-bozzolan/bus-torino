/*
	BusTO - View Model components
    Copyright (C) 2023 Fabio Mazza

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
package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.backend.GPSPoint
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.OldDataRepository
import java.util.ArrayList
import java.util.concurrent.Executors

class NearbyStopsViewModel(application: Application): AndroidViewModel(application) {

    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, application)


    val locationLiveData = MutableLiveData<GPSPoint>()
    val distanceMtLiveData = MutableLiveData<Int>(40)


    val stopsAtDistance = MediatorLiveData<ArrayList<Stop>>()

    private val dbUpdateRunning = MutableLiveData(false)

    private val callback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
            if(res.isSuccess){
                stopsAtDistance.postValue(res.result)
                if(BuildConfig.DEBUG)
                    Log.d(DEBUG_TAG, "Setting value of stops in bounding box")
            }
        }

    fun setLastLocation(location: Location) {
        locationLiveData.value = GPSPoint(location.latitude, location.longitude)
    }
    fun setDistance(distance: Int) {
        distanceMtLiveData.value = distance
    }
    fun setDBUpdateRunning(running: Boolean) {
        dbUpdateRunning.value = (running)
    }


    /**
     * Request stop in location [latitude], [longitude], at distance [distanceMeters]
     * If [saveValues] is true, store the position and the distance used
     */
    fun requestStopsAtDistance(latitude: Double, longitude: Double, distanceMeters: Int, saveValues: Boolean){
        if(saveValues){
            locationLiveData.postValue(GPSPoint(latitude, longitude))
            distanceMtLiveData.postValue(distanceMeters)
        }
        oldRepo.requestStopsWithinDistance(latitude, longitude, distanceMeters, callback)
    }

    /**
     * Request stops using the previously saved location
     */
    fun requestStopsAtDistance(distanceMeters: Int, saveValue: Boolean){
        if(saveValue){
            distanceMtLiveData.postValue(distanceMeters)
        }
        oldRepo.requestStopsWithinDistance(
            locationLiveData.value!!.latitude,
            locationLiveData.value!!.longitude, distanceMeters, callback)
    }

    fun requestStopsCheckDBRunning(position: GPSPoint, distanceMt: Int){
        if(dbUpdateRunning.value==null || !(dbUpdateRunning.value!!)){
            oldRepo.requestStopsWithinDistance(position.latitude, position.longitude, distanceMt, callback)
        } else{
            Log.d(DEBUG_TAG, "Database update is running, cannot do it")
        }
    }


    fun postLocation(location: Location){
        locationLiveData.postValue(GPSPoint(location.latitude, location.longitude))
    }
    fun postLocation(location: GPSPoint){
        locationLiveData.postValue(location)
    }
    fun postLastDistance(distanceMeters: Int){
        distanceMtLiveData.postValue(distanceMeters)
    }

    init {
        stopsAtDistance.addSource(locationLiveData){ point->
            if(BuildConfig.DEBUG) Log.d(DEBUG_TAG, "New location: $point")
            val distance = distanceMtLiveData.value ?: 40
            //oldRepo.requestStopsWithinDistance(point.latitude, point.longitude, distance, callback)
            requestStopsCheckDBRunning(point, distance)
        }

        stopsAtDistance.addSource(distanceMtLiveData){ dist->
            if(BuildConfig.DEBUG) Log.d(DEBUG_TAG, "New distance: $dist")

            if(locationLiveData.value != null){
                val point: GPSPoint = locationLiveData.value!!
                //oldRepo.requestStopsWithinDistance(point.latitude, point.longitude, dist, callback)
                requestStopsCheckDBRunning(point, dist)
            } else{
                Log.d(DEBUG_TAG, "Modified distance but locationLiveData value is null")
            }
        }
        stopsAtDistance.addSource(dbUpdateRunning){ running ->
            if(BuildConfig.DEBUG) Log.d(DEBUG_TAG, "DB update running: $running")
            if(!running) {
                reRequestStops()
            }
        }
    }


    private fun reRequestStops(){
        var req = false
        locationLiveData.value?.let{ point ->
            distanceMtLiveData.value ?.let { dist->
                req = true
                oldRepo.requestStopsWithinDistance(point.latitude, point.longitude, dist, callback)
            }

        }
        if(!req){
            Log.w(DEBUG_TAG, "Requested to rerun stops, but position or distance (or both) are null")
        }
    }


    companion object{
        private const val DEBUG_TAG = "BusTO-NearbyStopVwModel"
    }
}