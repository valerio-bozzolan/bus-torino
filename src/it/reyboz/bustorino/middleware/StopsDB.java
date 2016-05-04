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

package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsDBInterface;

/**
 * There's just one single difference between this and SQLiteAssetHelper: this implementation works,
 * instead of crashing the app without any error message or exception whatsoever as soon as you try
 * to open the database.
 *
 * @see <a href="http://blog.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/">http://blog.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/</a>
 */
public class StopsDB extends SQLiteOpenHelper implements StopsDBInterface {
    private final Context c;
    private static String DB_NAME = "busto.sqlite";
    private static int DB_VERSION = 1;
    private final File filename;
    private SQLiteDatabase db;
    private AtomicInteger openCounter = new AtomicInteger();

    public StopsDB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.c = context;
        this.filename = new File(this.c.getFilesDir(), DB_NAME);
    }

    private void createDb() {
        //this.getReadableDatabase();

        InputStream is;
        OutputStream os;

        try {
            is = c.getAssets().open(DB_NAME);
        } catch(IOException e) {
            /* in case an upgrade is failing, nuke everything rather than leaving an old database
             * in place (should save us lots of headaches related to absurd non-reproducible bugs)
             */
            //noinspection ResultOfMethodCallIgnored
            filename.delete();
            return;
        }

        try {
            os = new FileOutputStream(filename);
        } catch(FileNotFoundException e) {
            //noinspection ResultOfMethodCallIgnored
            filename.delete();
            return;
        }

        byte[] buffer = new byte[1024];
        int length;

        try {
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            os.close();
            is.close();
        } catch(IOException e) {
            //noinspection ResultOfMethodCallIgnored
            filename.delete();
        }
    }

    /**
     * Through the magic of an atomic counter, the database gets opened and closed without race
     * conditions between threads (HOPEFULLY).
     *
     * @return database or null if cannot be opened
     */
    @Nullable
    public synchronized SQLiteDatabase openIfNeeded() {
        openCounter.incrementAndGet();

        if(this.db == null) {
            this.db = openRecursive(false);
        }
        return this.db;
    }

    /**
     * Making this function recursive is just a pointless exercise in style, since it calls itself exactly once.
     *
     * @param retrying set to true during recursion
     * @return SQLiteDatabase or null
     */
    @Nullable
    private SQLiteDatabase openRecursive(boolean retrying) {
        try {
            /* I have no idea why it worked without NO_LOCALIZED_COLLATORS, since it's mandatory
             * unless you place some Android-specific metadata tables that aren't that well documented...
             * Also, apparently there's no openDatabase() method that takes a File.
             */
            return SQLiteDatabase.openDatabase(this.filename.getPath(), null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch(SQLiteException e) {
            if(retrying) {
                // failed a 2nd time, give up
                return null;
            } else {
                // create it
                createDb();
                return openRecursive(true);
            }
        }
    }

    /**
     * Through the magic of an atomic counter, the database gets really closed only when no thread
     * is using it anymore (HOPEFULLY).
     */
    public synchronized void closeIfNeeded() {
        // is anybody still using the database or can we close it?
        if(openCounter.decrementAndGet() <= 0) {
            if (this.db != null && this.db.isOpen()) {
                this.db.close();
            }
            super.close();
            this.db = null;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // copy database (new install)
        createDb();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // destroy old database, replace with new
        createDb();
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // destroy new database, replace with old
        createDb();
    }

    @Override
    public List<String> getRoutesByStop(@NonNull String stopID) {
        String[] uselessArray = {stopID};
        int count;
        Cursor result;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.rawQuery("SELECT route FROM routemap WHERE stop = ?", uselessArray);
        } catch(SQLiteException e) {
            return null;
        }

        count = result.getCount();
        if(count == 0) {
            return null;
        }

        List<String> routes = new ArrayList<>(count);

        while(result.moveToNext()) {
            routes.add(result.getString(0));
        }

        result.close();

        return routes;
    }

    @Override
    public String getNameFromID(@NonNull String stopID) {
        String[] uselessArray = {stopID};
        int count;
        String name;
        Cursor result;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.rawQuery("SELECT name FROM stops WHERE ID = ?", uselessArray);
        } catch(SQLiteException e) {
            return null;
        }

        count = result.getCount();
        if(count == 0) {
            return null;
        }

        result.moveToNext();
        name = result.getString(0);

        result.close();

        return name;
    }

    @Override
    public Stop getAllFromID(@NonNull String stopID) {
        Cursor result;
        int count;
        Stop s;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.query("stops", new String[] {"name", "location", "type", "lat", "lon"}, "ID = ?", new String[] {stopID}, null, null, null, "1");
            int colName = result.getColumnIndex("name");
            int colLocation = result.getColumnIndex("location");
            int colType = result.getColumnIndex("type");
            int colLat = result.getColumnIndex("lat");
            int colLon = result.getColumnIndex("lon");

            count = result.getCount();
            if(count == 0) {
                return null;
            }

            result.moveToNext();

            Route.Type type;
            switch(result.getString(colType)) {
                case "B":
                default:
                    type = Route.Type.BUS;
                    break;
                case "M":
                    type = Route.Type.METRO;
                    break;
                case "T":
                    type = Route.Type.RAILWAY;
                    break;
            }

            String locationWhichSometimesIsAnEmptyString = result.getString(colLocation);
            if(locationWhichSometimesIsAnEmptyString.length() <= 0) {
                locationWhichSometimesIsAnEmptyString = null;
            }

            s = new Stop(stopID, result.getString(colName), null, locationWhichSometimesIsAnEmptyString, type, getRoutesByStop(stopID), result.getDouble(colLat), result.getDouble(colLon));
        } catch(SQLiteException e) {
            return null;
        }

        result.close();

        return s;
    }
}
