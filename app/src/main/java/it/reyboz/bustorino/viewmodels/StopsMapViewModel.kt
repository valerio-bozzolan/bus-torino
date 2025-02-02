package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import org.maplibre.android.geometry.LatLngBounds
import org.osmdroid.util.BoundingBox
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class StopsMapViewModel(application: Application): AndroidViewModel(application) {


    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, NextGenDB.getInstance(application))

    val stopsToShow = MutableLiveData(ArrayList<Stop>())
    private var stopsShownIDs = HashSet<String>()


    val stopsInBoundingBox = MutableLiveData<ArrayList<Stop>>()

    private val callback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
                if(res.isSuccess){
                    stopsInBoundingBox.postValue(res.result)
                    Log.d(DEBUG_TAG, "Setting value of stops in bounding box")
                }
        }

    private val addStopsCallback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
            if(res.isSuccess) res.result?.let{ newStops ->
                val stopsAdd = stopsToShow.value ?: ArrayList()
                for (s in newStops){
                    if (s.ID !in stopsShownIDs){
                        stopsShownIDs.add(s.ID)
                        stopsAdd.add(s)
                    }
                }

                stopsToShow.postValue(stopsAdd)
                Log.d(DEBUG_TAG, "Loaded ${stopsAdd.size} stops in total")
            }
        }

    fun requestStopsInBoundingBox(bb: BoundingBox) {
        bb.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latSouth, it.latNorth, it.lonWest, it.lonEast, callback)
        }
    }
    fun requestStopsInLatLng(bb: LatLngBounds) {
        bb.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latitudeSouth, it.latitudeNorth, it.longitudeWest, it.longitudeEast, callback)
        }
    }
    fun loadStopsInLatLngBounds(bb: LatLngBounds?){
        bb?.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latitudeSouth, it.latitudeNorth, it.longitudeWest, it.longitudeEast,
                addStopsCallback)
        }
    }
    companion object{
        private const val DEBUG_TAG = "BusTOStopMapViewModel"
    }
}