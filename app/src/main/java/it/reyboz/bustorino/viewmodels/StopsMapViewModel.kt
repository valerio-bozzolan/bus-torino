package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import org.osmdroid.util.BoundingBox
import java.util.ArrayList
import java.util.concurrent.Executors

class StopsMapViewModel(application: Application): AndroidViewModel(application) {


    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, NextGenDB.getInstance(application))
    /*
    private val boundingBoxLiveData = MutableLiveData<BoundingBox>()

    fun setStopBoundingBox(bb: BoundingBox){
        boundingBoxLiveData.value = bb
    }

     */

    val stopsInBoundingBox = MutableLiveData<ArrayList<Stop>>()

    private val callback =
        OldDataRepository.Callback<ArrayList<Stop>> { result ->
            result.let {
                if(it.isSuccess){
                    stopsInBoundingBox.postValue(it.result)
                    Log.d(DEBUG_TAG, "Setting value of stops in bounding box")
                }

            }
        }

    fun requestStopsInBoundingBox(bb: BoundingBox) {
        bb.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latSouth, it.latNorth, it.lonWest, it.lonEast, callback)
        }
    }
    companion object{
        private const val DEBUG_TAG = "BusTOStopMapViewModel"
    }
}