/*
	BusTO  - ViewModel components
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
import android.util.Log
import androidx.lifecycle.*
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.gtfs.GtfsRtPositionsRequest
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.MatoPatternsDownloadWorker
import it.reyboz.bustorino.data.MatoTripsDownloadWorker
import it.reyboz.bustorino.data.gtfs.MatoPattern
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

typealias FullPositionUpdatesMap = HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>>
typealias FullPositionUpdate = Pair<LivePositionUpdate, TripAndPatternWithStops?>

class LivePositionsViewModel(application: Application): AndroidViewModel(application) {

    private val gtfsRepo = GtfsRepository(application)

    //private val updates = UpdatesMap()
    private val positionsToBeMatchedLiveData = MutableLiveData<ArrayList<LivePositionUpdate>>()
    private val netVolleyManager = NetworkVolleyManager.getInstance(application)


    private var mqttClient = MQTTMatoClient()

    private var lineListening = ""
    private var lastTimeReceived: Long = 0

    private val gtfsRtRequestRunning = MutableLiveData<Boolean>(false)

    private val lastFailedTripsRequest = HashMap<String, Date>()
    private val workManager = WorkManager.getInstance(application)

    private var lastRequestedDownloadTrips = MutableLiveData<List<String>>()

    //INPUT FILTER FOR LINE
    private var gtfsLineToFilterPos = MutableLiveData<Pair<String,MatoPattern?>>()

    fun setGtfsLineToFilterPos(line: String, pattern: MatoPattern?){
        gtfsLineToFilterPos.value = Pair(line, pattern)
    }

    var isLastWorkResultGood =  workManager
        .getWorkInfosForUniqueWorkLiveData(MatoTripsDownloadWorker.TAG_TRIPS).map { it ->
            if (it.isEmpty()) return@map false
            var res = true
            if(it[0].state == WorkInfo.State.FAILED){
                val currDate = Date()
                res =  false
                lastRequestedDownloadTrips.value?.let { trips->
                    for(tr in trips){
                        lastFailedTripsRequest[tr] = currDate
                    }
                }

            }
            return@map res
    }
    /**
     * Responder to the MQTT Client
     */
    private val matoPositionListener = MQTTMatoClient.Companion.MQTTMatoListener{

        val mupds = ArrayList<LivePositionUpdate>()
        if(lineListening==MQTTMatoClient.LINES_ALL){
            for(sdic in it.values){
                for(update in sdic.values){
                    mupds.add(update)
                }
            }
        } else{
            //we're listening to one
            if (it.containsKey(lineListening.trim()) ){
                for(up in it[lineListening]?.values!!){
                    mupds.add(up)
                }
            }
        }
        val time = System.currentTimeMillis()
        if(lastTimeReceived == (0.toLong()) || (time-lastTimeReceived)>500){
            positionsToBeMatchedLiveData.postValue(mupds)
            lastTimeReceived = time
        }

    }

    //find the trip IDs in the updates
    private val tripsIDsInUpdates = positionsToBeMatchedLiveData.map { it ->
        //Log.d(DEBUG_TI, "Updates map has keys ${upMap.keys}")
        it.map { pos -> "gtt:"+pos.tripID  }

    }
    // get the trip IDs in the DB
    private val gtfsTripsPatternsInDB = tripsIDsInUpdates.switchMap {
        //Log.i(DEBUG_TI, "tripsIds in updates: ${it.size}")
        gtfsRepo.gtfsDao.getTripPatternStops(it)
    }
    //trip IDs to query, which are not present in the DB
    //REMEMBER TO OBSERVE THIS IN THE MAP
    val tripsGtfsIDsToQuery: LiveData<List<String>> = gtfsTripsPatternsInDB.map { tripswithPatterns ->
        val tripNames=tripswithPatterns.map { twp-> twp.trip.tripID }
        Log.i(DEBUG_TI, "Have ${tripswithPatterns.size} trips in the DB")
        if (tripsIDsInUpdates.value!=null)
            return@map tripsIDsInUpdates.value!!.filter { !(tripNames.contains(it) || it.contains("null"))}
        else {
            Log.e(DEBUG_TI,"Got results for gtfsTripsInDB but not tripsIDsInUpdates??")
            return@map ArrayList<String>()
        }
    }

    // unify trips with updates
    val updatesWithTripAndPatterns = gtfsTripsPatternsInDB.map { tripPatterns->
        Log.i(DEBUG_TI, "Mapping trips and patterns")
        val mdict = HashMap<String,FullPositionUpdate>()
        //missing patterns
        val routesToDownload = HashSet<String>()
        if(positionsToBeMatchedLiveData.value!=null)
            for(update in positionsToBeMatchedLiveData.value!!){

                val trID:String = update.tripID
                var found = false
                for(trip in tripPatterns){
                    if (trip.pattern == null){
                        //pattern is null, which means we have to download
                        // the pattern data from MaTO
                        routesToDownload.add(trip.trip.routeID)
                    }
                    if (trip.trip.tripID == "gtt:$trID"){
                        found = true
                        //insert directly
                        mdict[trID] = Pair(update,trip)
                        break
                    }
                }
                if (!found){
                    //Log.d(DEBUG_TI, "Cannot find pattern ${tr}")
                    //give the update anyway
                    mdict[trID] = Pair(update,null)
                }
            }
        //have to request download of missing Patterns
        if (routesToDownload.size > 0){
            Log.d(DEBUG_TI, "Have ${routesToDownload.size} missing patterns from the DB: $routesToDownload")
            //downloadMissingPatterns (ArrayList(routesToDownload))
            MatoPatternsDownloadWorker.downloadPatternsForRoutes(routesToDownload.toList(), getApplication())
        }

        return@map mdict
    }

    //OBSERVE THIS TO GET THE LOCATION UPDATES FILTERED
    val filteredLocationUpdates = MediatorLiveData<Pair<FullPositionUpdatesMap, List<String>>>()
    init {
        filteredLocationUpdates.addSource(updatesWithTripAndPatterns){
            filteredLocationUpdates.value = filterUpdatesForGtfsLine(it, gtfsLineToFilterPos.value!!)
        }

        filteredLocationUpdates.addSource(gtfsLineToFilterPos){
            updatesWithTripAndPatterns.value?.let{ ups-> filteredLocationUpdates.value = filterUpdatesForGtfsLine(ups, it)}
        }

    }

    private fun filterUpdatesForGtfsLine(updates: FullPositionUpdatesMap,
                                         linePatt: Pair<String, MatoPattern?>):
            Pair<HashMap<String,FullPositionUpdate>, List<String>>{
        val gtfsLineId = linePatt.first
        val pattern = linePatt.second


        val filtdLineID = GtfsUtils.stripGtfsPrefix(gtfsLineId)
        //filter buses with direction, show those only with the same direction
        val updsForTripId = HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>>()
        val directionId = pattern?.directionId ?: -100
        val numUpds = updates.entries.size
        Log.d(DEBUG_TI, "Got $numUpds updates, current pattern is: ${pattern?.name}, directionID: ${pattern?.directionId}")
        // cannot understand where this is used
        //val patternsDirections = HashMap<String,Int>()
        val vehicleOnWrongDirection = mutableListOf<String>()
        for((tripId, pair) in updates.entries){
            //remove trips with wrong line
            val posUp = pair.first
            val vehicle = pair.first.vehicle
            if(pair.first.routeID!=filtdLineID)
                continue

            if(directionId!=-100 && pair.second!=null && pair.second?.pattern !=null){
                val dir = pair.second!!.pattern!!.directionId

                if(dir == directionId){
                    //add the trip
                    updsForTripId[tripId] = pair
                } else{
                    vehicleOnWrongDirection.add(vehicle)
                }
                //patternsDirections[tripId] = dir ?: -10
            } else{
                updsForTripId[tripId] = pair
                //Log.d(DEBUG_TAG, "No pattern for tripID: $tripId")
                //patternsDirections[tripId] = -10
            }
        }
        Log.d(DEBUG_TI, " Filtered updates are ${updsForTripId.keys.size}") // Original updates directs: $patternsDirections\n

        return  Pair(updsForTripId, vehicleOnWrongDirection)
    }


    fun requestMatoPosUpdates(line: String){
        lineListening = line
        viewModelScope.launch {
            mqttClient.startAndSubscribe(line,matoPositionListener, getApplication())
        }


        //updatePositions(1000)
    }

    fun stopMatoUpdates(){
        viewModelScope.launch {
            val tt = System.currentTimeMillis()
            mqttClient.stopMatoRequests(matoPositionListener)
            val time = System.currentTimeMillis() -tt
            Log.d(DEBUG_TI, "Took $time ms to unsubscribe")
        }

    }

    fun retriggerPositionUpdate(){
        if(positionsToBeMatchedLiveData.value!=null){
            positionsToBeMatchedLiveData.postValue(positionsToBeMatchedLiveData.value)
        }
    }
    //Gtfs Real time
    private val gtfsPositionsReqListener = object: GtfsRtPositionsRequest.Companion.RequestListener{
        override fun onResponse(response: ArrayList<LivePositionUpdate>?) {
            Log.i(DEBUG_TI,"Got response from the GTFS RT server")
            response?.let {it:ArrayList<LivePositionUpdate> ->
                if (it.size == 0) {
                    Log.w(DEBUG_TI,"No position updates from the GTFS RT server")
                    return
                }
                else {
                    //Log.i(DEBUG_TI, "Posting value to positionsLiveData")
                    viewModelScope.launch { positionsToBeMatchedLiveData.postValue(it) }

                }
            }
            gtfsRtRequestRunning.postValue(false)

        }

    }
    private val positionRequestErrorListener = Response.ErrorListener {
        Log.e(DEBUG_TI, "Could not download the update", it)
        gtfsRtRequestRunning.postValue(false)
    }

    fun requestGTFSUpdates(){
        if(gtfsRtRequestRunning.value == null || !gtfsRtRequestRunning.value!!) {
            val request = GtfsRtPositionsRequest(positionRequestErrorListener, gtfsPositionsReqListener)
            request.setRetryPolicy(
                DefaultRetryPolicy(1000,10,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            )
            netVolleyManager.requestQueue.add(request)
            Log.i(DEBUG_TI, "Requested GTFS realtime position updates")
            gtfsRtRequestRunning.value = true
        }

    }

    fun requestDelayedGTFSUpdates(timems: Long){
        viewModelScope.launch {
            delay(timems)
            requestGTFSUpdates()
        }
    }

    override fun onCleared() {
        //stop the MQTT Service
        Log.d(DEBUG_TI, "Clearing the live positions view model, stopping the mqttClient")
        mqttClient.disconnect()
        super.onCleared()
    }
    //Request trips download
    fun downloadTripsFromMato(trips: List<String>): Boolean{
        if(trips.isEmpty())
            return false
        var shouldContinue = false
        val currentDateTime = Date().time

        for (tr in trips){
            if (!lastFailedTripsRequest.containsKey(tr)){
                shouldContinue = true
                break
            } else{
                //Log.i(DEBUG_TI, "Last time the trip has failed is ${lastFailedTripsRequest[tr]}")
                if ((lastFailedTripsRequest[tr]!!.time - currentDateTime) > MAX_TIME_RETRY){
                    shouldContinue =true
                    break
                }
            }
        }
        if (shouldContinue) {
            //if one trip
            val workRequ =MatoTripsDownloadWorker.requestMatoTripsDownload(trips, getApplication(), "BusTO-MatoTripsDown")
            workRequ?.let { req ->
                Log.d(DEBUG_TI, "Enqueueing new work, saving work info")
                lastRequestedDownloadTrips.postValue(trips)
                //isLastWorkResultGood =
            }
        } else{
            Log.w(DEBUG_TI, "Requested to fetch data for ${trips.size} trips but they all have failed before in the last $MAX_MINUTES_RETRY mins")
        }
        return shouldContinue
    }

    companion object{
        private const val DEBUG_TI = "BusTO-LivePosViewModel"
        private const val MAX_MINUTES_RETRY = 3
        private const val MAX_TIME_RETRY = MAX_MINUTES_RETRY*60*1000 //3 minutes (in milliseconds)
    }
}