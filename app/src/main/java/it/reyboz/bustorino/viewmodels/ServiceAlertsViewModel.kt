/*
	BusTO - View Model components
    Copyright (C) 2026 Fabio Mazza

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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.room.concurrent.AtomicBoolean
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.transit.realtime.GtfsRealtime.Alert
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.data.GtfsAlertDBDownloadWorker
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class ServiceAlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val gtfsRepo = GtfsRepository(app)
    private val volleyManager = NetworkVolleyManager.getInstance(app)

    private val alertsDao = GtfsDatabase.getGtfsDatabase(app).alertsDao()

    private val workManager = WorkManager.getInstance(app)

    //val alertsLiveData = MutableLiveData<ArrayList<Alert>>(ArrayList())

    private val stopToFilter = MutableLiveData("")
    private val routeToFilter = MutableLiveData("")

    val lastTimeRunningDownload = MutableLiveData(0L)
    private val keepRunning = AtomicBoolean(false)
    private val waitingToRerun = AtomicBoolean(false)
    fun setRunningDownloadRequests(value: Boolean) {
        Log.d(DEBUG_TAG, "setRunningDownloadRequests: $value")
        keepRunning.set(value)
    }

    val alertsByRouteLiveData = routeToFilter.switchMap {
        val unixTimestamp = (System.currentTimeMillis()/1000)
        gtfsRepo.getAlertsByRouteID(it).map{ l -> l.filter { al->al.isActive(unixTimestamp) }}
    }

    val alertsByStopLiveData = stopToFilter.switchMap {
        gtfsRepo.alertsDao.getAlertsForStop(it)
    }

    val allAlertsLiveData = gtfsRepo.alertsDao.getAllAlertsLiveData()
    /*
    private val volleyErrorListener = Response.ErrorListener { err ->
        Log.e(DEBUG_TAG, "Error getting alerts: ${err.message}", err)
    }
    private var numTries = 0
    private val responseListener = Response.Listener<ArrayList<GtfsRealtime.FeedEntity>> {
        Log.d(DEBUG_TAG, "Received ${it.size} alerts")
        if (it.isEmpty()) {
            if(numTries<4){
                numTries++;
                requestAlerts()
                Log.d(DEBUG_TAG, "Alerts requested again: $numTries")
            }
        }

        alertsLiveData.postValue(it.map { it.alert })
    }

    private fun requestAlerts(){
        val req = GtfsRtAlertsRequest(volleyErrorListener, responseListener)

        volleyManager.requestQueue.add(req)
    }

     */
    fun setStopFilter(stopId: String) {
        stopToFilter.value = stopId
    }
    fun setGtfsLineFilter(routeId: String) {
        routeToFilter.value = routeId
    }

    private fun downloadWorkIfTimePassed(){
        val currentTime = System.currentTimeMillis()
        waitingToRerun.set(false)
        val diff = currentTime - lastTimeRunningDownload.value!!
        Log.d(DEBUG_TAG, "diff : ${diff/1000} s")
        val MINUTES_CHECK = 3
        if (lastTimeRunningDownload.value == 0L ||
            currentTime > lastTimeRunningDownload.value!! + MINUTES_CHECK*60*1000){
            //actually enqueue request
            Log.d(DEBUG_TAG, "Launching request to download alerts")
            val req = GtfsAlertDBDownloadWorker.makeOneTimeRequest("alertsrn")
            workManager.enqueueUniqueWork("AlertsDownloadsRun", ExistingWorkPolicy.KEEP, req)
            lastTimeRunningDownload.postValue(System.currentTimeMillis())
        }
        viewModelScope.launch(Dispatchers.IO) {
            waitingToRerun.set(true)
            delay((61).seconds)
            if(keepRunning.get()) downloadWorkIfTimePassed()
        }

    }

    fun launchAlertsPeriodCheck(){
        setRunningDownloadRequests(true)
        if(!waitingToRerun.get())
            downloadWorkIfTimePassed()
    }



    private fun filterAlertsForStop(stopId: String, alerts: ArrayList<Alert>) : ArrayList<Alert>{

        val filteredAlerts = ArrayList<Alert>()
        for (al in alerts) {
            for (ie in al.informedEntityList) {
                if (ie.stopId == stopId) {
                    filteredAlerts.add(al)
                }
            }
        }
        return filteredAlerts
    }

    init{

        /*
        requestAlerts()

        alertsByRouteLiveData.addSource(alertsLiveData){ alerts ->
            if(alerts.isEmpty()){
                return@addSource
            }
            val routeMap = HashMap<String, ArrayList<Alert>>()
            for (al in alerts){
                for( ie in al.informedEntityList){
                    var routeID  = ""
                    if(ie.routeId.isNotEmpty()){
                        routeID = "gtt:${ie.routeId}"
                    } else if(ie.trip?.routeId?.isNotEmpty() == true){
                        routeID = "gtt:${ie.trip?.routeId}"
                    }
                    if (routeID.isNotEmpty()) {
                        if (!routeMap.containsKey(routeID)) {
                            routeMap[routeID] = ArrayList()
                        }

                        routeMap[routeID]!!.add(al)
                    }
                }
            }

            alertsByRouteLiveData.postValue(routeMap)
        }
        // Set transformations for stop
        alertsForStop.addSource(stopToFilter){ stopId ->
            alertsLiveData.value?.let{
                alertsForStop.postValue(filterAlertsForStop(stopId,it))
            }
        }

        alertsForStop.addSource(alertsLiveData){ alerts ->
            alertsForStop.postValue(filterAlertsForStop(stopToFilter.value!!,alerts))
        }


         */
    }

    companion object{
        private const val DEBUG_TAG = "BusTO-GTFSRTAlerts"

    }
}