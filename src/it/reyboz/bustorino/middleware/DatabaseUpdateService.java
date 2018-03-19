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
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicReference;

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
    private static final int MAX_TRIALS = 5;
    public DatabaseUpdateService() {
        super("DatabaseUpdateService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startDBUpdate(Context context) {
        startDBUpdate(context,0);
    }
    public static void startDBUpdate(Context con, int trial){
        Intent intent = new Intent(con, DatabaseUpdateService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(TRIAL,trial);
        con.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE.equals(action)) {
                final int trial = intent.getIntExtra(TRIAL,-1);
                if(!isUpdateNeeded(trial)) return;

            }
        }
    }

    public void performDBUpdate(){

    }
    private boolean isUpdateNeeded(int trial){
        SharedPreferences shPr = getSharedPreferences(getString(R.string.mainSharedPreferences),MODE_PRIVATE);
        int versionDB = shPr.getInt(DB_VERSION,-1);
        if(versionDB==-1) return true;
        AtomicReference<Fetcher.result> gres = new AtomicReference<>();
        String networkRequest = FiveTAPIFetcher.performAPIRequest(FiveTAPIFetcher.QueryType.STOPS_VERSION,null,gres);
        if(networkRequest == null){
            if(gres.get()!= Fetcher.result.PARSER_ERROR){
              restartDBUpdateifPossible(trial);
            }
            return false;
        }

        boolean needed;
        try {
            JSONObject resp = new JSONObject(networkRequest);
            int ver = resp.getInt("id");
            if(ver>versionDB) {
                SharedPreferences.Editor editor = shPr.edit();
                editor.putInt(DB_VERSION, ver);
                //TODO: add the version date maybe?
                editor.apply();
                needed = true;
            } else {
                needed = false;
            }

        } catch (JSONException e) {
            e.printStackTrace();
            restartDBUpdateifPossible(trial);
            needed = false;
        }
        return needed;
    }
    private void restartDBUpdateifPossible(int currentTrial){
        if (currentTrial<MAX_TRIALS){
            Log.d(DEBUG_TAG,"Update failed, starting new trial ("+currentTrial+")");
            startDBUpdate(getApplicationContext(),++currentTrial);
        }
    }

}
