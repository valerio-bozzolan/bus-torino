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
package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequest
import com.android.volley.Response
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.gtfs.GtfsRtPositionsRequest
import it.reyboz.bustorino.data.*
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * View Model for the map. For containing the stops, the trips and whatever
 */
class GtfsPositionsViewModel(application: Application): AndroidViewModel(application) {
    private val gtfsRepo = GtfsRepository(application)

    private val netVolleyManager = NetworkVolleyManager.getInstance(application)


    val positionsLiveData = MutableLiveData<ArrayList<LivePositionUpdate>>()
    private val positionsRequestRunning = MutableLiveData<Boolean>()


    private val positionRequestListener = object: GtfsRtPositionsRequest.Companion.RequestListener{
        override fun onResponse(response: ArrayList<LivePositionUpdate>?) {
            Log.i(DEBUG_TI,"Got response from the GTFS RT server")
            response?.let {it:ArrayList<LivePositionUpdate> ->
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
        Log.e(DEBUG_TI, "Could not download the update, error:\n"+it.stackTrace)
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
    //this holds the trips that have been downloaded but for which we have no pattern
    /*private val gtfsTripsInDBMissingPattern = tripsIDsInUpdates.map { tripsIDs ->
        val tripsInDB = gtfsRepo.gtfsDao.getTripsFromIDs(tripsIDs)
        val tripsPatternCodes = tripsInDB.map { tr -> tr.patternId }
        val codesInDB = gtfsRepo.gtfsDao.getPatternsCodesInTheDB(tripsPatternCodes)

        tripsInDB.filter { !(codesInDB.contains(it.patternId)) }
    }*/
    //private val patternsCodesInDB = tripsDBPatterns.map { gtfsRepo.gtfsDao.getPatternsCodesInTheDB(it) }

    // trips that are in the DB, together with the pattern. If the pattern is not present in the DB, it's null
    val gtfsTripsPatternsInDB = tripsIDsInUpdates.switchMap {
        //Log.i(DEBUG_TI, "tripsIds in updates changed: ${it.size}")
        gtfsRepo.gtfsDao.getTripPatternStops(it)
    }
    //trip IDs to query, which are not present in the DB
     val tripsGtfsIDsToQuery: LiveData<List<String>> = gtfsTripsPatternsInDB.map { tripswithPatterns ->
        val tripNames=tripswithPatterns.map { twp-> twp.trip.tripID }
        Log.i(DEBUG_TI, "Have ${tripswithPatterns.size} trips in the DB")
        if (tripsIDsInUpdates.value!=null)
            return@map tripsIDsInUpdates.value!!.filter { !tripNames.contains(it) }
        else {
            Log.e(DEBUG_TI,"Got results for gtfsTripsInDB but not tripsIDsInUpdates??")
            return@map ArrayList<String>()
        }
    }

    val updatesWithTripAndPatterns = gtfsTripsPatternsInDB.map { tripPatterns->
        Log.i(DEBUG_TI, "Mapping trips and patterns")
        val mdict = HashMap<String,Pair<LivePositionUpdate, TripAndPatternWithStops?>>()
        //missing patterns
        val routesToDownload = HashSet<String>()
        if(positionsLiveData.value!=null)
            for(update in positionsLiveData.value!!){
                val trID = update.tripID
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
            downloadMissingPatterns(ArrayList(routesToDownload))
        }

        return@map mdict
    }
    /*
     There are two strategies for the queries, since we cannot query a bunch of tripIDs all together
     to Mato API -> we need to query each separately:
     1 -> wait until they are all queried to insert in the DB
     2 -> after each request finishes, insert it into the DB

     Keep in mind that trips DO CHANGE often, and so do the Patterns
     */
    fun downloadTripsFromMato(trips: List<String>): OneTimeWorkRequest?{
        return MatoTripsDownloadWorker.requestMatoTripsDownload(trips,getApplication(), "BusTO-MatoTripsDown")
    }
    private fun downloadMissingPatterns(routeIds: List<String>): Boolean{
        return MatoPatternsDownloadWorker.downloadPatternsForRoutes(routeIds, getApplication())
    }

    init {

        Log.d(DEBUG_TI, "MapViewModel created")
        Log.d(DEBUG_TI, "Observers of positionsLiveData ${positionsLiveData.hasActiveObservers()}")

        positionsRequestRunning.value = false;
    }
    fun testCascade(){
       val n  = ArrayList<LivePositionUpdate>()
        n.add(LivePositionUpdate("22920721U","lala","lalal","lol","ASD",
            1000.0,1000.0, 9000.0f, 21838191, null
            ))
        positionsLiveData.value = n
    }


    /**
     * Start downloading missing GtfsTrips and Insert them in the DB
     */


    companion object{
        private const val DEBUG_TI="BusTO-GTFSRTViewModel"
        const val DEFAULT_DELAY_REQUESTS: Long=4000

    }
}