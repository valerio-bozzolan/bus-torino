/*
	BusTO  - Data components
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
package it.reyboz.bustorino.data

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.*
import it.reyboz.bustorino.backend.Notifications
import it.reyboz.bustorino.data.gtfs.GtfsTrip
import java.util.concurrent.CountDownLatch

class MatoTripsDownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {
        return downloadGtfsTrips()
    }

    /**
     * Download GTFS Trips from Mato
     */
    private fun downloadGtfsTrips():Result{
        val tripsList = inputData.getStringArray(TRIPS_KEYS)

        if (tripsList== null){
            Log.e(DEBUG_TAG,"trips list given is null")
            return Result.failure()
        }
        val gtfsRepository = GtfsRepository(applicationContext)
        val matoRepository = MatoRepository(applicationContext)
        //clear the matoTrips
        val queriedMatoTrips = HashSet<String>()

        val downloadedMatoTrips = ArrayList<GtfsTrip>()
        val failedMatoTripsDownload = HashSet<String>()


        Log.i(DEBUG_TAG, "Requesting download for the trips")
        val requestCountDown = CountDownLatch(tripsList.size);
        for(trip in tripsList){
            queriedMatoTrips.add(trip)
            matoRepository.requestTripUpdate(trip,{error->
                Log.e(DEBUG_TAG, "Cannot download Gtfs Trip $trip, error: $error")
                //val stacktrace  = error.stackTrace.take(5)
                //Log.w(DEBUG_TAG, "Stacktrace:\n$stacktrace")
                failedMatoTripsDownload.add(trip)
                requestCountDown.countDown()
            }){

                if(it.isSuccess){
                    if (it.result == null){
                        Log.e(DEBUG_TAG, "Got null result");
                    }
                    downloadedMatoTrips.add(it.result!!)
                } else{
                    failedMatoTripsDownload.add(trip)
                }
                Log.i(
                    DEBUG_TAG,"Result download, so far, trips: ${queriedMatoTrips.size}, failed: ${failedMatoTripsDownload.size}," +
                            " succeded: ${downloadedMatoTrips.size}")
                //check if we can insert the trips
                requestCountDown.countDown()
            }

        }
        requestCountDown.await()
        val tripsIDsCompleted = downloadedMatoTrips.map { trip-> trip.tripID }
        if (tripsIDsCompleted.isEmpty()){
            Log.d(DEBUG_TAG, "No trips have been downloaded, set work to fail")
            return Result.failure()
        } else {
            val doInsert = (queriedMatoTrips subtract failedMatoTripsDownload).containsAll(tripsIDsCompleted)
            Log.i(DEBUG_TAG, "Inserting missing GtfsTrips in the database, should insert $doInsert")
            if (doInsert) {

                gtfsRepository.gtfsDao.insertTrips(downloadedMatoTrips)

            }

            return Result.success()
        }
    }
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val context = applicationContext
        Notifications.createDBNotificationChannel(context)

        return ForegroundInfo(NOTIFICATION_ID, Notifications.makeMatoDownloadNotification(context))
    }


    companion object{
        const val TRIPS_KEYS = "tripsToDownload"
        const val DEBUG_TAG="BusTO:MatoTripDownWRK"
        const val NOTIFICATION_ID=42424221

        const val TAG_TRIPS ="gtfsTripsDownload"

        fun requestMatoTripsDownload(trips: List<String>, context: Context, debugTag: String): OneTimeWorkRequest? {
            if (trips.isEmpty()) return null
            val workManager = WorkManager.getInstance(context)
            val info = workManager.getWorkInfosForUniqueWork(TAG_TRIPS).get()

            val runNewWork = if(info.isEmpty()) true
                            else info[0].state!= WorkInfo.State.RUNNING && info[0].state!= WorkInfo.State.ENQUEUED
            val addDat = if(info.isEmpty())
                null else  info[0].state
            Log.d(debugTag, "Request to download and insert ${trips.size} trips, proceed: $runNewWork, workstate: $addDat")
            if(runNewWork) {
                val tripsArr: Array<String?> = trips.toTypedArray()
                val dataBuilder = Data.Builder().putStringArray(TRIPS_KEYS, tripsArr)
                //build()
                val requ = OneTimeWorkRequest.Builder(MatoTripsDownloadWorker::class.java)
                    .setInputData(dataBuilder.build()).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG_TRIPS)
                    .build()
                workManager.enqueueUniqueWork(TAG_TRIPS, ExistingWorkPolicy.KEEP, requ)
                return requ
            } else return null;
        }
    }
}
