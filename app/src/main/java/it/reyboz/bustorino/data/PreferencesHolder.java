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

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Static class for commonly used SharedPreference operations
 */
public abstract class PreferencesHolder {

    public static final String PREF_GTFS_DB_VERSION = "gtfs_db_version";
    public static final String PREF_INTRO_ACTIVITY_RUN ="pref_intro_activity_run";
    public static final String DB_GTT_VERSION_KEY = "NextGenDB.GTTVersion";
    public static final String DB_LAST_UPDATE_KEY = "NextGenDB.LastDBUpdate";
    public static final String PREF_FAVORITE_LINES = "pref_favorite_lines";

    public static final Set<String> IGNORE_KEYS_LOAD_MAIN = Set.of(PREF_GTFS_DB_VERSION, PREF_INTRO_ACTIVITY_RUN, DB_GTT_VERSION_KEY, DB_LAST_UPDATE_KEY);

    public static SharedPreferences getMainSharedPreferences(Context context){
        return context.getSharedPreferences(context.getString(R.string.mainSharedPreferences), MODE_PRIVATE);
    }

    public static SharedPreferences getAppPreferences(Context con){
        return PreferenceManager.getDefaultSharedPreferences(con);
    }

    public static int getGtfsDBVersion(SharedPreferences pref){
        return pref.getInt(PREF_GTFS_DB_VERSION,-1);
    }
    public static void setGtfsDBVersion(SharedPreferences pref,int version){
        SharedPreferences.Editor ed = pref.edit();
        ed.putInt(PREF_GTFS_DB_VERSION,version);
        ed.apply();
    }

    /**
     * Check if the introduction activity has been run at least one
     * @param con the context needed
     * @return true if it has been run
     */
    public static boolean hasIntroFinishedOneShot(Context con){
        final SharedPreferences pref = getMainSharedPreferences(con);
        return pref.getBoolean(PREF_INTRO_ACTIVITY_RUN, false);
    }

    public static boolean addOrRemoveLineToFavorites(Context con, String gtfsLineId, boolean addToFavorites){
        final SharedPreferences pref = getMainSharedPreferences(con);
        final HashSet<String> favorites = new HashSet<>(pref.getStringSet(PREF_FAVORITE_LINES, new HashSet<>()));
        boolean modified = true;
        if(addToFavorites)
            favorites.add(gtfsLineId);
        else if(favorites.contains(gtfsLineId))
            favorites.remove(gtfsLineId);
        else
            modified = false; // we are not changing anything
        if(modified) {
            final SharedPreferences.Editor editor = pref.edit();
            editor.putStringSet(PREF_FAVORITE_LINES, favorites);
            editor.apply();
        }
        return modified;
    }

    public static HashSet<String> getFavoritesLinesGtfsIDs(Context con){
        final SharedPreferences pref = getMainSharedPreferences(con);
        return new HashSet<>(pref.getStringSet(PREF_FAVORITE_LINES, new HashSet<>()));
    }
}
