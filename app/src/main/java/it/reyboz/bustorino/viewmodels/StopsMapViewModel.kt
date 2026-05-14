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
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.OldDataRepository
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.BoundingBox
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs

class StopsMapViewModel(application: Application): AndroidViewModel(application) {


    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, application)

    //private var stopsShownIDs = HashSet<String>()
    //private var allStopsLoaded = HashMap<String,Stop>()

    private val boundingBoxLoaded = MutableLiveData<BoundingBox>()
    val stopsInBoundingBox = MutableLiveData<ArrayList<Stop>>()

    private val callback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
                if(res.isSuccess){
                    stopsInBoundingBox.postValue(res.result)
                    Log.d(DEBUG_TAG, "Setting value of stops in bounding box")
                }
        }

    fun getStopByID(id: String): Stop? {
        return stopsToShow.value?.firstOrNull{ s-> s.ID == id}
    }

    fun getAllStopsLoaded(): ArrayList<Stop>?{
        return stopsToShow.value
    }

    /*fun requestStopsInBoundingBox(bb: BoundingBox) {
        bb.let {
            Log.d(DEBUG_TAG, "Launching stop request")
            oldRepo.requestStopsInArea(it.latSouth, it.latNorth, it.lonWest, it.lonEast, callback)
        }
    }

     */
    private fun updateBoundingBox(boundingBox: BoundingBox){
        val current = boundingBoxLoaded.value
        if(current == null){
            boundingBoxLoaded.value = boundingBox
        } else{
            val bb = boundingBox
            val mix = BoundingBox.fromLngLats(Math.min(current.west(), bb.west()),
                Math.min(current.south(), bb.south()), Math.max(current.north(), bb.east()),
                Math.max(current.north(), bb.north()))

            boundingBoxLoaded.value = mix
        }
    }

    fun loadStopsInLatLngBounds(bb: LatLngBounds){
        val extra = 0.05
        val deltaLong = abs(bb.longitudeEast - bb.longitudeWest) * extra
        val deltaLat = abs(bb.latitudeNorth - bb.latitudeSouth) * extra

        val newBB = BoundingBox.fromLngLats(bb.longitudeWest - deltaLat,
            bb.latitudeSouth -deltaLong,
            bb.longitudeEast + deltaLong,
            bb.latitudeNorth +deltaLat)

        updateBoundingBox(newBB)
        /*Log.d(DEBUG_TAG, "Launching stop request")

        oldRepo.requestStopsInArea(bb.latitudeSouth-deltaLat, bb.latitudeNorth+deltaLat,
            bb.longitudeWest-deltaLong, bb.longitudeEast+deltaLong,
            addStopsCallback)

         */

    }

    /*private val addStopsCallback =
        OldDataRepository.Callback<ArrayList<Stop>> { res ->
            if(res.isSuccess) res.result?.let{ newStops ->
                //val stopsAdd = stopsToShow.value ?: ArrayList()
                /*for (s in newStops){
                    if (s.ID !in stopsShownIDs){
                        stopsShownIDs.add(s.ID)
                        stopsAdd.add(s)
                        allStopsLoaded[s.ID] = s
                    }
                }

                 */
                allStopsLoaded.clear()
                for(stop in newStops){
                    allStopsLoaded[stop.ID] = stop
                }
                Log.d(DEBUG_TAG, "Loaded ${newStops.size} stops")
                stopsToShow.postValue(newStops)
                //Log.d(DEBUG_TAG, "Loaded ${stopsAdd.size} stops in total")
            }
        }

     */
    /*stopsToShow.addSource(boundingBoxLoaded){
        oldRepo.requestStopsInArea(it.south(), it.north(),
            it.west(), it.east(),
            addStopsCallback)
    }

     */

    val stopsToShow = boundingBoxLoaded.switchMap {
        oldRepo.requestStopsInAreaLiveData(it.south(), it.north(), it.west(), it.east())
    }


    //this is only saved at the end, is it really necessary?
    var lastUserLocation: Location? = null

    companion object{
        private const val DEBUG_TAG = "BusTOStopMapViewModel"
    }
}