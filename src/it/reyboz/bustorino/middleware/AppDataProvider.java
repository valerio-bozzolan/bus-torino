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
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import it.reyboz.bustorino.middleware.NextGenDB.Contract.*;

import java.util.List;

public class AppDataProvider extends ContentProvider {

    public static final String AUTHORITY = "it.reyboz.bustorino.provider";
    private static final int STOP_OP = 1;
    private static final int LINE_OP = 2;
    private static final int BRANCH_OP = 3;
    private static final int FAVORITES_OP =4;
    private static final int MANY_STOPS = 5;
    private static final int ADD_UPDATE_BRANCHES = 6;
    private static final int LINE_INSERT_OP = 7;
    private static final int CONNECTIONS = 8;
    private static final int LOCATION_SEARCH = 9;

    private static final String DEBUG_TAG="AppDataProvider";

    private NextGenDB appDBHelper;
    private UserDB udbhelper;
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
        sUriMatcher.addURI(AUTHORITY,"stops/location/*/*/*",LOCATION_SEARCH);
        /*
         * Sets the code for a single row to 2. In this case, the "#" wildcard is
         * used. "content://com.example.app.provider/table3/3" matches, but
         * "content://com.example.app.provider/table3 doesn't.
         */
        sUriMatcher.addURI(AUTHORITY, "line/#", LINE_OP);
        sUriMatcher.addURI(AUTHORITY,"branch/#",BRANCH_OP);
        sUriMatcher.addURI(AUTHORITY,"line/insert",LINE_INSERT_OP);

        sUriMatcher.addURI(AUTHORITY,"branches",ADD_UPDATE_BRANCHES);
        sUriMatcher.addURI(AUTHORITY,"connections",CONNECTIONS);
        sUriMatcher.addURI(AUTHORITY,"favorites/#",FAVORITES_OP);
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        db = appDBHelper.getWritableDatabase();
        int rows;
        switch (sUriMatcher.match(uri)){
            case MANY_STOPS:
                rows = db.delete(NextGenDB.Contract.StopsTable.TABLE_NAME,null,null);
                break;
            default:
                throw new UnsupportedOperationException("Not yet implemented");

        }
        return rows;
    }

    @Override
    public String getType(Uri uri) {
        // TODO: Implement this to handle requests for the MIME type of the data
        // at the given URI.
        int match = sUriMatcher.match(uri);
        String baseTypedir = "vnd.android.cursor.dir/";
        String baseTypeitem = "vnd.android.cursor.item/";
        switch (match){
            case LOCATION_SEARCH:
                return baseTypedir+"stop";
            case LINE_OP:
                return baseTypeitem+"line";
            case CONNECTIONS:
                return baseTypedir+"stops";

        }
        return baseTypedir+"/item";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) throws IllegalArgumentException{
        db = appDBHelper.getWritableDatabase();
        Uri finalUri = null;
        long last_rowid = -1;
        switch (sUriMatcher.match(uri)){
            case ADD_UPDATE_BRANCHES:
                Log.d("InsBranchWithProvider","new Insert request");

                String line_name = values.getAsString(NextGenDB.Contract.LinesTable.COLUMN_NAME);
                if(line_name==null) throw new IllegalArgumentException("No line name given");
                long lineid = -1;
                Cursor c = db.query(LinesTable.TABLE_NAME,
                        new String[]{LinesTable._ID,LinesTable.COLUMN_NAME,LinesTable.COLUMN_DESCRIPTION},NextGenDB.Contract.LinesTable.COLUMN_NAME +" =?",
                        new String[]{line_name},null,null,null);
                Log.d("InsBranchWithProvider","finding line in the database: "+c.getCount()+" matches");
                if(c.getCount() == 0){
                    //There are no lines, insert?
                    //NOPE
                    /*
                    c.close();
                    ContentValues cv = new ContentValues();
                    cv.put(LinesTable.COLUMN_NAME,line_name);
                    lineid = db.insert(LinesTable.TABLE_NAME,null,cv);
                    */
                    break;
                }else {
                    c.moveToFirst();
                    /*
                    while(c.moveToNext()){
                        Log.d("InsBranchWithProvider","line: "+c.getString(c.getColumnIndex(LinesTable.COLUMN_NAME))+"\n"
                        +c.getString(c.getColumnIndex(LinesTable.COLUMN_DESCRIPTION)));
                    }*/
                    lineid = c.getInt(c.getColumnIndex(NextGenDB.Contract.LinesTable._ID));
                    c.close();
                }
                values.remove(NextGenDB.Contract.LinesTable.COLUMN_NAME);

                values.put(BranchesTable.COL_LINE,lineid);

                last_rowid = db.insertWithOnConflict(NextGenDB.Contract.BranchesTable.TABLE_NAME,null,values,SQLiteDatabase.CONFLICT_REPLACE);
                break;
            case MANY_STOPS:
                //Log.d("AppDataProvider_busTO","New stop insert request");
                try{
                    last_rowid = db.insertOrThrow(NextGenDB.Contract.StopsTable.TABLE_NAME,null,values);
                } catch (SQLiteConstraintException e){
                    Log.w("AppDataProvider_busTO","Insert failed because of constraint");
                    last_rowid = -1;
                    e.printStackTrace();
                }
                break;
            case CONNECTIONS:
                try{
                    last_rowid = db.insertOrThrow(NextGenDB.Contract.ConnectionsTable.TABLE_NAME,null,values);
                } catch (SQLiteConstraintException e){
                    Log.w("AppDataProvider_busTO","Insert failed because of constraint");
                    last_rowid = -1;
                    e.printStackTrace();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid parameters");
        }
        finalUri = ContentUris.withAppendedId(uri,last_rowid);
        return finalUri;
    }

    @Override
    public boolean onCreate() {
        appDBHelper = new NextGenDB(getContext());
        udbhelper = new UserDB(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) throws UnsupportedOperationException,IllegalArgumentException{
        // TODO: Implement this to handle query requests from clients.
        SQLiteDatabase  db = appDBHelper.getReadableDatabase();
        List<String>  parts = uri.getPathSegments();
        switch (sUriMatcher.match(uri)){
            case LOCATION_SEARCH:
                //authority/stops/location/"Lat"/"Lon"/"distance"
                //distance in metres (integer)
                if(parts.size()>=4 && "location".equals(parts.get(1))){
                    Double latitude = Double.parseDouble(parts.get(2));
                    Double longitude = Double.parseDouble(parts.get(3));
                    //converting distance to a float to not lose precision
                    Float distance = parts.size()>=5 ? Float.parseFloat(parts.get(4))/1000 : 0.1f;
                    if(parts.size()>=5)
                    Log.d("LocationSearch"," given distance to search is "+parts.get(4)+" m");
                    Double distasAngle = (distance/6371)*180/Math.PI; //small angles approximation, still valid for about 500 metres

                    String whereClause = StopsTable.COL_LAT+ "< "+(latitude+distasAngle)+" AND "
                            +StopsTable.COL_LAT +" > "+(latitude-distasAngle)+" AND "+
                            StopsTable.COL_LONG+" < "+(longitude+distasAngle)+" AND "+StopsTable.COL_LONG+" > "+(longitude-distasAngle);
                    Log.d("Provider-LOCSearch","Querying stops  by position, query args: \n"+whereClause);
                    return db.query(StopsTable.TABLE_NAME,projection,whereClause,null,null,null,null);
                    //return getStopsNearby(latitude,longitude,distance);
                }
                else {
                    Log.w(DEBUG_TAG,"Not enough parameters");
                    if(parts.size()>=5) for(String s:parts) Log.d(DEBUG_TAG,"\t element "+parts.indexOf(s)+" is: "+s);
                    return null;
                }

            case FAVORITES_OP:
                final String stopFavSelection = UserDB.getFavoritesColumnNamesAsArray[0]+" = ?";
                db = udbhelper.getReadableDatabase();
                Log.d(DEBUG_TAG,"Asked information on Favorites about stop with id "+uri.getLastPathSegment());
                return db.query(UserDB.TABLE_NAME,projection,stopFavSelection,new String[]{uri.getLastPathSegment()},null,null,sortOrder);
            case STOP_OP:
                //Let's try this plain and simple
                final String[] selectionValues = {uri.getLastPathSegment()};
                final String stopSelection = StopsTable.COL_ID+" = ?";
                Log.d(DEBUG_TAG,"Asked information about stop with id "+selectionValues[0]);
                return db.query(StopsTable.TABLE_NAME,projection,stopSelection,selectionValues,null,null,sortOrder);
            default:
                Log.d("DataProvider","got request "+uri.getPath()+" which doesn't match anything");
            }

        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

   // public static Uri getBaseUriGivenOp(int operationType);
    public static Uri.Builder getAlmostFinishedBuilder(){
        final Uri.Builder b = new Uri.Builder();
        b.scheme("content").authority(AUTHORITY);
        return b;
    }

}
