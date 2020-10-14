package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.*;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;

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


    public DBUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context con = getApplicationContext();
        final SharedPreferences shPr = con.getSharedPreferences(con.getString(R.string.mainSharedPreferences),MODE_PRIVATE);
        final int current_DB_version = shPr.getInt(DatabaseUpdate.DB_VERSION_KEY,-1);

        final int new_DB_version = DatabaseUpdate.getNewVersion();
        if (new_DB_version < 0){
            //there has been an error
            final Data out = new Data.Builder().putInt(ERROR_REASON_KEY, ERROR_FETCHING_VERSION)
                    .putInt(ERROR_CODE_KEY,new_DB_version).build();
            return Result.failure(out);
        }
        //we got a good version
        if (current_DB_version >= new_DB_version) {
            //don't need to update
            return Result.success(new Data.Builder().
                    putInt(SUCCESS_REASON_KEY, SUCCESS_NO_ACTION_NEEDED).build());
        }
        //start the real update
        AtomicReference<Fetcher.result> resultAtomicReference = new AtomicReference<>();
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,true);
        final DatabaseUpdate.Result resultUpdate = DatabaseUpdate.performDBUpdate(con,resultAtomicReference);
        DatabaseUpdate.setDBUpdatingFlag(con, shPr,false);

        if (resultUpdate != DatabaseUpdate.Result.DONE){
            Fetcher.result result = resultAtomicReference.get();

            final Data.Builder dataBuilder = new Data.Builder();
            switch (resultUpdate){
                case ERROR_STOPS_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_STOPS);
                    break;
                case ERROR_LINES_DOWNLOAD:
                    dataBuilder.put(ERROR_REASON_KEY, ERROR_DOWNLOADING_LINES);
                    break;
            }
            return Result.failure(dataBuilder.build());
        }
        //update the version in the shared preference
        final SharedPreferences.Editor editor = shPr.edit();
        editor.putInt(DatabaseUpdate.DB_VERSION_KEY, new_DB_version);
        editor.apply();

        return Result.success(new Data.Builder().putInt(SUCCESS_REASON_KEY, SUCCESS_UPDATE_DONE).build());
    }

    public static Constraints getWorkConstraints(){
        return  new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(false).build();
    }

    public static WorkRequest newFirstTimeWorkRequest(){
        return new OneTimeWorkRequest.Builder(DBUpdateWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build();
    }

}
