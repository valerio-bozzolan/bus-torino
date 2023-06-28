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

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.work.*;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.mato.MatoAPIFetcher;
import it.reyboz.bustorino.data.gtfs.GtfsAgency;
import it.reyboz.bustorino.data.gtfs.GtfsDatabase;
import it.reyboz.bustorino.data.gtfs.GtfsDBDao;
import it.reyboz.bustorino.data.gtfs.GtfsFeed;
import it.reyboz.bustorino.data.gtfs.GtfsRoute;
import it.reyboz.bustorino.data.gtfs.MatoPattern;
import it.reyboz.bustorino.data.gtfs.PatternStop;
import kotlin.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
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
            DONE, ERROR_STOPS_DOWNLOAD, ERROR_LINES_DOWNLOAD, DB_CLOSED
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
    private static boolean updateGTFSAgencies(Context con, AtomicReference<Fetcher.Result> res){

        final GtfsDBDao dao = GtfsDatabase.Companion.getGtfsDatabase(con).gtfsDao();

        final Pair<List<GtfsFeed>, ArrayList<GtfsAgency>> respair = MatoAPIFetcher.Companion.getFeedsAndAgencies(
                con, res
        );

        dao.insertAgenciesWithFeeds(respair.getFirst(), respair.getSecond());

        return true;
    }
    private static HashMap<String, Set<String>> updateGTFSRoutes(Context con, AtomicReference<Fetcher.Result> res){

        final GtfsDBDao dao = GtfsDatabase.Companion.getGtfsDatabase(con).gtfsDao();

        final List<GtfsRoute> routes= MatoAPIFetcher.Companion.getRoutes(con, res);

        final HashMap<String,Set<String>> routesStoppingInStop = new HashMap<>();

        dao.insertRoutes(routes);
        if(res.get()!= Fetcher.Result.OK){
            return routesStoppingInStop;
        }
        final ArrayList<String> gtfsRoutesIDs = new ArrayList<>(routes.size());
        final HashMap<String,GtfsRoute> routesMap = new HashMap<>(routes.size());
        for(GtfsRoute r: routes){
            gtfsRoutesIDs.add(r.getGtfsId());
            routesMap.put(r.getGtfsId(),r);
        }
        long t0 = System.currentTimeMillis();
        final ArrayList<MatoPattern> patterns = MatoAPIFetcher.Companion.getPatternsWithStops(con,gtfsRoutesIDs,res);
        long tend = System.currentTimeMillis() - t0;
        Log.d(DEBUG_TAG, "Downloaded patterns in "+tend+" ms");
        if(res.get()!=Fetcher.Result.OK){
            Log.e(DEBUG_TAG, "Something went wrong downloading patterns");
            return routesStoppingInStop;
        }
        //match patterns with routes

        final ArrayList<PatternStop> patternStops = makeStopsForPatterns(patterns);
        final List<String> allPatternsCodeInDB = dao.getPatternsCodes();
        final HashSet<String> patternsCodesToDelete = new HashSet<>(allPatternsCodeInDB);

        for(MatoPattern p: patterns){
            //scan patterns
            final ArrayList<String> stopsIDs = p.getStopsGtfsIDs();
            final GtfsRoute mRoute = routesMap.get(p.getRouteGtfsId());
            if (mRoute == null) {
                Log.e(DEBUG_TAG, "Error in parsing the route: " + p.getRouteGtfsId() + " , cannot find the IDs in the map");
            }
            for (final String sID : stopsIDs) {
                //add stops to pattern stops
                // save routes stopping in the stop
                if (!routesStoppingInStop.containsKey(sID)) {
                    routesStoppingInStop.put(sID, new HashSet<>());
                }
                Set<String> mset = routesStoppingInStop.get(sID);
                assert mset != null;
                mset.add(mRoute.getShortName());
            }
            //finally, remove from deletion list
            patternsCodesToDelete.remove(p.getCode());
        }
        // final time for insert
        dao.insertPatterns(patterns);
        // clear patterns that are unused
        Log.d(DEBUG_TAG, "Have to remove "+patternsCodesToDelete.size()+ " patterns from the DB");
        dao.deletePatternsWithCodes(new ArrayList<>(patternsCodesToDelete));
        dao.insertPatternStops(patternStops);

        return routesStoppingInStop;
    }

    /**
     * Make the list of stops that each pattern does, to be inserted into the DB
     * @param patterns the MatoPattern
     * @return a list of PatternStop
     */
    public static ArrayList<PatternStop> makeStopsForPatterns(List<MatoPattern> patterns){
        final ArrayList<PatternStop> patternStops = new ArrayList<>(patterns.size());
        for (MatoPattern p: patterns){
            final ArrayList<String> stopsIDs = p.getStopsGtfsIDs();
            for (int i=0; i<stopsIDs.size(); i++) {
                //add stops to pattern stops
                final String ID = stopsIDs.get(i);
                patternStops.add(new PatternStop(p.getCode(), ID, i));
            }
        }
        return patternStops;
    }


    /**
     * Run the DB Update
     * @param con a context
     * @param gres a result reference
     * @return result of the update
     */
    public static Result performDBUpdate(Context con, AtomicReference<Fetcher.Result> gres) {

        // GTFS data fetching
        AtomicReference<Fetcher.Result> gtfsRes = new AtomicReference<>(Fetcher.Result.OK);
        updateGTFSAgencies(con, gtfsRes);
        if (gtfsRes.get()!= Fetcher.Result.OK){
            Log.w(DEBUG_TAG, "Could not insert the feeds and agencies stuff");
        } else{
            Log.d(DEBUG_TAG, "Done downloading agencies");
        }
        gtfsRes.set(Fetcher.Result.OK);
        final HashMap<String, Set<String>> routesStoppingByStop = updateGTFSRoutes(con,gtfsRes);
        if (gtfsRes.get()!= Fetcher.Result.OK){
            Log.w(DEBUG_TAG, "Could not insert the routes into DB");
        } else{
            Log.d(DEBUG_TAG, "Done downloading routes from MaTO");
        }
        /*db.beginTransaction();
        startTime = System.currentTimeMillis();
        int countStop = NextGenDB.writeLinesStoppingHere(db, routesStoppingByStop);
         if(countStop!= routesStoppingByStop.size()){
             Log.w(DEBUG_TAG, "Something went wrong in updating the linesStoppingBy, have "+countStop+" lines updated, with "
                     +routesStoppingByStop.size()+" stops to update");
         }
         db.setTransactionSuccessful();
         db.endTransaction();
         endTime = System.currentTimeMillis();
         Log.d(DEBUG_TAG, "Updating lines took "+(endTime-startTime)+" ms");
         */
        // Stops insertion
        final List<Palina> palinasMatoAPI = MatoAPIFetcher.Companion.getAllStopsGTT(con, gres);
        if (gres.get() != Fetcher.Result.OK) {
            Log.w(DEBUG_TAG, "Something went wrong downloading stops");
            return DatabaseUpdate.Result.ERROR_STOPS_DOWNLOAD;

        }
        final NextGenDB dbHelp = NextGenDB.getInstance(con.getApplicationContext());
        final SQLiteDatabase db = dbHelp.getWritableDatabase();

        if(!db.isOpen()){
            //catch errors like: java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase
            //we have to abort the work and restart it
            return Result.DB_CLOSED;
        }
        //TODO: Get the type of stop from the lines
        //Empty the needed tables

        db.beginTransaction();
        //db.execSQL("DELETE FROM "+StopsTable.TABLE_NAME);
        //db.delete(LinesTable.TABLE_NAME,null,null);

        //put new data
        long startTime = System.currentTimeMillis();

        Log.d(DEBUG_TAG, "Inserting " + palinasMatoAPI.size() + " stops");
        String routesStoppingString="";
        int patternsStopsHits = 0;
        for (final Palina p : palinasMatoAPI) {
            final ContentValues cv = new ContentValues();

            cv.put(NextGenDB.Contract.StopsTable.COL_ID, p.ID);
            cv.put(NextGenDB.Contract.StopsTable.COL_NAME, p.getStopDefaultName());
            if (p.location != null)
                cv.put(NextGenDB.Contract.StopsTable.COL_LOCATION, p.location);
            cv.put(NextGenDB.Contract.StopsTable.COL_LAT, p.getLatitude());
            cv.put(NextGenDB.Contract.StopsTable.COL_LONG, p.getLongitude());
            if (p.getAbsurdGTTPlaceName() != null) cv.put(NextGenDB.Contract.StopsTable.COL_PLACE, p.getAbsurdGTTPlaceName());
            if(p.gtfsID!= null && routesStoppingByStop.containsKey(p.gtfsID)){
                final ArrayList<String> routesSs= new ArrayList<>(routesStoppingByStop.get(p.gtfsID));
                routesStoppingString = Palina.buildRoutesStringFromNames(routesSs);
                patternsStopsHits++;
            } else{
                routesStoppingString = p.routesThatStopHereToString();
            }
            cv.put(NextGenDB.Contract.StopsTable.COL_LINES_STOPPING, routesStoppingString);
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
        Log.d(DEBUG_TAG, "\t"+patternsStopsHits+" routes string were built from the patterns");
        db.close();
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
    public static void requestDBUpdateWithWork(Context con,boolean restart, boolean forced){
        final SharedPreferences theShPr = PreferencesHolder.getMainSharedPreferences(con);
        final WorkManager workManager = WorkManager.getInstance(con);
        final Data reqData = new Data.Builder()
                .putBoolean(DBUpdateWorker.FORCED_UPDATE, forced).build();

        PeriodicWorkRequest wr = new PeriodicWorkRequest.Builder(DBUpdateWorker.class, 7, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(reqData)
                .build();
        final int version = theShPr.getInt(DatabaseUpdate.DB_VERSION_KEY, -10);
        final long lastDBUpdateTime = theShPr.getLong(DatabaseUpdate.DB_LAST_UPDATE_KEY, -10);
        if ((version >= 0 || lastDBUpdateTime >=0) && !restart)
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

    public static void watchUpdateWorkStatus(Context context, @NonNull LifecycleOwner lifecycleOwner,
                                             @NonNull Observer<? super List<WorkInfo>> observer) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.getWorkInfosForUniqueWorkLiveData(DBUpdateWorker.DEBUG_TAG).observe(
                lifecycleOwner, observer
        );
    }
}
