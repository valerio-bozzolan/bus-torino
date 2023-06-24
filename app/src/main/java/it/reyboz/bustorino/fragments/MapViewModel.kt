/*
	BusTO  - View Models components
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
package it.reyboz.bustorino.fragments

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.*
import com.android.volley.Response
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.gtfs.GtfsPositionUpdate
import it.reyboz.bustorino.backend.gtfs.GtfsRtPositionsRequest
import it.reyboz.bustorino.data.*
import it.reyboz.bustorino.data.gtfs.GtfsTrip
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * View Model for the map. For containing the stops, the trips and whatever
 */
class MapViewModel(application: Application): AndroidViewModel(application) {
    private val gtfsRepo = GtfsRepository(application)
    private val executor = Executors.newFixedThreadPool(2)

    private val oldRepo= OldDataRepository(executor, NextGenDB.getInstance(application))
    private val matoRepository = MatoRepository(application)
    private val netVolleyManager = NetworkVolleyManager.getInstance(application)


    val positionsLiveData = MutableLiveData<ArrayList<GtfsPositionUpdate>>()
    private val positionsRequestRunning = MutableLiveData<Boolean>()


    private val positionRequestListener = object: GtfsRtPositionsRequest.Companion.RequestListener{
        override fun onResponse(response: ArrayList<GtfsPositionUpdate>?) {
            Log.i(DEBUG_TI,"Got response from the GTFS RT server")
            response?.let {it:ArrayList<GtfsPositionUpdate> ->
                if (it.size == 0) {
                    Log.w(DEBUG_TI,"No position updates from the server")
                    return
                }
                else {
                    //Log.i(DEBUG_TI, "Posting value to positionsLiveData")
                    viewModelScope.launch { positionsLiveData.postValue(it) }

                }
            }
            //whatever the result, launch again the update TODO

        }

    }
    private val positionRequestErrorListener = Response.ErrorListener {
        //error listener, it->VolleyError
        Log.e(DEBUG_TI, "Could not download the update, error:\n"+it.stackTrace)

        //TODO: launch again if needed

    }

    fun requestUpdates(){
        if(positionsRequestRunning.value == null || !positionsRequestRunning.value!!) {
            val request = GtfsRtPositionsRequest(positionRequestErrorListener, positionRequestListener)
            netVolleyManager.requestQueue.add(request)
            Log.i(DEBUG_TI, "Requested GTFS realtime position updates")
            positionsRequestRunning.value = true
        }

    }
    /*suspend fun requestDelayedUpdates(timems: Long){
        delay(timems)
        requestUpdates()
    }
     */
    fun requestDelayedUpdates(timems: Long){
        viewModelScope.launch {
            delay(timems)
            requestUpdates()
        }
    }

    // TRIPS IDS that have to be queried to the DB
    val tripsIDsInUpdates : LiveData<List<String>> = positionsLiveData.map {

        Log.i(DEBUG_TI, "positionsLiveData changed")
        //allow new requests for the positions of buses
        positionsRequestRunning.value = false
        //add "gtt:" prefix because it's implicit in GTFS Realtime API
        return@map it.map { pos -> "gtt:"+pos.tripID  }
    }
    // trips that are in the DB
    val gtfsTripsInDB = tripsIDsInUpdates.switchMap {
        //Log.i(DEBUG_TI, "tripsIds in updates changed: ${it.size}")
        gtfsRepo.gtfsDao.getTripPatternStops(it)
    }
    //trip IDs to query, which are not present in the DB
     val tripsGtfsIDsToQuery: LiveData<List<String>> = gtfsTripsInDB.map { tripswithPatterns ->
        val tripNames=tripswithPatterns.map { twp-> twp.trip.tripID }
        Log.i(DEBUG_TI, "Have ${tripswithPatterns.size} trips in the DB")
        if (tripsIDsInUpdates.value!=null)
        return@map tripsIDsInUpdates.value!!.filter { !tripNames.contains(it) }
        else {
            Log.e(DEBUG_TI,"Got results for gtfsTripsInDB but not tripsIDsInUpdates??")
            return@map ArrayList<String>()
        }
    }

    val updatesWithTripAndPatterns = gtfsTripsInDB.map { tripPatterns->
        Log.i(DEBUG_TI, "Mapping trips and patterns")
        val mdict = HashMap<String,Pair<GtfsPositionUpdate, TripAndPatternWithStops?>>()
        if(positionsLiveData.value!=null)
            for(update in positionsLiveData.value!!){
                val trID = update.tripID
                var found = false
                for(trip in tripPatterns){
                    if (trip.trip.tripID == "gtt:$trID"){
                        found = true
                        //insert directly
                        mdict[trID] = Pair(update,trip)
                        break
                    }
                }
                if (!found){
                    //give the update anyway
                    mdict[trID] = Pair(update,null)
                }
            }
        return@map mdict
    }
    /*
     There are two strategies for the queries, since we cannot query a bunch of tripIDs all together
     to Mato API -> we need to query each separately:
     1 -> wait until they are all queried to insert in the DB
     2 -> after each request finishes, insert it into the DB

     Keep in mind that trips do not change very often, so they might only need to be inserted once every two months
     TODO: find a way to avoid trips getting scrubbed (check if they are really scrubbed)
     */

    init {
        /*
        //what happens when the trips to query with Mato are determined
        tripsIDsQueried.addSource(tripsGtfsIDsToQuery) { tripList ->
            // avoid infinite loop of querying to Mato, insert in DB and
            // triggering another query update

            val tripsToQuery =
            Log.d(DEBUG_TI, "Querying trips IDs to Mato: $tripsToQuery")
            for (t in tripsToQuery){
                matoRepository.requestTripUpdate(t,matoTripReqErrorList, matoTripCallback)
                }
            tripsIDsQueried.value = tripsToQuery
            }

        tripsToInsert.addSource(tripsFromMato) { matoTrips ->
            if (tripsIDsQueried.value == null) return@addSource
            val tripsIdsToInsert = matoTrips.map { trip -> trip.tripID }

            //val setTripsToInsert = HashSet(tripsIdsToInsert)
            val tripsRequested = HashSet(tripsIDsQueried.value!!)
            val insertInDB = tripsRequested.containsAll(tripsIdsToInsert)
            if(insertInDB){
                gtfsRepo.gtfsDao.insertTrips(matoTrips)
            }
            Log.d(DEBUG_TI, "MatoTrips: ${matoTrips.size}, total trips req: ${tripsRequested.size}, inserting: $insertInDB")
        }
         */
        Log.d(DEBUG_TI, "MapViewModel created")
        Log.d(DEBUG_TI, "Observers of positionsLiveData ${positionsLiveData.hasActiveObservers()}")

        positionsRequestRunning.value = false;
    }
    fun testCascade(){
       val n  = ArrayList<GtfsPositionUpdate>()
        n.add(GtfsPositionUpdate("22920721U","lala","lalal","lol",1000.0f,1000.0f, 9000.0f,
            378192810192, GtfsPositionUpdate.VehicleInfo("aj","a"),
            null, null

            ))
        positionsLiveData.value = n
    }
    private val queriedMatoTrips = HashSet<String>()

    private val downloadedMatoTrips = ArrayList<GtfsTrip>()
    private val failedMatoTripsDownload = HashSet<String>()

    /**
     * Start downloading missing GtfsTrips and Insert them in the DB
     */
    fun downloadandInsertTripsInDB(trips: List<String>): Boolean{
        if (trips.isEmpty()) return false
        val workManager = WorkManager.getInstance(getApplication())
        val info = workManager.getWorkInfosForUniqueWork(MatoDownloadTripsWorker.WORK_TAG).get()

        val runNewWork = if(info.isEmpty()){
            true
        } else info[0].state!=WorkInfo.State.RUNNING && info[0].state!=WorkInfo.State.ENQUEUED
        val addDat = if(info.isEmpty())
            null else  info[0].state
        Log.d(DEBUG_TI, "Request to download and insert ${trips.size} trips, proceed: $runNewWork, workstate: $addDat")
        if(runNewWork) {
            val tripsArr = trips.toTypedArray()
            val data = Data.Builder().putStringArray(MatoDownloadTripsWorker.TRIPS_KEYS, tripsArr).build()
            val requ = OneTimeWorkRequest.Builder(MatoDownloadTripsWorker::class.java)
                .setInputData(data).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(MatoDownloadTripsWorker.WORK_TAG)
                .build()
            workManager.enqueueUniqueWork(MatoDownloadTripsWorker.WORK_TAG, ExistingWorkPolicy.KEEP, requ)
        }
        return true
    }

    companion object{
        const val DEBUG_TI="BusTO-MapViewModel"
        const val DEFAULT_DELAY_REQUESTS: Long=4000

    }
}