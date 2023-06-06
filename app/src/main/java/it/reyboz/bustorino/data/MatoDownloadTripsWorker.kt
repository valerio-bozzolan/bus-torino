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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Response
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Notifications
import it.reyboz.bustorino.data.gtfs.GtfsTrip
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

class MatoDownloadTripsWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        //val imageUriInput =
        //    inputData.("IMAGE_URI") ?: return Result.failure()
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
                Log.e(DEBUG_TAG, "Cannot download Gtfs Trip $trip")
                val stacktrace  = error.stackTrace.take(5)
                Log.w(DEBUG_TAG, "Stacktrace:\n$stacktrace")
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
        val doInsert = (queriedMatoTrips subtract failedMatoTripsDownload).containsAll(tripsIDsCompleted)
        Log.i(DEBUG_TAG, "Inserting missing GtfsTrips in the database, should insert $doInsert")
        if(doInsert){

            gtfsRepository.gtfsDao.insertTrips(downloadedMatoTrips)

        }

        return Result.success()
    }
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Notifications.DB_UPDATE_CHANNELS_ID,
                context.getString(R.string.database_notification_channel),
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, Notifications.DB_UPDATE_CHANNELS_ID)
            //.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), Constants.PENDING_INTENT_FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.bus)
            .setOngoing(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(context.getString(R.string.app_name))
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentText("Downloading data")
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }


    companion object{
        const val TRIPS_KEYS = "tripsToDownload"
        const val WORK_TAG = "tripsDownloaderAndInserter"
        const val DEBUG_TAG="BusTO:MatoTripDownWRK"
        const val NOTIFICATION_ID=424242
    }
}
