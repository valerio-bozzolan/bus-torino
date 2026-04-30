package it.reyboz.bustorino.data

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.RequestFuture
import com.google.transit.realtime.GtfsRealtime
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.Notifications
import it.reyboz.bustorino.backend.gtfs.GtfsRtAlertsRequest
import it.reyboz.bustorino.data.GtfsMaintenanceWorker.Companion.OPERATION_TYPE
import it.reyboz.bustorino.data.gtfs.GtfsAlertsActivePeriods
import it.reyboz.bustorino.data.gtfs.GtfsAlertsTranslation
import it.reyboz.bustorino.data.gtfs.GtfsAlertEntity
import it.reyboz.bustorino.data.gtfs.GtfsAlertInformedEntity
import it.reyboz.bustorino.data.gtfs.GtfsAlertsDBConverter
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class GtfsAlertDBDownloadWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams)  {
    override suspend fun doWork(): Result {
        val volleyManager = NetworkVolleyManager.getInstance(applicationContext)
        val gtfsDatabase = GtfsDatabase.getGtfsDatabase(applicationContext)
        //use future to wait for request
        val dao =gtfsDatabase.alertsDao()
        //clear old ones
        dao.deleteOlderThanHours(24)

        var attempts = 0
        var notOK = true
        var resuList = ArrayList<GtfsRealtime.FeedEntity>()
        while (notOK && attempts < 5) {
            Log.d(DEBUG_TAG, "Fetching alerts, trial $attempts")
            val future = RequestFuture.newFuture<ArrayList<GtfsRealtime.FeedEntity>>()

            val req = GtfsRtAlertsRequest(object : Response.ErrorListener {
                override fun onErrorResponse(err: VolleyError) {
                    Log.e(DEBUG_TAG, "Error getting alerts: ${err.message}", err)
                }
            }, future)

            volleyManager.requestQueue.add(req)
            try {
                resuList = future.get(10, TimeUnit.SECONDS)
                if (resuList.isNotEmpty()){
                    Log.d(DEBUG_TAG, "Have no alerts, attempt $attempts")
                    notOK = false
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Log.e(DEBUG_TAG, e.message, e)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                Log.e(DEBUG_TAG, e.message, e)
            } catch (e: TimeoutException) {
                e.printStackTrace()
                Log.e(DEBUG_TAG, e.message, e)
            }

            attempts++
        }
        if (notOK) {
            return Result.failure()
        }

        val timeReceived = System.currentTimeMillis()
        val alertsToAdd = ArrayList<GtfsAlertEntity>()
        val translToAdd = ArrayList<GtfsAlertsTranslation>()
        val activePeriods = ArrayList<GtfsAlertsActivePeriods>()
        val informedEntities = ArrayList<GtfsAlertInformedEntity>()
        for(e in resuList){
            val parsedRes = GtfsAlertsDBConverter.fromFeedEntity(e, timeReceived)

            alertsToAdd.add(parsedRes.alert)
            translToAdd.addAll(parsedRes.translations)
            activePeriods.addAll(parsedRes.activePeriods)
            informedEntities.addAll(parsedRes.informedEntities)
        }
        Log.d(DEBUG_TAG, "alerts received: ${alertsToAdd.size}")
        dao.insertMissingAlerts(alertsToAdd, translToAdd, activePeriods, informedEntities)

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {

        val context = applicationContext
        Notifications.createDBNotificationChannelIfNeeded(context)

        return ForegroundInfo(NOTIFICATION_ID,
            Notifications.makeDBUpdateLowPriorityNotification(context, context.getString(R.string.downloading_alerts_message)))
    }


    companion object{
        private const val NOTIFICATION_ID = 271899102
        private const val DEBUG_TAG = "BusTO-GTFSRTAlertsDown"

        fun makeOneTimeRequest(tag: String): OneTimeWorkRequest {
            //val data = Data.Builder().putString(OPERATION_TYPE, type).build()
            return OneTimeWorkRequest.Builder(GtfsAlertDBDownloadWorker::class.java)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(tag)
                .build()
        }
    }
}