/*
	BusTO  - Backend components
    Copyright (C) 2019 Fabio Mazza

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
package it.reyboz.bustorino.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import it.reyboz.bustorino.R;

/**
 * Class to handle app status modifications, e.g. database is being updated or not
 */
public class DBStatusManager {
    private static String PREFERENCES_NAME;// = "it.reyboz.bustorino.statusPreferences";
    private String DB_UPDATING;

    private SharedPreferences preferences;
    //private ArrayList<SharedPreferences.OnSharedPreferenceChangeListener> prefListeners;
    //private static DBStatusManager classinstance;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private OnDBUpdateStatusChangeListener dbUpdateListener;

    public DBStatusManager(Context context, OnDBUpdateStatusChangeListener listener) {
        Context thecon = context.getApplicationContext();
        this.preferences = thecon.getSharedPreferences(context.getString(R.string.mainSharedPreferences),Context.MODE_PRIVATE);
        DB_UPDATING = context.getString(R.string.databaseUpdatingPref);
        PREFERENCES_NAME = context.getString(R.string.mainSharedPreferences);
        dbUpdateListener = listener;
        //this.prefListeners = new ArrayList<>();
        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.d("BUSTO-PrefListener", "Changed key " + key + " in the sharedPref");

                if (key.equals(DB_UPDATING)) {
                    dbUpdateListener.onDBStatusChanged(sharedPreferences.getBoolean(DB_UPDATING, dbUpdateListener.defaultStatusValue()));

                }
            }
        };
    }


    public boolean isDBUpdating(boolean defaultvalue){
        if (preferences == null) //preferences = thecon.getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE);
        {
            //This should NOT HAPPEN
            Log.e("BUSTO_Pref","Preference reference is null");
            return false;
        } else {
            return preferences.getBoolean(DB_UPDATING,defaultvalue);
        }
    }


    public void registerListener(){
        if(prefListener!=null)
        preferences.registerOnSharedPreferenceChangeListener(prefListener);
    }

    public void unregisterListener(){
        if(prefListener!=null)
            preferences.unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    public void setDbUpdating(boolean value){
        final SharedPreferences.Editor editor  = preferences.edit();
        editor.putBoolean(DB_UPDATING,value);
        editor.apply();
    }

    public interface OnDBUpdateStatusChangeListener {
        void onDBStatusChanged(boolean updating);
        boolean defaultStatusValue();
    }
}
