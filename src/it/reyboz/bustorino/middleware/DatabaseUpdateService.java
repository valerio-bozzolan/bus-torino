/*
	BusTO (middleware)
    Copyright (C) 2018 Fabio Mazza

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
package it.reyboz.bustorino.middleware;

import android.app.IntentService;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import android.util.Log;
import it.reyboz.bustorino.ActivityMain;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static it.reyboz.bustorino.middleware.NextGenDB.Contract.*;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class DatabaseUpdateService extends IntentService {
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_UPDATE = "it.reyboz.bustorino.middleware.action.UPDATE_DB";
    private static final String DB_VERSION = "NextGenDB.GTTVersion";
    private static final String DEBUG_TAG = "DatabaseService_BusTO";
    // TODO: Rename parameters
    private static final String TRIAL = "it.reyboz.bustorino.middleware.extra.TRIAL";
    private static final String COMPULSORY = "compulsory_update";
    private static final int MAX_TRIALS = 5;
    private static final int VERSION_UNAIVALABLE = -2;
    public DatabaseUpdateService() {
        super("DatabaseUpdateService");
    }
    private boolean isRunning;
    private int updateTrial;
    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startDBUpdate(Context con, int trial, @Nullable Boolean mustUpdate){
        Intent intent = new Intent(con, DatabaseUpdateService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(TRIAL,trial);
        if(mustUpdate!=null){
            intent.putExtra(COMPULSORY,mustUpdate);
        }
        con.startService(intent);
    }
    public static void startDBUpdate(Context con) {
        startDBUpdate(con, 0, false);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE.equals(action)) {
                Log.d(DEBUG_TAG,"Started action update");
                SharedPreferences shPr = getSharedPreferences(getString(R.string.mainSharedPreferences),MODE_PRIVATE);
                int versionDB = shPr.getInt(DB_VERSION,-1);
                final int trial = intent.getIntExtra(TRIAL,-1);

                updateTrial = trial;
                UpdateRequestParams params = new UpdateRequestParams(intent);
                int newVersion = getNewVersion(params);
                if(newVersion==VERSION_UNAIVALABLE){
                    //NOTHING LEFT TO DO
                    return;
                }
                Log.d(DEBUG_TAG,"newDBVersion: "+newVersion+" oldVersion: "+versionDB);
                if(params.mustUpdate || versionDB==-1 || newVersion>versionDB){
                    final SharedPreferences.Editor editor = shPr.edit();
                    editor.putBoolean(getString(R.string.databaseUpdatingPref),true);
                    editor.commit();
                    Log.d(DEBUG_TAG,"Downloading the bus stops info");
                    final AtomicReference<Fetcher.result> gres = new AtomicReference<>();
                    if(!performDBUpdate(gres))
                        restartDBUpdateifPossible(params,gres);
                    else {
                        editor.putInt(DB_VERSION,newVersion);
                        //  BY COMMENTING THIS, THE APP WILL CONTINUOUSLY UPDATE THE DATABASE
                        editor.apply();
                    }
                } else {
                    Log.d(DEBUG_TAG,"No update needed");
                }


                Log.d(DEBUG_TAG,"Finished update");
                final SharedPreferences.Editor editor = shPr.edit();
                editor.putBoolean(getString(R.string.databaseUpdatingPref),false);
                editor.commit();
            }
        }
    }

    private boolean performDBUpdate(AtomicReference<Fetcher.result> gres){

        final FiveTAPIFetcher f = new FiveTAPIFetcher();
        final ArrayList<Stop> stops = f.getAllStopsFromGTT(gres);
        //final ArrayList<ContentProviderOperation> cpOp = new ArrayList<>();

        if(gres.get()!= Fetcher.result.OK){
            Log.w(DEBUG_TAG,"Something went wrong downloading");
            return false;

        }
        final NextGenDB dbHelp = new NextGenDB(getApplicationContext());
        final SQLiteDatabase db = dbHelp.getWritableDatabase();
        //Empty the needed tables
        db.beginTransaction();
        //db.execSQL("DELETE FROM "+StopsTable.TABLE_NAME);
        //db.delete(LinesTable.TABLE_NAME,null,null);

        //put new data
        long startTime = System.currentTimeMillis();

        Log.d(DEBUG_TAG,"Inserting "+stops.size()+" stops");
        for (final Stop s : stops) {
            final ContentValues cv = new ContentValues();

            cv.put(StopsTable.COL_ID, s.ID);
            cv.put(StopsTable.COL_NAME, s.getStopDefaultName());
            if (s.location != null)
                cv.put(StopsTable.COL_LOCATION, s.location);
            cv.put(StopsTable.COL_LAT, s.getLatitude());
            cv.put(StopsTable.COL_LONG, s.getLongitude());
            if (s.getAbsurdGTTPlaceName() != null) cv.put(StopsTable.COL_PLACE, s.getAbsurdGTTPlaceName());
            cv.put(StopsTable.COL_LINES_STOPPING, s.routesThatStopHereToString());
            if (s.type != null) cv.put(StopsTable.COL_TYPE, s.type.getCode());

            //Log.d(DEBUG_TAG,cv.toString());
            //cpOp.add(ContentProviderOperation.newInsert(uritobeused).withValues(cv).build());
            //valuesArr[i] = cv;
            db.replace(StopsTable.TABLE_NAME,null,cv);

        }
        db.setTransactionSuccessful();
        db.endTransaction();
        long endTime = System.currentTimeMillis();
        Log.d(DEBUG_TAG,"Inserting stops took: "+((double) (endTime-startTime)/1000)+" s");

        final ArrayList<Route> routes = f.getAllLinesFromGTT(gres);

        if(routes==null){
            Log.w(DEBUG_TAG,"Something went wrong downloading the lines");
            dbHelp.close();
            return false;

        }

        db.beginTransaction();
        startTime = System.currentTimeMillis();
        for (Route r: routes){
            final ContentValues cv = new ContentValues();
            cv.put(LinesTable.COLUMN_NAME,r.getName());
            switch (r.type){
                case BUS:
                    cv.put(LinesTable.COLUMN_TYPE,"URBANO");
                    break;
                case RAILWAY:
                    cv.put(LinesTable.COLUMN_TYPE,"FERROVIA");
                    break;
                case LONG_DISTANCE_BUS:
                    cv.put(LinesTable.COLUMN_TYPE,"EXTRA");
                    break;
            }
            cv.put(LinesTable.COLUMN_DESCRIPTION,r.description);

            //db.insert(LinesTable.TABLE_NAME,null,cv);
            int rows = db.update(LinesTable.TABLE_NAME,cv,LinesTable.COLUMN_NAME+" = ?",new String[]{r.getName()});
            if(rows<1){ //we haven't changed anything
                db.insert(LinesTable.TABLE_NAME,null,cv);
            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        endTime = System.currentTimeMillis();
        Log.d(DEBUG_TAG,"Inserting lines took: "+((double) (endTime-startTime)/1000)+" s");
        dbHelp.close();
        return true;
    }
    private int getNewVersion(UpdateRequestParams params){
        AtomicReference<Fetcher.result> gres = new AtomicReference<>();
        String networkRequest = FiveTAPIFetcher.performAPIRequest(FiveTAPIFetcher.QueryType.STOPS_VERSION,null,gres);
        if(networkRequest == null){
            restartDBUpdateifPossible(params,gres);
            return VERSION_UNAIVALABLE;
        }

        boolean needed;
        try {
            JSONObject resp = new JSONObject(networkRequest);
            return resp.getInt("id");
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(DEBUG_TAG,"Error: wrong JSON response\nResponse:\t"+networkRequest);
            return -4;
        }
    }
    private void restartDBUpdateifPossible(UpdateRequestParams pars, AtomicReference<Fetcher.result> res){
        if (pars.trial<MAX_TRIALS && res.get()!= Fetcher.result.PARSER_ERROR){
            Log.d(DEBUG_TAG,"Update failed, starting new trial ("+pars.trial+")");
            startDBUpdate(getApplicationContext(),++pars.trial,pars.mustUpdate);
        }
    }

    /**
     * Private class to hold the parameters of the request
     */
    private class UpdateRequestParams{
        int trial;
        boolean mustUpdate;


        UpdateRequestParams(Intent intent){
            trial = intent.getIntExtra(TRIAL,-1);
            mustUpdate = intent.getBooleanExtra(COMPULSORY, false);
        }
    }

    /**
     * Probably useless
     *
     * Spoiler: IT IS
    public class FinishedUpdateListener implements SharedPreferences.OnSharedPreferenceChangeListener{
        private OnDBStatusChangedListener thingToDo;

        public FinishedUpdateListener(OnDBStatusChangedListener thingToDo) {
            this.thingToDo = thingToDo;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            final String Update_KEY = getString(R.string.databaseUpdatingPref);
            if(key.equals(Update_KEY)){
                thingToDo.onDBUpdateStatusChanged(sharedPreferences.getBoolean(Update_KEY,true));
            }
        }
    }
    public interface OnDBStatusChangedListener {
        void onDBUpdateStatusChanged(boolean isUpdating);
    }
        */
}
