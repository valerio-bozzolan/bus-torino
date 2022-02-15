/*
	BusTO ("backend" components)
    Copyright (C) 2016 Ludovico Pavesi

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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsDBInterface;

public class UserDB extends SQLiteOpenHelper {
	public static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "user.db";
	static final String TABLE_NAME = "favorites";
    private final Context c; // needed during upgrade
    private final static String[] usernameColumnNameAsArray = {"username"};
    public final static String[] getFavoritesColumnNamesAsArray = {"ID", "username"};

    private static final Uri FAVORITES_URI = AppDataProvider.getUriBuilderToComplete().appendPath(
            AppDataProvider.FAVORITES).build();


    public UserDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.c = context;
	}

    @Override
	public void onCreate(SQLiteDatabase db) {
        // exception intentionally left unhandled
		db.execSQL("CREATE TABLE favorites (ID TEXT PRIMARY KEY NOT NULL, username TEXT)");

        if(OldDB.doesItExist(this.c)) {
            upgradeFromOldDatabase(db);
        }
	}

    private void upgradeFromOldDatabase(SQLiteDatabase newdb) {
        OldDB old;
        try {
            old = new OldDB(this.c);
        } catch(IllegalStateException e) {
            // can't create database => it doesn't really exist, no matter what doesItExist() says
            return;
        }

        int ver = old.getOldVersion();

        /* version 8 was the previous version, OldDB "upgrades" itself to 1337 but unless the app
         * has crashed midway through the upgrade and the user is retrying, that should never show
         * up here. And if it does, try to recover favorites anyway.
         * Versions < 8 already got dropped during the update process, so let's do the same.
         *
         * Edit: Android runs getOldVersion() then, after a while, onUpgrade(). Just to make it
         * more complicated. Workaround added in OldDB.
         */
        if(ver >= 8) {
            ArrayList<String> ID = new ArrayList<>();
            ArrayList<String> username = new ArrayList<>();
            int len;
            int len2;

            try {
                Cursor c = old.getReadableDatabase().rawQuery("SELECT busstop_ID, busstop_username FROM busstop WHERE busstop_isfavorite = 1 ORDER BY busstop_name ASC", new String[] {});

                int zero = c.getColumnIndex("busstop_ID");
                int one = c.getColumnIndex("busstop_username");

                while(c.moveToNext()) {
                    try {
                        ID.add(c.getString(zero));
                    } catch(Exception e) {
                        // no ID = can't add this
                        continue;
                    }

                    if(c.getString(one) == null || c.getString(one).length() <= 0) {
                        username.add(null);
                    } else {
                        username.add(c.getString(one));
                    }
                }

                c.close();
                old.close();
            } catch(Exception ignored) {
                // there's no hope, go ahead and nuke old database.
            }

            len = ID.size();
            len2 = username.size();
            if(len2 < len) {
                len = len2;
            }


            if (len > 0) {

                try {
                    for (int i = 0; i < len; i++) {
                        final Stop mStop = new Stop(ID.get(i));
                        mStop.setStopUserName(username.get(i));
                        addOrUpdateStop(mStop, newdb);
                    }
                } catch(Exception ignored) {
                    // partial data is better than no data at all, no transactions here
                }
            }
        }

        if(!OldDB.destroy(this.c)) {
            // TODO: notify user somehow?
            Log.e("UserDB", "Failed to delete old database, you should really uninstall and reinstall the app. Unfortunately I have no way to tell the user.");
        }
    }

    @Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do yet
	}

    @Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do yet
	}

    /**
     * Check if a stop ID is in the favorites
     *
     * @param db readable database
     * @param stopId stop ID
     * @return boolean
     */
    public static boolean isStopInFavorites(SQLiteDatabase db, String stopId) {
        boolean found = false;

        try {
            Cursor c = db.query(TABLE_NAME, usernameColumnNameAsArray, "ID = ?", new String[] {stopId}, null, null, null);

            if(c.moveToNext()) {
                found = true;
            }

            c.close();
        } catch(SQLiteException ignored) {
            // don't care
        }

        return found;
    }

    /**
     * Gets stop name set by the user.
     *
     * @param db readable database
     * @param stopID stop ID
     * @return name set by user, or null if not set\not found
     */
    public static String getStopUserName(SQLiteDatabase db, String stopID) {
        String username = null;

        try {
            Cursor c = db.query(TABLE_NAME, usernameColumnNameAsArray, "ID = ?", new String[] {stopID}, null, null, null);

            if(c.moveToNext()) {
                int userNameIndex = c.getColumnIndex("username");
                if (userNameIndex>=0)
                    username = c.getString(userNameIndex);
            }
            c.close();
        } catch(SQLiteException ignored) {}

        return username;
    }

    /**
     * Get all the bus stops marked as favorites
     *
     * @param db
     * @param dbi
     * @return
     */
    public static List<Stop> getFavorites(SQLiteDatabase db, StopsDBInterface dbi) {
        List<Stop> l = new ArrayList<>();
        Stop s;
        String stopID, stopUserName;

        try {
            Cursor c = db.query(TABLE_NAME, getFavoritesColumnNamesAsArray, null, null, null, null, null, null);
            int colID = c.getColumnIndex("ID");
            int colUser = c.getColumnIndex("username");

            while(c.moveToNext()) {
                stopUserName = c.getString(colUser);
                stopID = c.getString(colID);

                s = dbi.getAllFromID(stopID);

                if(s == null) {
                    // can't find it in database
                    l.add(new Stop(stopUserName, stopID, null, null, null));
                } else {
                    // setStopName() already does sanity checks
                    s.setStopUserName(stopUserName);
                    l.add(s);
                }
            }
            c.close();
        } catch(SQLiteException ignored) {}

        // comparison rules are too complicated to let SQLite do this (e.g. it outputs: 3234, 34, 576, 67, 8222) and stop name is in another database
        Collections.sort(l);

        return l;
    }
    public static void notifyContentProvider(Context context){
        context.
                getContentResolver().
                notifyChange(FAVORITES_URI, null);
    }

    public static ArrayList<Stop> getFavoritesFromCursor(Cursor cursor, String[] columns){
        List<String> colsList = Arrays.asList(columns);
        if (!colsList.contains(getFavoritesColumnNamesAsArray[0]) || !colsList.contains(getFavoritesColumnNamesAsArray[1])){
            throw new IllegalArgumentException();
        }
        ArrayList<Stop> l = new ArrayList<>();
        if (cursor==null){
            Log.e("UserDB-BusTO", "Null cursor given in getFavoritesFromCursor");
            return l;
        }
        final int colID = cursor.getColumnIndex("ID");
        final int colUser = cursor.getColumnIndex("username");
        while(cursor.moveToNext()) {
            final String stopUserName = cursor.getString(colUser);
            final String stopID = cursor.getString(colID);
            final Stop s = new Stop(stopID.trim());
            if (stopUserName!=null) s.setStopUserName(stopUserName);

            l.add(s);
        }
        return l;

    }

    public static boolean addOrUpdateStop(Stop s, SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        long result = -1;
        String un = s.getStopUserName();

        cv.put("ID", s.ID);
        // is there an username?
        if(un == null) {
            // no: see if it's in the database
            cv.put("username", getStopUserName(db, s.ID));
        } else {
            // yes: use it
            cv.put("username", un);
        }

        try {
            //ignore and throw -1 if the row is already in the DB
            result = db.insertWithOnConflict(TABLE_NAME, null, cv,SQLiteDatabase.CONFLICT_IGNORE);
        } catch (SQLiteException ignored) {}

        // Android Studio suggested this unreadable replacement: return true if insert succeeded (!= -1), or try to update and return
        return (result != -1) || updateStop(s, db);
    }

    public static boolean updateStop(Stop s, SQLiteDatabase db) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("username", s.getStopUserName());
            db.update(TABLE_NAME, cv, "ID = ?", new String[]{s.ID});
            return true;
        } catch(SQLiteException e) {
            return false;
        }
    }

    public static boolean deleteStop(Stop s, SQLiteDatabase db) {
        try {
            db.delete(TABLE_NAME, "ID = ?", new String[]{s.ID});
            return true;
        } catch(SQLiteException e) {
            return false;
        }
    }
    public static boolean checkStopInFavorites(String stopID, Context con){
        boolean found = false;
        // no stop no party
        if (stopID != null) {
            SQLiteDatabase userDB = new UserDB(con).getReadableDatabase();
            found = UserDB.isStopInFavorites(userDB, stopID);
        }

        return found;
    }
}
