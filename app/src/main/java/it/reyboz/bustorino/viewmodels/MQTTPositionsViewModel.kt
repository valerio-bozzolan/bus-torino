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
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.MatoPatternsDownloadWorker
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.fragments.GTFSPositionsViewModel
import kotlinx.coroutines.launch


typealias UpdatesMap = HashMap<String, LivePositionUpdate>

class MQTTPositionsViewModel(application: Application): AndroidViewModel(application) {

    private val gtfsRepo = GtfsRepository(application)

    //private val updates = UpdatesMap()
    private val updatesLiveData = MutableLiveData<ArrayList<LivePositionUpdate>>()

    private var mqttClient = MQTTMatoClient.getInstance()

    private var lineListening = ""
    private var lastTimeReceived: Long = 0

    private val positionListener = MQTTMatoClient.Companion.MQTTMatoListener{

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
            updatesLiveData.value = (mupds)
            lastTimeReceived = time
        }

    }

    //find the trip IDs in the updates
    private val tripsIDsInUpdates = updatesLiveData.map { it ->
        //Log.d(DEBUG_TI, "Updates map has keys ${upMap.keys}")
        it.map { pos -> "gtt:"+pos.tripID  }

    }
    // get the trip IDs in the DB
    private val gtfsTripsPatternsInDB = tripsIDsInUpdates.switchMap {
        Log.i(DEBUG_TI, "tripsIds in updates changed: ${it.size}")
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
        val mdict = HashMap<String,Pair<LivePositionUpdate, TripAndPatternWithStops?>>()
        //missing patterns
        val routesToDownload = HashSet<String>()
        if(updatesLiveData.value!=null)
            for(update in updatesLiveData.value!!){

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


    fun requestPosUpdates(line: String){
        lineListening = line
        viewModelScope.launch {
            mqttClient.startAndSubscribe(line,positionListener, getApplication())
        }


        //updatePositions(1000)
    }

    fun stopPositionsListening(){
        viewModelScope.launch {
            val tt = System.currentTimeMillis()
            mqttClient.desubscribe(positionListener)
            val time = System.currentTimeMillis() -tt
            Log.d(DEBUG_TI, "Took $time ms to unsubscribe")
        }

    }

    fun retriggerPositionUpdate(){
        if(updatesLiveData.value!=null){
            updatesLiveData.postValue(updatesLiveData.value)
        }
    }

    companion object{
        private const val DEBUG_TI = "BusTO-MQTTLiveData"
    }
}