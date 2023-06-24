package it.reyboz.bustorino.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.*
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Notifications

class GtfsMaintenanceWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams)  {
    override suspend fun doWork(): Result {
        val data = inputData.getString(OPERATION_TYPE)
        if(data ==null){
            return Result.failure()
        }
        val result = when (data){
            CLEAR_GTFS_TRIPS ->clearGtfsTrips()
            else -> {Result.failure()}
        }
        return result
    }
    private fun clearGtfsTrips(): Result{
        val gtfsRepository = GtfsRepository(applicationContext)
        gtfsRepository.gtfsDao.deleteAllTrips()

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
            .setContentText("Database maintenance")
            .build()
        return ForegroundInfo(3671672811121.toInt(), notification)
    }
    companion object{
        const val CLEAR_GTFS_TRIPS="trips_clear"
        const val OPERATION_TYPE="oper_type"
        fun makeOneTimeRequest(type: String): OneTimeWorkRequest {
            val data = Data.Builder().putString(OPERATION_TYPE, type).build()
            return OneTimeWorkRequest.Builder(GtfsMaintenanceWorker::class.java)
                .setInputData(data).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(type)
                .build()
        }
    }
}