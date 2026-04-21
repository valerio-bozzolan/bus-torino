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

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Lightweight periodic worker that checks local state and enqueues [DBUpdateWorker]
 * only when an update is actually needed, without making any network calls itself.
 */
class DBUpdateCheckWorker(context: Context, workerParams: WorkerParameters)
    : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val con = applicationContext
        val sharedPrefs = PreferencesHolder.getMainSharedPreferences(con)

        val currentDBVersion = sharedPrefs.getInt(PreferencesHolder.DB_GTT_VERSION_KEY, -10)
        val lastDBUpdateTime = sharedPrefs.getLong(PreferencesHolder.DB_LAST_UPDATE_KEY, 0L)
        val currentTime = System.currentTimeMillis() / 1000

        val neverUpdated = currentDBVersion < 0 || lastDBUpdateTime <= 0
        val timeElapsed = currentTime > lastDBUpdateTime + UPDATE_MIN_DELAY

        if (neverUpdated || timeElapsed) {
            Log.d(DEBUG_TAG, "Scheduling DBUpdateWorker")
            DBUpdateWorker.requestDBUpdateUniqueWork(con, forced = true)
        } else {
            Log.d(DEBUG_TAG, "No update needed")
        }

        return Result.success()
    }

    companion object {
        const val DEBUG_TAG = "BusTO-DBUpdateScheduler"
        const val WORK_NAME = "DBUpdateChecker"

        private const val UPDATE_MIN_DELAY = (3 * 24 * 3600L) //

        fun schedulePeriodicCheck(context: Context, restart: Boolean = false) {
            val workRequest = PeriodicWorkRequest.Builder(
                DBUpdateCheckWorker::class.java, 1, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            val policy = if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                         else ExistingPeriodicWorkPolicy.KEEP

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, policy, workRequest)
        }

    }
}
