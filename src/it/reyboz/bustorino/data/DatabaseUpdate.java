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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.work.*;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.mato.MatoAPIFetcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.MODE_PRIVATE;


public class DatabaseUpdate {

    public static final String DEBUG_TAG = "BusTO-DBUpdate";
    public static final int VERSION_UNAVAILABLE = -2;
    public static final int JSON_PARSING_ERROR = -4;

    public static final String DB_VERSION_KEY = "NextGenDB.GTTVersion";
    public static final String DB_LAST_UPDATE_KEY = "NextGenDB.LastDBUpdate";


    enum Result {
            DONE, ERROR_STOPS_DOWNLOAD, ERROR_LINES_DOWNLOAD
        }

        /**
         * Request the server the version of the database
         * @return the version of the DB, or an error code
         */
        public static int getNewVersion(){
            AtomicReference<Fetcher.Result> gres = new AtomicReference<>();
            String networkRequest = FiveTAPIFetcher.performAPIRequest(FiveTAPIFetcher.QueryType.STOPS_VERSION,null,gres);
            if(networkRequest == null){
                return VERSION_UNAVAILABLE;
            }

            try {
                JSONObject resp = new JSONObject(networkRequest);
                return resp.getInt("id");
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(DEBUG_TAG,"Error: wrong JSON response\nResponse:\t"+networkRequest);
                return JSON_PARSING_ERROR;
            }
        }
        /**
         * Run the DB Update
         * @param con a context
         * @param gres a result reference
         * @return result of the update
         */
        public static Result performDBUpdate(Context con, AtomicReference<Fetcher.Result> gres) {

            final FiveTAPIFetcher f = new FiveTAPIFetcher();
            /*
            final ArrayList<Stop> stops = f.getAllStopsFromGTT(gres);
            //final ArrayList<ContentProviderOperation> cpOp = new ArrayList<>();

            if (gres.get() != Fetcher.Result.OK) {
                Log.w(DEBUG_TAG, "Something went wrong downloading");
                return DatabaseUpdate.Result.ERROR_STOPS_DOWNLOAD;

            }

             */
            final NextGenDB dbHelp = new NextGenDB(con.getApplicationContext());
            final SQLiteDatabase db = dbHelp.getWritableDatabase();

            final List<Palina> palinasMatoAPI = MatoAPIFetcher.Companion.getAllStopsGTT(con, gres);
            if (gres.get() != Fetcher.Result.OK) {
                Log.w(DEBUG_TAG, "Something went wrong downloading");
                return DatabaseUpdate.Result.ERROR_STOPS_DOWNLOAD;

            }
            //TODO: Get the type of stop from the lines
            //Empty the needed tables
            db.beginTransaction();
            //db.execSQL("DELETE FROM "+StopsTable.TABLE_NAME);
            //db.delete(LinesTable.TABLE_NAME,null,null);

            //put new data
            long startTime = System.currentTimeMillis();

            Log.d(DEBUG_TAG, "Inserting " + palinasMatoAPI.size() + " stops");
            for (final Palina p : palinasMatoAPI) {
                final ContentValues cv = new ContentValues();

                cv.put(NextGenDB.Contract.StopsTable.COL_ID, p.ID);
                cv.put(NextGenDB.Contract.StopsTable.COL_NAME, p.getStopDefaultName());
                if (p.location != null)
                    cv.put(NextGenDB.Contract.StopsTable.COL_LOCATION, p.location);
                cv.put(NextGenDB.Contract.StopsTable.COL_LAT, p.getLatitude());
                cv.put(NextGenDB.Contract.StopsTable.COL_LONG, p.getLongitude());
                if (p.getAbsurdGTTPlaceName() != null) cv.put(NextGenDB.Contract.StopsTable.COL_PLACE, p.getAbsurdGTTPlaceName());
                cv.put(NextGenDB.Contract.StopsTable.COL_LINES_STOPPING, p.routesThatStopHereToString());
                if (p.type != null) cv.put(NextGenDB.Contract.StopsTable.COL_TYPE, p.type.getCode());
                if (p.gtfsID != null) cv.put(NextGenDB.Contract.StopsTable.COL_GTFS_ID, p.gtfsID);
                //Log.d(DEBUG_TAG,cv.toString());
                //cpOp.add(ContentProviderOperation.newInsert(uritobeused).withValues(cv).build());
                //valuesArr[i] = cv;
                db.replace(NextGenDB.Contract.StopsTable.TABLE_NAME, null, cv);

            }
            db.setTransactionSuccessful();
            db.endTransaction();
            long endTime = System.currentTimeMillis();
            Log.d(DEBUG_TAG, "Inserting stops took: " + ((double) (endTime - startTime) / 1000) + " s");

            final ArrayList<Route> routes = f.getAllLinesFromGTT(gres);

            if (routes == null) {
                Log.w(DEBUG_TAG, "Something went wrong downloading the lines");
                dbHelp.close();
                return DatabaseUpdate.Result.ERROR_LINES_DOWNLOAD;

            }

            db.beginTransaction();
            startTime = System.currentTimeMillis();
            for (Route r : routes) {
                final ContentValues cv = new ContentValues();
                cv.put(NextGenDB.Contract.LinesTable.COLUMN_NAME, r.getName());
                switch (r.type) {
                    case BUS:
                        cv.put(NextGenDB.Contract.LinesTable.COLUMN_TYPE, "URBANO");
                        break;
                    case RAILWAY:
                        cv.put(NextGenDB.Contract.LinesTable.COLUMN_TYPE, "FERROVIA");
                        break;
                    case LONG_DISTANCE_BUS:
                        cv.put(NextGenDB.Contract.LinesTable.COLUMN_TYPE, "EXTRA");
                        break;
                }
                cv.put(NextGenDB.Contract.LinesTable.COLUMN_DESCRIPTION, r.description);

                //db.insert(LinesTable.TABLE_NAME,null,cv);
                int rows = db.update(NextGenDB.Contract.LinesTable.TABLE_NAME, cv, NextGenDB.Contract.LinesTable.COLUMN_NAME + " = ?", new String[]{r.getName()});
                if (rows < 1) { //we haven't changed anything
                    db.insert(NextGenDB.Contract.LinesTable.TABLE_NAME, null, cv);
                }
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            endTime = System.currentTimeMillis();
            Log.d(DEBUG_TAG, "Inserting lines took: " + ((double) (endTime - startTime) / 1000) + " s");
            dbHelp.close();

            return DatabaseUpdate.Result.DONE;
        }

    public static boolean setDBUpdatingFlag(Context con, boolean value){
        final SharedPreferences shPr = con.getSharedPreferences(con.getString(R.string.mainSharedPreferences),MODE_PRIVATE);
        return setDBUpdatingFlag(con, shPr, value);
    }
    static boolean setDBUpdatingFlag(Context con, SharedPreferences shPr,boolean value){
        final SharedPreferences.Editor editor = shPr.edit();
        editor.putBoolean(con.getString(R.string.databaseUpdatingPref),value);
        return editor.commit();
    }

    /**
     * Request update using workmanager framework
     * @param con the context to use
     * @param forced if you want to force the request to go now
     */
    public static void requestDBUpdateWithWork(Context con, boolean forced){
        final SharedPreferences theShPr = PreferencesHolder.getMainSharedPreferences(con);
        final WorkManager workManager = WorkManager.getInstance(con);
        PeriodicWorkRequest wr = new PeriodicWorkRequest.Builder(DBUpdateWorker.class, 7, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        final int version = theShPr.getInt(DatabaseUpdate.DB_VERSION_KEY, -10);
        final long lastDBUpdateTime = theShPr.getLong(DatabaseUpdate.DB_LAST_UPDATE_KEY, -10);
        if ((version >= 0 || lastDBUpdateTime >=0) && !forced)
            workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                    ExistingPeriodicWorkPolicy.KEEP, wr);
        else workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, wr);
    }
    /*
    public static boolean isDBUpdating(){
        return false;
        TODO
    }
     */
}
