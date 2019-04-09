/*
	BusTO  - Backend components
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
package it.reyboz.bustorino.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import it.reyboz.bustorino.R;

import java.util.ArrayList;

/**
 * Class to handle app status modifications, e.g. database is being updated or not
 */
public class GlobalStatusPreferences {
    private static String PREFERENCES_NAME;// = "it.reyboz.bustorino.statusPreferences";
    private String DB_UPDATING;

    private SharedPreferences preferences;
    private ArrayList<SharedPreferences.OnSharedPreferenceChangeListener> prefListeners;
    private static GlobalStatusPreferences classinstance;

    private GlobalStatusPreferences(Context context) {
        Context thecon = context.getApplicationContext();
        this.preferences = thecon.getSharedPreferences(context.getString(R.string.mainSharedPreferences),Context.MODE_PRIVATE);
        DB_UPDATING = context.getString(R.string.databaseUpdatingPref);
        PREFERENCES_NAME = context.getString(R.string.mainSharedPreferences);
        this.prefListeners = new ArrayList<>();
    }
    public static GlobalStatusPreferences getInstance(Context con){
        if(classinstance == null) classinstance = new GlobalStatusPreferences(con);
        return classinstance;
    }


    public boolean isDBUpdating(){
        if (preferences == null) //preferences = thecon.getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE);
        {
            //This should NOT HAPPEN
            Log.e("BUSTO_Pref","Preference reference is null");
            return false;
        } else {
            return preferences.getBoolean(DB_UPDATING,false);
        }
    }

    /**
     * Add a Listener to the list of the ones to be notified
     * @param listener a OnSharedPreferenceChangeListener to add
     * @return the same listener to store the reference to
     */
    public SharedPreferences.OnSharedPreferenceChangeListener
    registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener){
        if(!prefListeners.contains(listener)) prefListeners.add(listener);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        return listener;
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener){
        prefListeners.remove(listener);
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
    public void emptyListeners(){

        for(SharedPreferences.OnSharedPreferenceChangeListener lis : prefListeners){
            preferences.unregisterOnSharedPreferenceChangeListener(lis);

        }
        prefListeners.clear();
    }
    public void setDbUpdating(boolean value){
        final SharedPreferences.Editor editor  = preferences.edit();
        editor.putBoolean(DB_UPDATING,value);
        editor.apply();
    }
}
