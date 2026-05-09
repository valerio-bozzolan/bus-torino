/*
	BusTO - View Model components
    Copyright (C) 2025 Fabio Mazza

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
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import org.maplibre.android.geometry.LatLngBounds
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class StopsMapViewModel(application: Application): AndroidViewModel(application) {


    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, NextGenDB.getInstance(application))

    val stopsToShow = MutableLiveData(ArrayList<Stop>())
    private var stopsShownIDs = HashSet<String>()
    private var allStopsLoaded = HashMap<String,Stop>()


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
                        allStopsLoaded[s.ID] = s
                    }
                }

                stopsToShow.postValue(stopsAdd)
                //Log.d(DEBUG_TAG, "Loaded ${stopsAdd.size} stops in total")
            }
        }

    fun getStopByID(id: String): Stop? {
        if (id in allStopsLoaded) return allStopsLoaded[id]
        else return null
    }

    fun getAllStopsLoaded(): ArrayList<Stop>{
        return ArrayList(allStopsLoaded.values)
    }

    /*fun requestStopsInBoundingBox(bb: BoundingBox) {
        bb.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latSouth, it.latNorth, it.lonWest, it.lonEast, callback)
        }
    }

     */
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

    //this is only saved at the end, is it really necessary?
    var lastUserLocation: Location? = null

    companion object{
        private const val DEBUG_TAG = "BusTOStopMapViewModel"
    }
}