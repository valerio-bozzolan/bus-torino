package it.reyboz.bustorino.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.MODE_PRIVATE;


public class DatabaseUpdate {

        public static final String DEBUG_TAG = "BusTO-DBUpdate";
        public static final int VERSION_UNAVAILABLE = -2;
        public static final int JSON_PARSING_ERROR = -4;

        public static final String DB_VERSION_KEY = "NextGenDB.GTTVersion";

        enum Result {
            DONE, ERROR_STOPS_DOWNLOAD, ERROR_LINES_DOWNLOAD
        }

        /**
         * Request the server the version of the database
         * @return the version of the DB, or an error code
         */
        public static int getNewVersion(){
            AtomicReference<Fetcher.result> gres = new AtomicReference<>();
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
        public static Result performDBUpdate(Context con, AtomicReference<Fetcher.result> gres) {

            final FiveTAPIFetcher f = new FiveTAPIFetcher();
            final ArrayList<Stop> stops = f.getAllStopsFromGTT(gres);
            //final ArrayList<ContentProviderOperation> cpOp = new ArrayList<>();

            if (gres.get() != Fetcher.result.OK) {
                Log.w(DEBUG_TAG, "Something went wrong downloading");
                return Result.ERROR_STOPS_DOWNLOAD;

            }
            //    return false; //If the commit to the SharedPreferences didn't succeed, simply stop updating the database
            final NextGenDB dbHelp = new NextGenDB(con.getApplicationContext());
            final SQLiteDatabase db = dbHelp.getWritableDatabase();
            //Empty the needed tables
            db.beginTransaction();
            //db.execSQL("DELETE FROM "+StopsTable.TABLE_NAME);
            //db.delete(LinesTable.TABLE_NAME,null,null);

            //put new data
            long startTime = System.currentTimeMillis();

            Log.d(DEBUG_TAG, "Inserting " + stops.size() + " stops");
            for (final Stop s : stops) {
                final ContentValues cv = new ContentValues();

                cv.put(NextGenDB.Contract.StopsTable.COL_ID, s.ID);
                cv.put(NextGenDB.Contract.StopsTable.COL_NAME, s.getStopDefaultName());
                if (s.location != null)
                    cv.put(NextGenDB.Contract.StopsTable.COL_LOCATION, s.location);
                cv.put(NextGenDB.Contract.StopsTable.COL_LAT, s.getLatitude());
                cv.put(NextGenDB.Contract.StopsTable.COL_LONG, s.getLongitude());
                if (s.getAbsurdGTTPlaceName() != null) cv.put(NextGenDB.Contract.StopsTable.COL_PLACE, s.getAbsurdGTTPlaceName());
                cv.put(NextGenDB.Contract.StopsTable.COL_LINES_STOPPING, s.routesThatStopHereToString());
                if (s.type != null) cv.put(NextGenDB.Contract.StopsTable.COL_TYPE, s.type.getCode());

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
                return Result.ERROR_LINES_DOWNLOAD;

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

            return Result.DONE;
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
}
