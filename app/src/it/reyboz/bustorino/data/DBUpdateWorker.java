/*
	BusTO - Data components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.*;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Notifications;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.MODE_PRIVATE;

public class DBUpdateWorker extends Worker{


    public static final String ERROR_CODE_KEY ="Error_Code";
    public static final String ERROR_REASON_KEY = "ERROR_REASON";
    public static final int ERROR_FETCHING_VERSION = 4;
    public static final int ERROR_DOWNLOADING_STOPS = 5;
    public static final int ERROR_DOWNLOADING_LINES = 6;

    public static final String SUCCESS_REASON_KEY = "SUCCESS_REASON";
    public static final int SUCCESS_NO_ACTION_NEEDED = 9;
    public static final int SUCCESS_UPDATE_DONE = 1;

    private final static int NOTIFIC_ID =32198;

    public static final String FORCED_UPDATE = "FORCED-UPDATE";

    public static final String DEBUG_TAG = "Busto-UpdateWorker";

    private static final long UPDATE_MIN_DELAY= 9*24*3600; //9 days


    public DBUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("RestrictedApi")
    @NonNull
    @Override
    public Result doWork() {
        //register Notification channel
        final Context con = getApplicationContext();
        //Notifications.createDefaultNotificationChannel(con);
        //Use the new notification channels
        Notifications.createNotificationChannel(con,con.getString(R.string.database_notification_channel),
                con.getString(R.string.database_notification_channel_desc), NotificationManagerCompat.IMPORTANCE_LOW,
                Notifications.DB_UPDATE_CHANNELS_ID
            );
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        final int notification_ID = 32198;
        final SharedPreferences shPr = con.getSharedPreferences(con.getString(R.string.mainSharedPreferences),MODE_PRIVATE);
        final int current_DB_version = shPr.getInt(DatabaseUpdate.DB_VERSION_KEY,-10);

        final int new_DB_version = DatabaseUpdate.getNewVersion();

        final boolean isUpdateCompulsory = getInputData().getBoolean(FORCED_UPDATE,false);

        final long lastDBUpdateTime = shPr.getLong(DatabaseUpdate.DB_LAST_UPDATE_KEY, 0);
        long currentTime = System.currentTimeMillis()/1000;

        //showNotification(notificationManager, notification_ID);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(con,
                Notifications.DB_UPDATE_CHANNELS_ID)
                .setContentTitle(con.getString(R.string.database_update_msg_notif))
                .setProgress(0,0,true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setSmallIcon(R.drawable.ic_bus_orange);


        notificationManager.notify(notification_ID,builder.build());

        Log.d(DEBUG_TAG, "Have previous version: "+current_DB_version +" and new version "+new_DB_version);
        Log.d(DEBUG_TAG, "Update compulsory: "+isUpdateCompulsory);
        /*
        SKIP CHECK (Reason: The Old API might fail at any moment)
        if (new_DB_version < 0){
            //there has been an error
            final Data out = new Data.Builder().putInt(ERROR_REASON_KEY, ERROR_FETCHING_VERSION)
                    .putInt(ERROR_CODE_KEY,new_DB_version).build();
            cancelNotification(notificationID);
            return ListenableWorker.Result.failure(out);
        }
         */

        //we got a good version
        if (!(current_DB_version < new_DB_version || currentTime > lastDBUpdateTime + UPDATE_MIN_DELAY )
                && !isUpdateCompulsory) {
            //don't need to update
            cancelNotification(notification_ID);
            return ListenableWorker.Result.success(new Data.Builder().
                    putInt(SUCCESS_REASON_KEY, SUCCESS_NO_ACTION_NEEDED).build());
        }
        //start the real update
        AtomicReference<Fetcher.Result> resultAtomicReference = new AtomicReference<>();
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,true);
        final DatabaseUpdate.Result resultUpdate = DatabaseUpdate.performDBUpdate(con,resultAtomicReference);
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,false);

        if (resultUpdate != DatabaseUpdate.Result.DONE){
            //Fetcher.Result result = resultAtomicReference.get();
            final Data.Builder dataBuilder = new Data.Builder();
            switch (resultUpdate){
                case ERROR_STOPS_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_STOPS);
                    break;
                case ERROR_LINES_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_LINES);
                    break;
            }
            cancelNotification(notification_ID);
            return ListenableWorker.Result.failure(dataBuilder.build());
        }
        Log.d(DEBUG_TAG, "Update finished successfully!");
        //update the version in the shared preference
        final SharedPreferences.Editor editor = shPr.edit();
        editor.putInt(DatabaseUpdate.DB_VERSION_KEY, new_DB_version);
        currentTime = System.currentTimeMillis()/1000;
        editor.putLong(DatabaseUpdate.DB_LAST_UPDATE_KEY, currentTime);
        editor.apply();
        cancelNotification(notification_ID);

        return ListenableWorker.Result.success(new Data.Builder().putInt(SUCCESS_REASON_KEY, SUCCESS_UPDATE_DONE).build());
    }

    public static Constraints getWorkConstraints(){
        return  new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(false).build();
    }

    public static WorkRequest newFirstTimeWorkRequest(){
        return new OneTimeWorkRequest.Builder(DBUpdateWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                //.setInputData(new Data.Builder().putBoolean())
                .build();
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

    private void cancelNotification(int notificationID){
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        notificationManager.cancel(notificationID);
    }
}
