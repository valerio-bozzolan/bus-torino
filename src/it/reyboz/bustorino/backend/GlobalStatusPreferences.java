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

/**
 * Class to handle app status modifications, e.g. database is being updated or not
 */
public class GlobalStatusPreferences {
    private static final String PREFERENCES_NAME = "it.reyboz.bustorino.statusPreferences";
    private static final String DB_UPDATING = "DB_updating";

    private Context thecon;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener;

    public GlobalStatusPreferences(Context thecon) {
        this.thecon = thecon;
        this.preferences = thecon.getApplicationContext().getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE);
    }

    public boolean isDBUpdating(){
        if (preferences == null) preferences = thecon.getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE);
        return preferences.getBoolean(DB_UPDATING,false);
    }

    public void registerListener(OnDBStatusChangedListener listener){
        if(sharedPrefListener!=null) unregisterListener();
        sharedPrefListener = new FinishedUpdateListener(listener);
        preferences.registerOnSharedPreferenceChangeListener(sharedPrefListener);
    }

    public void unregisterListener(){
        preferences.unregisterOnSharedPreferenceChangeListener(sharedPrefListener);
    }
    public void setDbUpdating(boolean value){
        final SharedPreferences.Editor editor  = preferences.edit();
        editor.putBoolean(DB_UPDATING,value);
        editor.apply();
    }
    /**
     * Probably useless
     */
    public class FinishedUpdateListener implements SharedPreferences.OnSharedPreferenceChangeListener{
        private OnDBStatusChangedListener thingToDo;

        public FinishedUpdateListener(OnDBStatusChangedListener thingToDo) {
            this.thingToDo = thingToDo;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(key.equals(DB_UPDATING)){
                thingToDo.onDBUpdateStatusChanged(sharedPreferences.getBoolean(DB_UPDATING,true));
            }
        }
    }
    public interface OnDBStatusChangedListener {
        void onDBUpdateStatusChanged(boolean isUpdating);
    }

}
