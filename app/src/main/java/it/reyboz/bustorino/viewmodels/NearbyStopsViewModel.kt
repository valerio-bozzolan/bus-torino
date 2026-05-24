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
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.android.volley.Response
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.backend.mato.MapiArrivalRequest
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.util.StopSorterByDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class NearbyStopsViewModel(application: Application): AndroidViewModel(application) {

    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, application)

    val arrivalsNearby = MutableLiveData<List<Palina>>()

    // map of arrivals by stopID
    private val arrivalsMapStopID = ConcurrentHashMap<String, Palina>()

    val progressPerc = MutableLiveData<Int>()

    val downloadingArrivals = MutableLiveData<Boolean>()
    val lastTimeFinished = AtomicLong(0)
    private var job : Job? = null

    /*private val arrivalsListener = object : NearbyArrivalsDownloader.ArrivalsListener {
        override fun setProgress(completedRequests: Int, pendingRequests: Int) {
            val totalReq = completedRequests + pendingRequests
            progressPerc.postValue( (completedRequests * 100) / totalReq )

            if(pendingRequests == 0)
                downloadingArrivals.postValue(false)
        }

        override fun onAllRequestsCancelled() {
            downloadingArrivals.postValue(false)
        }

        override fun showCompletedArrivals(completedPalinas: ArrayList<Palina>) {
            arrivalsNearby.postValue(completedPalinas)
        }

    }

     */

    //private val nearbyArrivalsDownloader = NearbyArrivalsDownloader(application,arrivalsListener )

    private val volleyManager = NetworkVolleyManager.getInstance(application)

    /**
     * Response listener for the requests
     */
    private val responseListener = Response.Listener<Palina> { p ->
        val key = p.ID
        arrivalsMapStopID[key] = p

        arrivalsNearby.postValue(arrivalsMapStopID.values.toList())

        val c = completedRequests.incrementAndGet()
        val r = runningRequests.decrementAndGet()
        updateProgressPost(c,errorRequests.get(), totalReqs.get())
        if(r==0){
           setFinishedPost()
        }
    }

    /**
     * Error listener for the requests
     */
    private val errorListener = Response.ErrorListener { error ->
        val e = errorRequests.incrementAndGet()
        val r = runningRequests.decrementAndGet()
        updateProgressPost(completedRequests.get(), e, totalReqs.get())
        if(r==0){
            setFinishedPost()
        }
        if(BuildConfig.DEBUG) Log.d(DEBUG_TAG,"query to palina, error "+ error.toString())
    }

    private fun setFinishedPost(){
        downloadingArrivals.postValue(false)
        //val time = System.currentTimeMillis()
        //lastTimeFinished.set(time)
        job?.cancel()
        job = viewModelScope.launch {
            delay(30.seconds)
            launch(Dispatchers.Main) {
                Log.d(DEBUG_TAG, "Updating arrivals from job")
                stopsAtDistance.value?.let {
                    requestArrivalsForStops(it)
                }
            }
        }
    }

    private val totalReqs = AtomicInteger(0)
    private val completedRequests = AtomicInteger(0)
    private val errorRequests = AtomicInteger(0)
    private val runningRequests = AtomicInteger(0)

    /**
     * Run new batch of requests
     */
    fun requestArrivalsForStops(stops: List<Stop>) {
        //nearbyArrivalsDownloader.requestArrivalsForStops(stops)
        if(runningRequests.get() > 0) {
            volleyManager.requestQueue.cancelAll(REQUEST_TAG)
        }
        val currentDate = Date()
        val timeRange = 3600
        val departures = 10
        totalReqs.set(stops.size)
        runningRequests.set(stops.size)
        completedRequests.set(0)
        errorRequests.set(0)
        arrivalsMapStopID.clear()
        for (s in stops) {
            val req = MapiArrivalRequest(s.ID, currentDate, timeRange, departures, responseListener, errorListener)
            req.setTag(REQUEST_TAG)
            volleyManager.addToRequestQueue(req)
        }
        downloadingArrivals.value = (true)
    }
    private fun updateProgressPost(completed: Int, error: Int, total: Int) {
        val done = completed + error
        progressPerc.postValue( (done * 100) / total )
    }
    //// ------- LOCATION STUFF ---------
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


    val arrivalsDecoupled = arrivalsNearby.map { palinas ->
        locationLiveData.value?.let { loc ->
            Collections.sort(palinas, StopSorterByDistance(loc))
        }

        var routesPairList = ArrayList<RouteWithStop>(10)
        //int maxNum = Math.min(MAX_STOPS, stopList.size());
        for (p in palinas) {
            //if there are no routes available, skip stop
            if (p.queryAllRoutes().isEmpty()) continue
            for (r in p.queryAllRoutes()) {
                //if there are no routes, should not do anything
                if (r.passaggi != null && !r.passaggi.isEmpty()) routesPairList.add(RouteWithStop(p, r))
            }
        }

        val pos = locationLiveData.value
        if(pos != null) {
            routesPairList.sortWith { p1, p2 ->
                comparePairsRoutesArrivals(p1,p2,pos)
            }
        }
        routesPairList
    }

    fun comparePairsRoutesArrivals(pair1: RouteWithStop, pair2: RouteWithStop, pos: GPSPoint) : Int{
        var delta = 0
        val stop1: Stop = pair1.stop
        val stop2: Stop = pair2.stop

        val dist1 = utils.measuredistanceBetween(
            pos.latitude, pos.longitude,
            stop1.getLatitude()!!, stop1.getLongitude()!!
        )
        val dist2 = utils.measuredistanceBetween(
            pos.latitude, pos.longitude,
            stop2.getLatitude()!!, stop2.getLongitude()!!
        )
        val passaggi1 = pair1.route.passaggi
        val passaggi2 = pair2.route.passaggi
        if (passaggi1.size <= 0 || passaggi2.size <= 0) {
            Log.e("ArrivalsStopAdapter", "Cannot compare: No arrivals in one of the stops")
        } else {
            Collections.sort(passaggi1)
            Collections.sort(passaggi2)

            /*int deltaOre = passaggi1.get(0).hh-passaggi2.get(0).hh;
    if(deltaOre>12)
        deltaOre -= 24;
    else if (deltaOre<-12)
        deltaOre  += 24;
    delta+=deltaOre*60 + passaggi1.get(0).mm-passaggi2.get(0).mm;

     */
            delta = passaggi1[0]!!.getDifferenceMinutes(passaggi2[0]!!).toInt()
        }
        delta += ((dist1 - dist2) * MINUTI_PER_METRO * DISTANCE_MULTIPLIER).toInt()

        return delta

    }

    override fun onCleared() {
        volleyManager.requestQueue.cancelAll(REQUEST_TAG)
        super.onCleared()
    }

    companion object{
        private const val DEBUG_TAG = "BusTO-NearbyStopVwModel"

        const val REQUEST_TAG: String = "NearbyArrivals"

        const val MINUTI_PER_METRO: Double = 6.0 / 100 //v = 5km/h
        const val DISTANCE_MULTIPLIER: Double = 2.0 / 3
    }
}