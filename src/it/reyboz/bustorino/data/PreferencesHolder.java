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

import android.content.Context;
import android.content.SharedPreferences;
import it.reyboz.bustorino.R;

import static android.content.Context.MODE_PRIVATE;

/**
 * Static class for commonly used SharedPreference operations
 */
public abstract class PreferencesHolder {

    public static final String PREF_GTFS_DB_VERSION = "gtfs_db_version";

    public static SharedPreferences getMainSharedPreferences(Context context){
        return context.getSharedPreferences(context.getString(R.string.mainSharedPreferences), MODE_PRIVATE);
    }

    public static int getGtfsDBVersion(SharedPreferences pref){
        return pref.getInt(PREF_GTFS_DB_VERSION,-1);
    }
    public static void setGtfsDBVersion(SharedPreferences pref,int version){
        SharedPreferences.Editor ed = pref.edit();
        ed.putInt(PREF_GTFS_DB_VERSION,version);
        ed.apply();
    }
}
