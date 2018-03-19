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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.net.URI;

public class AppDataProvider extends ContentProvider {

    public static final String AUTHORITY = "it.reyboz.bustorino.provider";
    private static final int STOP_OP = 1;
    private static final int LINE_OP = 2;
    private static final int BRANCH_OP = 3;
    private static final int FAVORITES_OP =4;
    private static final int MANY_STOPS = 5;
    private static final int ADD_UPDATE_BRANCHES = 6;
    private static final int LINE_INSERT_OP = 7;

    private NextGenDB appDBHelper;
    private SQLiteDatabase db;

    public AppDataProvider() {
    }
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        /*
         * The calls to addURI() go here, for all of the content URI patterns that the provider
         * should recognize.
         */

        sUriMatcher.addURI(AUTHORITY, "stop/#", STOP_OP);
        sUriMatcher.addURI(AUTHORITY,"stops",MANY_STOPS);

        /*
         * Sets the code for a single row to 2. In this case, the "#" wildcard is
         * used. "content://com.example.app.provider/table3/3" matches, but
         * "content://com.example.app.provider/table3 doesn't.
         */
        sUriMatcher.addURI(AUTHORITY, "line/#/", LINE_OP);
        sUriMatcher.addURI(AUTHORITY,"branch/#",BRANCH_OP);
        sUriMatcher.addURI(AUTHORITY,"line/insert",LINE_INSERT_OP);

        sUriMatcher.addURI(AUTHORITY,"updatebranches/",ADD_UPDATE_BRANCHES);
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        // TODO: Implement this to handle requests for the MIME type of the data
        // at the given URI.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        //throw new UnsupportedOperationException("Not yet implemented");
        db = appDBHelper.getWritableDatabase();
        Uri finalUri = null;
        long last_rowid;
        switch (sUriMatcher.match(uri)){
            case ADD_UPDATE_BRANCHES:
                String line_name = values.getAsString(NextGenDB.Contract.LinesTable.COLUMN_NAME);
                if(line_name==null) throw new IllegalArgumentException("No line name given");
                values.remove(NextGenDB.Contract.LinesTable.COLUMN_NAME);
                Cursor c = db.query(NextGenDB.Contract.LinesTable.TABLE_NAME,
                        new String[]{NextGenDB.Contract.LinesTable._ID},NextGenDB.Contract.LinesTable.COLUMN_NAME+"like ?s",
                        new String[]{line_name},null,null,null);
                long lineid = c.getInt(0);
                c.close();
                values.put(NextGenDB.Contract.BranchesTable.COL_LINE,lineid);
                long rowid = db.insertWithOnConflict(NextGenDB.Contract.BranchesTable.TABLE_NAME,null,values,SQLiteDatabase.CONFLICT_REPLACE);
                finalUri= Uri.parse("content://"+AUTHORITY+"/branches/"+rowid);
                break;
            case MANY_STOPS:
                last_rowid = db.insertOrThrow(NextGenDB.Contract.StopsTable.TABLE_NAME,null,values);
                finalUri = ContentUris.withAppendedId(uri,last_rowid);
                break;
            default:
                throw new IllegalArgumentException("Invalid parameters");
        }
        return finalUri;
    }

    @Override
    public boolean onCreate() {
        // TODO: Implement this to initialize your content provider on startup.
        appDBHelper = new NextGenDB(getContext());
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        // TODO: Implement this to handle query requests from clients.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }


}
