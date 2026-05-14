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
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.writer.CsvWriter;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopFavoritesData;
import it.reyboz.bustorino.backend.StopsDBInterface;

public class UserDB extends SQLiteOpenHelper {
	public static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "user.db";
	static final String TABLE_NAME = "favorites";
    private final Context c; // needed during upgrade
    public final static String COL_ID = "ID";
    public final static String COL_USERNAME="username";

    public static final int FILE_INVALID=-10;
    private static final String DEBUG_TAG = "BusTO-FavoritesUserDB";
    private final static String[] usernameColumnNameAsArray = {"username"};
    public final static String[] FAVORITES_COLUMNS_ARRAY = {COL_ID, COL_USERNAME};

    private final InvalidationTracker  invalidationTracker = new InvalidationTracker();

    private static final Uri FAVORITES_URI = AppDataProvider.getUriBuilderToComplete().appendPath(
            AppDataProvider.FAVORITES).build();

    private static UserDB mInstance;

    public static synchronized UserDB getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new UserDB(context.getApplicationContext());
        }
        return mInstance;
    }

    private UserDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.c = context.getApplicationContext();
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
     **
     * @param stopId stop ID
     * @return boolean
     */
    public boolean isStopInFavorites(String stopId) {

        SQLiteDatabase db = this.getReadableDatabase();
        boolean found = false;

        try {
            // better way to check the existence
            long count = DatabaseUtils.queryNumEntries(db, TABLE_NAME, "ID = ?",
                    new String[]{stopId});
            return count > 0;
        } catch(SQLiteException e) {
            // don't care
            Log.w("BusTO-UserDB", "isStopInFavorites failed for " + stopId, e);
        }

        return found;
    }

    /**
     * Gets stop name set by the user.
     *
     * @param stopID stop ID
     * @return name set by user, or null if not set\not found
     */
    private @Nullable String getStopUserName(SQLiteDatabase db,String stopID) {
        String username = null;

        try(Cursor c = db.query(TABLE_NAME, usernameColumnNameAsArray, "ID = ?",
                new String[] {stopID}, null, null, null)) {

            if(c.moveToNext()) {
                int userNameIndex = c.getColumnIndex("username");
                if (userNameIndex>=0)
                    username = c.getString(userNameIndex);
            }
        } catch(SQLiteException e) {
            Log.e("BusTO-UserDB","Cannot get stop User name for stop "+stopID+":\n"+e);
        }

        return username;
    }
    public @Nullable String getStopUserName(String stopID) {
        SQLiteDatabase db = this.getReadableDatabase();
        return  getStopUserName(db,stopID);
    }

    /**
     * Get all the bus stops marked as favorites
     *
     * @param dbi
     * @return
     */
    public List<Stop> getFavorite( StopsDBInterface dbi) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Stop> l = new ArrayList<>();
        Stop s;
        String stopID, stopUserName;

        try {
            Cursor c = db.query(TABLE_NAME, FAVORITES_COLUMNS_ARRAY, null, null, null, null, null, null);
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
        if (!colsList.contains(FAVORITES_COLUMNS_ARRAY[0]) || !colsList.contains(FAVORITES_COLUMNS_ARRAY[1])){
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

    @NonNull
    public ArrayList<StopFavoritesData> getAllFavoritesData(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, FAVORITES_COLUMNS_ARRAY, null, null, null, null, null);

        ArrayList<StopFavoritesData> l = new ArrayList<>();
        final int colID = cursor.getColumnIndex("ID");
        final int colUser = cursor.getColumnIndex("username");
        while(cursor.moveToNext()) {
            final String stopUserName = cursor.getString(colUser);
            final String stopID = cursor.getString(colID);

            l.add(new StopFavoritesData(stopID, stopUserName));
        }
        cursor.close();
        return l;

    }
    @Nullable
    public ArrayList<StopFavoritesData> queryDataForStopIds(List<String> stopIds) {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<StopFavoritesData> result = null;

        final String whereClause = NextGenDB.buildWhereClause(COL_ID, stopIds);
        final String[] whereArgs = stopIds.toArray(new String[0]);
        Log.d(DEBUG_TAG, "queryDtaForStopId: " + whereClause+ " args: " + Arrays.toString(whereArgs));
        try(Cursor c =  db.query(
                TABLE_NAME, FAVORITES_COLUMNS_ARRAY, whereClause,
                whereArgs, null, null, null, null)){

            result = getFavoritesDataFromCursor(c, FAVORITES_COLUMNS_ARRAY);
        }
        catch(SQLiteException e) {
            Log.e(DEBUG_TAG, "queryDataForStopIds favorites failed for " + stopIds, e);
            return null;
        }

        return result;
    }

    @NonNull
    public QueryLiveData<List<StopFavoritesData>> getLiveDataForStopIds(List<String> stopIds) {
        return new QueryLiveData<>(List.of(TABLE_NAME), invalidationTracker, () -> {
            Log.d(DEBUG_TAG, "Favorites table changed, redoing query");
            return queryDataForStopIds(stopIds);
        });
    }
    @NonNull
    public QueryLiveData<List<StopFavoritesData>> getFavoritesLiveData() {
        return new QueryLiveData<>(List.of(TABLE_NAME), invalidationTracker, () -> {
            Log.d(DEBUG_TAG, "Favorites table changed, redoing query");
            return getAllFavoritesData();
        });
    }


    @NonNull
    public static ArrayList<StopFavoritesData> getFavoritesDataFromCursor(@NonNull Cursor cursor, String[] columns){
        List<String> colsList = Arrays.asList(columns);
        if (!colsList.contains(FAVORITES_COLUMNS_ARRAY[0]) || !colsList.contains(FAVORITES_COLUMNS_ARRAY[1])){
            throw new IllegalArgumentException();
        }
        ArrayList<StopFavoritesData> l = new ArrayList<>();
        final int colID = cursor.getColumnIndex("ID");
        final int colUser = cursor.getColumnIndex("username");
        while(cursor.moveToNext()) {
            final String stopUserName = cursor.getString(colUser);
            final String stopID = cursor.getString(colID);
            l.add(new StopFavoritesData(stopID, stopUserName));
        }
        return l;

    }
    public boolean addOrUpdateStop(Stop s) {
        return addOrUpdateStop(s.ID, s.getStopUserName());
    }
    public boolean addOrUpdateStop(@NonNull String stopID, @Nullable String stopUserName) {
        SQLiteDatabase db = this.getWritableDatabase();
        return addOrUpdateStop(stopID, stopUserName, db);
    }
    private boolean addOrUpdateStop(@NonNull String stopID, @Nullable String stopUserName, SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        long result = -1;

        cv.put("ID", stopID);
        // is there an username?
        if(stopUserName == null) {
            // no: see if it's in the database
            cv.put("username", getStopUserName(db,stopID));
        } else {
            // yes: use it
            cv.put("username", stopUserName);
        }

        try {
            //ignore and throw -1 if the row is already in the DB
            result = db.insertWithOnConflict(TABLE_NAME, null, cv,SQLiteDatabase.CONFLICT_IGNORE);
        } catch (SQLiteException ignored) {
            Log.e(DEBUG_TAG, "cannot insert stop in user db, error: " + ignored);
        }
        if(result!=-1)
            invalidationTracker.notifyInvalidation(TABLE_NAME);
        // Android Studio suggested this unreadable replacement: return true if insert succeeded (!= -1), or try to update and return
        return (result != -1) || (updateStop(stopID,stopUserName, db));
    }
    private boolean addOrUpdateStop(@NonNull Stop s, SQLiteDatabase db) {
        return addOrUpdateStop(s.ID, s.getStopUserName(), db);
    }

    private boolean updateStop(@NonNull String stopID, @Nullable String stopUsername, @NonNull SQLiteDatabase db) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("username", stopUsername);
            db.update(TABLE_NAME, cv, "ID = ?", new String[]{stopID});
            invalidationTracker.notifyInvalidation(TABLE_NAME);

            return true;
        } catch(SQLiteException e) {
            Log.w(DEBUG_TAG, "setStopUsername failed",e);
            return false;
        }
    }
    public boolean updateStop(@NonNull Stop s) {
        SQLiteDatabase db = this.getWritableDatabase();
        return updateStop(s.ID, s.getStopUserName(), db);
    }

    private boolean deleteStop(@NonNull String stopID,@NonNull SQLiteDatabase db) {
        try {
            db.delete(TABLE_NAME, "ID = ?", new String[]{stopID});
            invalidationTracker.notifyInvalidation(TABLE_NAME);
            return true;
        } catch(SQLiteException e) {
            Log.w(DEBUG_TAG, "failed to remove stop, ID: "+stopID);
            return false;
        }
    }
    private boolean deleteStop(@NonNull Stop s, @NonNull SQLiteDatabase db) {
        return deleteStop(s.ID, db);
    }
    public boolean deleteStop(@NonNull String stopID) {
        SQLiteDatabase db = this.getWritableDatabase();
        return deleteStop(stopID, db);
    }
    public boolean deleteStop(@NonNull Stop s) {
        return deleteStop(s.ID);
    }



    public boolean checkStopInFavorites(String stopID, Context con){
        boolean found = false;
        // no stop no party
        if (stopID != null) {
            UserDB userDB = UserDB.getInstance(con);
            found = userDB.isStopInFavorites(stopID);
        }

        return found;
    }

    //extract rows into CSV
    public boolean writeFavoritesToCsv(CsvWriter writer){
        SQLiteDatabase db =  this.getReadableDatabase();

        String sortOrder =
                COL_ID + " DESC";
        Cursor cursor = db.query(TABLE_NAME, FAVORITES_COLUMNS_ARRAY,null,null,null,null, sortOrder);

        final int nCols = 2;//cursor.getColumnCount();
        writer.writeRecord(cursor.getColumnNames());
        while (cursor.moveToNext()){
            String[] arr = {cursor.getString(0), cursor.getString(1)};
            writer.writeRecord(arr);
        }
        cursor.close();
        return true;
    }

    public int insertRowsFromCSV(CsvReader<CsvRecord> reader){
        SQLiteDatabase db = this.getWritableDatabase();

        boolean firstrow = true;
        final HashMap<String,Integer> colIndexByRows = new HashMap<>();

        final CloseableIterator<CsvRecord> rowsIter = reader.iterator();
        if (!rowsIter.hasNext()){
            //nothing to do, it's an empty file
            return -1;
        }
        final CsvRecord firstRow =  rowsIter.next();
        // close if there isn't another rows
        if(!rowsIter.hasNext()) return -2;
        for (int i =0; i<firstRow.getFieldCount(); i++){
            colIndexByRows.put(firstRow.getField(i),i);
        }
        if (!colIndexByRows.containsKey(COL_ID) || !colIndexByRows.containsKey(COL_USERNAME)){
            //Cannot accept the file
            return FILE_INVALID;
        }
        //begin
        db.beginTransaction();
        int updated = 0;
        final int col_id = colIndexByRows.get(COL_ID);
        final int col_username = colIndexByRows.get(COL_USERNAME);
        while (rowsIter.hasNext()){
            final CsvRecord row = rowsIter.next();
            final ContentValues cv = new ContentValues();
            cv.put(COL_ID, row.getField(col_id));
            cv.put(COL_USERNAME, row.getField(col_username));

            long rowid = db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            if (rowid >= 0)
                updated +=1;
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        // These should NOT be closed: the database is a singleton, the connections are recycled.
        //db.close();

       return updated;
    }

    //TODO: Copy method from @AppDataProvider to get all the favorites
}
