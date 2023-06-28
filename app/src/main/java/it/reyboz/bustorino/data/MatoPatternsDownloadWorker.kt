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
import it.reyboz.bustorino.backend.Fetcher
import it.reyboz.bustorino.backend.Notifications
import it.reyboz.bustorino.backend.mato.MatoAPIFetcher
import java.util.concurrent.atomic.AtomicReference

class MatoPatternsDownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {
        val routesList = inputData.getStringArray(ROUTES_KEYS)

        if (routesList== null){
            Log.e(DEBUG_TAG,"routes list given is null")
            return Result.failure()
        }

        val res = AtomicReference<Fetcher.Result>(Fetcher.Result.OK)

        val gtfsRepository = GtfsRepository(applicationContext)
        val patterns = MatoAPIFetcher.getPatternsWithStops(applicationContext, routesList.asList().toMutableList(), res)
        if (res.get() != Fetcher.Result.OK) {
            Log.e(DatabaseUpdate.DEBUG_TAG, "Something went wrong downloading patterns")
            return Result.failure()
        }

        gtfsRepository.gtfsDao.insertPatterns(patterns)
        //Insert the PatternStops
        gtfsRepository.gtfsDao.insertPatternStops(DatabaseUpdate.makeStopsForPatterns(patterns))
        return Result.success()
    }


    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val context = applicationContext
        Notifications.createDBNotificationChannel(context)

        return ForegroundInfo(NOTIFICATION_ID, Notifications.makeMatoDownloadNotification(context))
    }


    companion object{
        const val ROUTES_KEYS = "routesToDownload"
        const val DEBUG_TAG="BusTO:MatoPattrnDownWRK"
        const val NOTIFICATION_ID=21983102

        const val TAG_PATTERNS ="matoPatternsDownload"



        fun downloadPatternsForRoutes(routesIds: List<String>, context: Context): Boolean{
            if(routesIds.isEmpty()) return false;

            val workManager = WorkManager.getInstance(context);
            val info = workManager.getWorkInfosForUniqueWork(TAG_PATTERNS).get()

            val runNewWork = if(info.isEmpty()) true
                        else info[0].state!= WorkInfo.State.RUNNING && info[0].state!= WorkInfo.State.ENQUEUED
            val addDat = if(info.isEmpty())
                null else  info[0].state

            Log.d(DEBUG_TAG, "Request to download and insert patterns for ${routesIds.size} routes, proceed: $runNewWork, workstate: $addDat")
            if(runNewWork){
                val routeIdsArray = routesIds.toTypedArray()
                val dataBuilder = Data.Builder().putStringArray(ROUTES_KEYS,routeIdsArray)

                val requ = OneTimeWorkRequest.Builder(MatoPatternsDownloadWorker::class.java)
                    .setInputData(dataBuilder.build()).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG_PATTERNS)
                    .build()
                workManager.enqueueUniqueWork(TAG_PATTERNS, ExistingWorkPolicy.KEEP, requ)
            }
            return true
        }
    }
}
