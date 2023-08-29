package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
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


    val stopsAtDistance = MutableLiveData<ArrayList<Stop>>()

    private val callback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
            if(res.isSuccess){
                stopsAtDistance.postValue(res.result)
                Log.d(DEBUG_TAG, "Setting value of stops in bounding box")
            }
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


    fun setLocation(location: Location){
        locationLiveData.postValue(GPSPoint(location.latitude, location.longitude))
    }
    fun setLocation(location: GPSPoint){
        locationLiveData.postValue(location)
    }
    fun setLastDistance(distanceMeters: Int){
        distanceMtLiveData.postValue(distanceMeters)
    }




    companion object{
        private const val DEBUG_TAG = "BusTO-NearbyStopVwModel"
    }
}