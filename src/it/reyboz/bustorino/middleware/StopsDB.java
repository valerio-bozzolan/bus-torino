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
import android.support.annotation.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final String PATH;
    private static String DB_NAME = "busto.sqlite";
    private static int DB_VERSION = 1;
    private SQLiteDatabase db;
    private AtomicInteger openCounter = new AtomicInteger();

    public StopsDB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.c = context;
        this.PATH = this.c.getFilesDir().getPath();
    }

    private void createDb() {
        try {
            this.getReadableDatabase();
            copyTables();
        } catch (IOException ignored) {}
    }

    private void copyTables() throws IOException {
        InputStream is = c.getAssets().open(DB_NAME);
        OutputStream os = new FileOutputStream(this.PATH.concat(DB_NAME));

        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }

        os.flush();
        os.close();
        is.close();
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
            return SQLiteDatabase.openDatabase(this.PATH.concat(DB_NAME), null, SQLiteDatabase.OPEN_READONLY);
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
        // TODO: implement (do we even need to do anything?)
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: implement
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: implement
    }

    @Override
    public List<String> getRoutesByStop(String stopID) {
        String[] uselessArray = {stopID};
        int count;

        Cursor result = db.rawQuery("SELECT route FROM routemap WHERE stop = ?", uselessArray);

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
}
