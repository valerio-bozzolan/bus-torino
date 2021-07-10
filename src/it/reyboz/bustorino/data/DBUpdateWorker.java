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

    private final int notifi_ID=62341;

    public static final String FORCED_UPDATE = "FORCED-UPDATE";

    public static final String DEBUG_TAG = "Busto-UpdateWorker";


    public DBUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("RestrictedApi")
    @NonNull
    @Override
    public Result doWork() {
        //register Notification channel
        final Context con = getApplicationContext();
        Notifications.createDefaultNotificationChannel(con);
        final SharedPreferences shPr = con.getSharedPreferences(con.getString(R.string.mainSharedPreferences),MODE_PRIVATE);
        final int current_DB_version = shPr.getInt(DatabaseUpdate.DB_VERSION_KEY,-10);

        final int new_DB_version = DatabaseUpdate.getNewVersion();

        final boolean isUpdateCompulsory = getInputData().getBoolean(FORCED_UPDATE,false);

        final int notificationID = showNotification();
        Log.d(DEBUG_TAG, "Have previous version: "+current_DB_version +" and new version "+new_DB_version);
        Log.d(DEBUG_TAG, "Update compulsory: "+isUpdateCompulsory);
        if (new_DB_version < 0){
            //there has been an error
            final Data out = new Data.Builder().putInt(ERROR_REASON_KEY, ERROR_FETCHING_VERSION)
                    .putInt(ERROR_CODE_KEY,new_DB_version).build();
            cancelNotification(notificationID);
            return ListenableWorker.Result.failure(out);
        }

        //we got a good version
        if (current_DB_version >= new_DB_version && !isUpdateCompulsory) {
            //don't need to update
            cancelNotification(notificationID);
            return ListenableWorker.Result.success(new Data.Builder().
                    putInt(SUCCESS_REASON_KEY, SUCCESS_NO_ACTION_NEEDED).build());
        }
        //start the real update
        AtomicReference<Fetcher.Result> resultAtomicReference = new AtomicReference<>();
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,true);
        final DatabaseUpdate.Result resultUpdate = DatabaseUpdate.performDBUpdate(con,resultAtomicReference);
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,false);

        if (resultUpdate != DatabaseUpdate.Result.DONE){
            Fetcher.Result result = resultAtomicReference.get();

            final Data.Builder dataBuilder = new Data.Builder();
            switch (resultUpdate){
                case ERROR_STOPS_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_STOPS);
                    break;
                case ERROR_LINES_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_LINES);
                    break;
            }
            cancelNotification(notificationID);
            return ListenableWorker.Result.failure(dataBuilder.build());
        }
        Log.d(DEBUG_TAG, "Update finished successfully!");
        //update the version in the shared preference
        final SharedPreferences.Editor editor = shPr.edit();
        editor.putInt(DatabaseUpdate.DB_VERSION_KEY, new_DB_version);
        editor.apply();
        cancelNotification(notificationID);

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


    private int showNotification(){
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), Notifications.DEFAULT_CHANNEL_ID)
                .setContentTitle("Libre BusTO - Updating Database")
                .setProgress(0,0,true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setSmallIcon(R.drawable.ic_bus_orange);
        final NotificationManagerCompat notifcManager = NotificationManagerCompat.from(getApplicationContext());
        final  int notification_ID = 32198;
        notifcManager.notify(notification_ID,builder.build());

        return notification_ID;
    }

    private void cancelNotification(int notificationID){
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        notificationManager.cancel(notificationID);
    }
}
