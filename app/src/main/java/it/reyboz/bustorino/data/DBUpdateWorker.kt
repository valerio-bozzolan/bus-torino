/*
	BusTO - Data components
    Copyright (C) 2021-2026 Fabio Mazza

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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.work.*
import androidx.work.WorkManager.Companion.getInstance
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Fetcher
import it.reyboz.bustorino.backend.Notifications
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Worker class that runs the DB update, without checking if it is needed or not
 */
class DBUpdateWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @SuppressLint("RestrictedApi")
    override suspend fun doWork(): Result {
        val con = applicationContext
        val sharedPrefs = con.getSharedPreferences(con.getString(R.string.mainSharedPreferences), Context.MODE_PRIVATE)
        val newDBVersion = DatabaseUpdate.getNewVersion()

        /*val currentDBVersion = sharedPrefs.getInt(PreferencesHolder.DB_GTT_VERSION_KEY, -10)

        val isUpdateCompulsory = inputData.getBoolean(FORCED_UPDATE, false)

        val lastDBUpdateTime = sharedPrefs.getLong(PreferencesHolder.DB_LAST_UPDATE_KEY, 0)
        var currentTime = System.currentTimeMillis() / 1000

        // ---- RECREATE NOTIFICATION HERE IF YOU WANT TO SHOW IT TO THE USER ----
        // ---- create notification channel first
        Log.d(DEBUG_TAG, "Have previous version: $currentDBVersion and new version $newDBVersion")
        Log.d(DEBUG_TAG, "Update compulsory: $isUpdateCompulsory")


        //we got a good version
        if (!(currentDBVersion < newDBVersion || currentTime > lastDBUpdateTime + UPDATE_MIN_DELAY)
            && !isUpdateCompulsory
        ) {
            //don't need to update
            //cancelNotification(NOTIFICATION_ID)
            return Result.success(
                Data.Builder().putInt
                    (SUCCESS_REASON_KEY, SUCCESS_NO_ACTION_NEEDED).build()
            )
        }

         */
        //start the real update
        val resultAtomicReference = AtomicReference<Fetcher.Result?>()

        DatabaseUpdate.setDBUpdatingFlag(con, sharedPrefs, true)
        val resultUpdate = DatabaseUpdate.performDBUpdate(con, resultAtomicReference)
        DatabaseUpdate.setDBUpdatingFlag(con, sharedPrefs, false)

        if (resultUpdate != DatabaseUpdate.Result.DONE) {
            //Fetcher.Result result = resultAtomicReference.get();
            val dataBuilder = Data.Builder()
            when (resultUpdate) {
                DatabaseUpdate.Result.ERROR_STOPS_DOWNLOAD -> dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_STOPS)
                DatabaseUpdate.Result.ERROR_LINES_DOWNLOAD -> dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_LINES)
                DatabaseUpdate.Result.DB_CLOSED -> dataBuilder.put(ERROR_REASON_KEY, ERROR_CODE_DB_CLOSED)
                DatabaseUpdate.Result.DONE -> {}
            }
            //cancelNotification(NOTIFICATION_ID)
            return Result.failure(dataBuilder.build())
        }
        Log.d(DEBUG_TAG, "Update finished successfully!")
        //update the version in the shared preference
        val editor = sharedPrefs.edit()
        editor.putInt(PreferencesHolder.DB_GTT_VERSION_KEY, newDBVersion)
        val currentTime = System.currentTimeMillis() / 1000
        editor.putLong(PreferencesHolder.DB_LAST_UPDATE_KEY, currentTime)
        editor.apply()
        //cancelNotification(NOTIFICATION_ID)

        return Result.success(Data.Builder().putInt(SUCCESS_REASON_KEY, SUCCESS_UPDATE_DONE).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        //val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val context = applicationContext
        Notifications.createDBNotificationChannelIfNeeded(context)

        val builder = NotificationCompat.Builder(
            context,
            Notifications.DB_UPDATE_CHANNELS_ID
        )
            .setContentTitle(context.getString(R.string.database_update_msg_notif))
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        builder.setSmallIcon(R.drawable.ic_bus_stilized)

        /*val typeInt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0

         */

        return ForegroundInfo(NOTIFICATION_ID, builder.build())
    }

    /*
    private int showNotification(@NonNull final NotificationManagerCompat notificManager, final int notification_ID,
                                 final String channel_ID){
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channel_ID)
                .setContentTitle("Libre BusTO - Updating Database")
                .setProgress(0,0,true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setSmallIcon(R.drawable.ic_bus_orange);


        notificManager.notify(notification_ID,builder.build());

        return notification_ID;
    }
     */
    private fun cancelNotification(notificationID: Int) {
        val notificationManager = NotificationManagerCompat.from(getApplicationContext())

        notificationManager.cancel(notificationID)
    }

    companion object {
        const val ERROR_CODE_KEY: String = "Error_Code"
        const val ERROR_REASON_KEY: String = "ERROR_REASON"
        const val ERROR_FETCHING_VERSION: Int = 4
        const val ERROR_DOWNLOADING_STOPS: Int = 5
        const val ERROR_DOWNLOADING_LINES: Int = 6
        val ERROR_CODE_DB_CLOSED: Int = -2

        const val SUCCESS_REASON_KEY: String = "SUCCESS_REASON"
        const val SUCCESS_NO_ACTION_NEEDED: Int = 9
        const val SUCCESS_UPDATE_DONE: Int = 1

        const val FORCED_UPDATE: String = "FORCED-UPDATE"

        private const val DEBUG_TAG: String = "BusTO-UpdateWorker"
        const val STATUS_UPDATE: String = "STATUS_UPDATE"
        const val WORK_NAME = "BusTO-UpdateWorker"
        private const val NOTIFICATION_ID = 32198


        private const val UPDATE_MIN_DELAY = (9 * 24 * 3600 //9 days
                ).toLong()


        val workConstraints: Constraints
            get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false).build()

        /**
         * Run the database update immediately
         */
        @JvmStatic
        fun requestDBUpdateUniqueWork(con: Context, forced: Boolean) {

            val workManager = getInstance(con)
            val reqData = Data.Builder()
                .putBoolean(FORCED_UPDATE, forced).build()

            val wr = OneTimeWorkRequest.Builder(DBUpdateWorker::class.java)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(reqData)
                .build()

            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, wr)
        }

        @JvmStatic
        fun getWorkInfoLiveData(context: Context): LiveData<List<WorkInfo>> {
            val workManager = WorkManager.getInstance(context)
            return workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME)
        }
    }
}
