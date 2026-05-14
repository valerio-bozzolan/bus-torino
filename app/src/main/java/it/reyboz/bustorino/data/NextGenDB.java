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
package it.reyboz.bustorino.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.*;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Result;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;

import java.util.*;
import java.util.stream.Collectors;

import static it.reyboz.bustorino.data.NextGenDB.Contract.*;

public class NextGenDB extends SQLiteOpenHelper{
    public static final String DATABASE_NAME = "bustodatabase.db";
    public static final int DATABASE_VERSION = 3;
    public static final String DEBUG_TAG = "NextGenDB-BusTO";
    //NO Singleton instance
    //private static volatile NextGenDB instance = null;
    //Some generating Strings
    private static final String SQL_CREATE_LINES_TABLE="CREATE TABLE "+Contract.LinesTable.TABLE_NAME+" ("+
            Contract.LinesTable._ID +" INTEGER PRIMARY KEY AUTOINCREMENT, "+ Contract.LinesTable.COLUMN_NAME +" TEXT, "+
            Contract.LinesTable.COLUMN_DESCRIPTION +" TEXT, "+Contract.LinesTable.COLUMN_TYPE +" TEXT, "+
            "UNIQUE ("+LinesTable.COLUMN_NAME+","+LinesTable.COLUMN_DESCRIPTION+","+LinesTable.COLUMN_TYPE+" ) "+" )";

    private static final String SQL_CREATE_BRANCH_TABLE="CREATE TABLE "+Contract.BranchesTable.TABLE_NAME+" ("+
            Contract.BranchesTable._ID +" INTEGER, "+ Contract.BranchesTable.COL_BRANCHID +" INTEGER PRIMARY KEY, "+
            Contract.BranchesTable.COL_LINE +" INTEGER, "+ Contract.BranchesTable.COL_DESCRIPTION +" TEXT, "+
            Contract.BranchesTable.COL_DIRECTION+" TEXT, "+ Contract.BranchesTable.COL_TYPE +" INTEGER, "+
            //SERVICE DAYS: 0 => FERIALE,1=>FESTIVO,-1=>UNKNOWN,add others if necessary
            Contract.BranchesTable.COL_FESTIVO +" INTEGER, "+
            //DAYS COLUMNS. IT'S SO TEDIOUS I TRIED TO KILL MYSELF
            BranchesTable.COL_LUN+" INTEGER, "+BranchesTable.COL_MAR+" INTEGER, "+BranchesTable.COL_MER+" INTEGER, "+BranchesTable.COL_GIO+" INTEGER, "+
            BranchesTable.COL_VEN+" INTEGER, "+ BranchesTable.COL_SAB+" INTEGER, "+BranchesTable.COL_DOM+" INTEGER, "+
            "FOREIGN KEY("+ Contract.BranchesTable.COL_LINE +") references "+ Contract.LinesTable.TABLE_NAME+"("+ Contract.LinesTable._ID+") "
            +")";
    private static final String SQL_CREATE_CONNECTIONS_TABLE="CREATE TABLE "+Contract.ConnectionsTable.TABLE_NAME+" ("+
            Contract.ConnectionsTable.COLUMN_BRANCH+" INTEGER, "+ Contract.ConnectionsTable.COLUMN_STOP_ID+" TEXT, "+
            Contract.ConnectionsTable.COLUMN_ORDER+" INTEGER, "+
            "PRIMARY KEY ("+ Contract.ConnectionsTable.COLUMN_BRANCH+","+ Contract.ConnectionsTable.COLUMN_STOP_ID + "), "+
            "FOREIGN KEY("+ Contract.ConnectionsTable.COLUMN_BRANCH+") references "+ Contract.BranchesTable.TABLE_NAME+"("+ Contract.BranchesTable.COL_BRANCHID +"), "+
            "FOREIGN KEY("+ Contract.ConnectionsTable.COLUMN_STOP_ID+") references "+ Contract.StopsTable.TABLE_NAME+"("+ Contract.StopsTable.COL_ID +") "
            +")";
    private static final String SQL_CREATE_STOPS_TABLE="CREATE TABLE "+Contract.StopsTable.TABLE_NAME+" ("+
            Contract.StopsTable.COL_ID+" TEXT PRIMARY KEY, "+ Contract.StopsTable.COL_TYPE+" INTEGER, "+Contract.StopsTable.COL_LAT+" REAL NOT NULL, "+
            Contract.StopsTable.COL_LONG+" REAL NOT NULL, "+ Contract.StopsTable.COL_NAME+" TEXT NOT NULL, "+
            StopsTable.COL_GTFS_ID+" TEXT, "+
            Contract.StopsTable.COL_LOCATION+" TEXT, "+Contract.StopsTable.COL_PLACE+" TEXT, "+
            Contract.StopsTable.COL_LINES_STOPPING +" TEXT )";

    private static final String SQL_CREATE_STOPS_TABLE_TO_COMPLETE = " ("+
            Contract.StopsTable.COL_ID+" TEXT PRIMARY KEY, "+ Contract.StopsTable.COL_TYPE+" INTEGER, "+Contract.StopsTable.COL_LAT+" REAL NOT NULL, "+
            Contract.StopsTable.COL_LONG+" REAL NOT NULL, "+ Contract.StopsTable.COL_NAME+" TEXT NOT NULL, "+
            Contract.StopsTable.COL_LOCATION+" TEXT, "+Contract.StopsTable.COL_PLACE+" TEXT, "+
            Contract.StopsTable.COL_LINES_STOPPING +" TEXT )";

    public static final String[] QUERY_COLUMN_stops_all = {
            StopsTable.COL_ID, StopsTable.COL_NAME, StopsTable.COL_GTFS_ID, StopsTable.COL_LOCATION,
            StopsTable.COL_TYPE, StopsTable.COL_LAT, StopsTable.COL_LONG, StopsTable.COL_LINES_STOPPING};

    public static final String QUERY_WHERE_LAT_AND_LNG_IN_RANGE = StopsTable.COL_LAT + " >= ? AND " +
            StopsTable.COL_LAT + " <= ? AND "+ StopsTable.COL_LONG +
            " >= ? AND "+ StopsTable.COL_LONG + " <= ?";

    public static final String QUERY_FROM_GTFS_ID_IN_TO_COMPLETE= StopsTable.COL_GTFS_ID +" IN ";

    public static String QUERY_WHERE_ID = StopsTable.COL_ID+" = ?";
    public static final int RESULT_SQLITE_ERROR = -4;

    private final Context appContext;
    private final InvalidationTracker invalidationTracker = new InvalidationTracker();
    private static NextGenDB INSTANCE;

    private NextGenDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
    }
    public static NextGenDB getInstance(Context context) {
        if (INSTANCE == null){
            INSTANCE = new NextGenDB(context.getApplicationContext());
        }
        return INSTANCE;
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("BusTO-AppDB","Lines creating database:\n"+SQL_CREATE_LINES_TABLE+"\n"+
        SQL_CREATE_STOPS_TABLE+"\n"+SQL_CREATE_BRANCH_TABLE+"\n"+SQL_CREATE_CONNECTIONS_TABLE);
        db.execSQL(SQL_CREATE_LINES_TABLE);

        db.execSQL(SQL_CREATE_STOPS_TABLE);
        //tables with constraints
        db.execSQL(SQL_CREATE_BRANCH_TABLE);
        db.execSQL(SQL_CREATE_CONNECTIONS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if(oldVersion<2 && newVersion == 2){
            //DROP ALL TABLES
            db.execSQL("DROP TABLE "+ConnectionsTable.TABLE_NAME);
            db.execSQL("DROP TABLE "+BranchesTable.TABLE_NAME);
            db.execSQL("DROP TABLE "+LinesTable.TABLE_NAME);
            db.execSQL("DROP TABLE "+ StopsTable.TABLE_NAME);
            //RECREATE THE TABLES WITH THE NEW SCHEMA
            db.execSQL(SQL_CREATE_LINES_TABLE);
            db.execSQL(SQL_CREATE_STOPS_TABLE);
            //tables with constraints
            db.execSQL(SQL_CREATE_BRANCH_TABLE);
            db.execSQL(SQL_CREATE_CONNECTIONS_TABLE);

            //DatabaseUpdate.requestDBUpdateWithWork(appContext, true, true);
            DBUpdateWorker.requestDBUpdateUniqueWork(appContext, true);
        }
        if(oldVersion < 3 && newVersion == 3){
            Log.d("BusTO-Database", "Running upgrades for version 3");
            //add the new column
            db.execSQL("ALTER TABLE "+StopsTable.TABLE_NAME+
                    " ADD COLUMN "+StopsTable.COL_GTFS_ID+" TEXT ");

            //  DatabaseUpdate.requestDBUpdateWithWork(appContext, true);
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA foreign_keys=ON");

    }

    public static String getSqlCreateStopsTable(String tableName){

        return "CREATE TABLE "+tableName+" ("+
                Contract.StopsTable.COL_ID+" TEXT PRIMARY KEY, "+ Contract.StopsTable.COL_TYPE+" INTEGER, "+Contract.StopsTable.COL_LAT+" REAL NOT NULL, "+
                Contract.StopsTable.COL_LONG+" REAL NOT NULL, "+ Contract.StopsTable.COL_NAME+" TEXT NOT NULL, "+
                Contract.StopsTable.COL_LOCATION+" TEXT, "+Contract.StopsTable.COL_PLACE+" TEXT, "+
                Contract.StopsTable.COL_LINES_STOPPING +" TEXT )";
    }

    /**
     * Query some bus stops inside a map view
     * @return stoplist, if empty it means that an error occurred
     *
     * You can obtain the coordinates from OSMDroid using something like this:
     *  BoundingBoxE6 bb = mMapView.getBoundingBox();
     *  double latFrom = bb.getLatSouthE6() / 1E6;
     *  double latTo = bb.getLatNorthE6() / 1E6;
     *  double lngFrom = bb.getLonWestE6() / 1E6;
     *  double lngTo = bb.getLonEastE6() / 1E6;
     */
    @NonNull
    public synchronized Result<ArrayList<Stop>> queryAllInsideMapView(double minLat, double maxLat, double minLng, double maxLng) {
        ArrayList<Stop> stops = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // coordinates must be strings in the where condition
        String minLatRaw = String.valueOf(minLat);
        String maxLatRaw = String.valueOf(maxLat);
        String minLngRaw = String.valueOf(minLng);
        String maxLngRaw = String.valueOf(maxLng);

        if(db == null) {
            return Result.failure(new SQLiteException("Database is null"));
        }

        try (final Cursor result = db.query(StopsTable.TABLE_NAME, QUERY_COLUMN_stops_all, QUERY_WHERE_LAT_AND_LNG_IN_RANGE,
                new String[] {minLatRaw, maxLatRaw, minLngRaw, maxLngRaw},
                null, null, null)) {
            stops = getStopsFromCursorAllFields(result);
        } catch(SQLiteException e) {
            Log.e(DEBUG_TAG, "SQLiteException occurred");
            return Result.failure(e);
        }catch (Exception e){
            Log.e(DEBUG_TAG, "Exception occurred when getting stops");
            return Result.failure(e);
        }

        return Result.success(stops);
    }

    @NonNull
    public QueryLiveData<ArrayList<Stop>> queryAllInsideMapViewLiveData(double minLat, double maxLat, double minLng, double maxLng) {
        return  new QueryLiveData<>(List.of(StopsTable.TABLE_NAME), invalidationTracker,()->{
            Result<ArrayList<Stop>> queryResult = queryAllInsideMapView(minLat, maxLat, minLng, maxLng);
            if(queryResult.isSuccess()){
                return queryResult.result;
            } else{
                Log.w(DEBUG_TAG, "queryAllInsideMapViewLiveData failed", queryResult.exception);
                return new ArrayList<>();
            }
        });
    }



    /**
     * Query stops in the database having these IDs
     * @param bustoDB readable database instance
     * @param gtfsIDs gtfs IDs to query
     * @return list of stops
     */
    @NonNull
    public static synchronized Result<ArrayList<Stop>> queryAllStopsWithGtfsIDs(@NonNull SQLiteDatabase bustoDB,@NonNull List<String> gtfsIDs){
        final ArrayList<Stop> stops = new ArrayList<>();

        if (gtfsIDs.isEmpty()) {
            return Result.success(stops);
        }

        final StringBuilder builder = new StringBuilder(QUERY_FROM_GTFS_ID_IN_TO_COMPLETE);
        boolean first = true;
        builder.append(" ( ");
        for(int i=0; i< gtfsIDs.size(); i++){
            if(first){
                first = false;
            } else{
                builder.append(", ");
            }
            builder.append("?");//.append("\"").append(id).append("\"");
        }
        builder.append(") ");
        final String whereClause = builder.toString();

        final String[] idsQuery = gtfsIDs.toArray(new String[0]);

        try(Cursor result = bustoDB.query(StopsTable.TABLE_NAME,QUERY_COLUMN_stops_all, whereClause,
                idsQuery,
                null, null, null)) {
            stops.addAll(getStopsFromCursorAllFields(result));
        } catch(SQLiteException e) {
            Log.w(DEBUG_TAG, "SQLiteException occurred in getting stops");
            return Result.failure(e);
        }
        return Result.success(stops);
    }

    public static String buildWhereClause(String colName, List<String> args){
        final StringBuilder builder = new StringBuilder().append(colName).append(" IN ");
        boolean first = true;
        builder.append(" ( ");
        for(int i=0; i< args.size(); i++){
            if(first){
                first = false;
            } else{
                builder.append(", ");
            }
            builder.append("?");
        }
        builder.append(") ");
        return builder.toString();
    }

    /**
     * Query stops in the database having these IDs
     * @param bustoDB readable database instance
     * @param stopIds to query
     * @return list of stops
     */
    @NonNull
    public static synchronized Result<ArrayList<Stop>> queryStopsWithStopIds(@NonNull SQLiteDatabase bustoDB,@NonNull List<String> stopIds){
        ArrayList<Stop> stops = null;

        if (stopIds.isEmpty()) {
            return Result.success(stops);
        }

        final StringBuilder builder = new StringBuilder().append(StopsTable.COL_ID).append(" IN ");
        boolean first = true;
        builder.append(" ( ");
        for(int i=0; i< stopIds.size(); i++){
            if(first){
                first = false;
            } else{
                builder.append(", ");
            }
            builder.append("?");//.append("\"").append(id).append("\"");
        }
        builder.append(") ");
        final String whereClause = builder.toString();
        Log.d(DEBUG_TAG, "Asking for all stops with IDs, query: "+whereClause);

        final String[] idsQuery = stopIds.toArray(new String[0]);

        try(Cursor result = bustoDB.query(StopsTable.TABLE_NAME,QUERY_COLUMN_stops_all, whereClause,
                idsQuery,
                null, null, null)) {
            stops = getStopsFromCursorAllFields(result);
        } catch(SQLiteException e) {
            Log.e(DEBUG_TAG, "SQLiteException occurred");
            return Result.failure(e);
        }
        return Result.success(stops);
    }

    @NonNull
    public QueryLiveData<ArrayList<Stop>> queryStopsWithStopIdsLiveData(@NonNull  List<String> stopIds){
        return new QueryLiveData<>(List.of(StopsTable.TABLE_NAME), invalidationTracker, ()->{
            Log.d(DEBUG_TAG, "Table stops changed, redoing query");
            SQLiteDatabase db = this.getReadableDatabase();
            Result<ArrayList<Stop>> result = queryStopsWithStopIds(db, stopIds);
            if(result.isSuccess()){
                return result.result;
            } else{
                return null;
            }
        });
    }


    /**
     * Get the list of stop in the query, with all the possible fields {NextGenDB.QUERY_COLUMN_stops_all}
     * @param result cursor from query
     * @return an Array of the stops found in the query
     */
    public static ArrayList<Stop> getStopsFromCursorAllFields(Cursor result){
        final int colID = result.getColumnIndex(StopsTable.COL_ID);
        final int colName = result.getColumnIndex(StopsTable.COL_NAME);
        final int colLocation = result.getColumnIndex(StopsTable.COL_LOCATION);
        final int colType = result.getColumnIndex(StopsTable.COL_TYPE);
        final int colLat = result.getColumnIndex(StopsTable.COL_LAT);
        final int colGtfsID = result.getColumnIndex(StopsTable.COL_GTFS_ID);
        final int colLon = result.getColumnIndex(StopsTable.COL_LONG);
        final int colLines = result.getColumnIndex(StopsTable.COL_LINES_STOPPING);

        int count = result.getCount();
        ArrayList<Stop> stops = new ArrayList<>(count);

        int i = 0;
        while(result.moveToNext()) {

            final String stopID = result.getString(colID).trim();
            Route.Type type;
            //if(result.getString(colType) == null) type = Route.Type.BUS;
            //else type = Route.getTypeFromSymbol(result.getString(colType));
            //if(result.getInt(colType) == null) type = Route.Type.BUS;
            try{
                type =  Route.Type.fromCode(result.getInt(colType));
            } catch (Exception e){
                type = Route.Type.BUS;
            }
            String lines = result.getString(colLines).trim();

            String locationSometimesEmpty = result.getString(colLocation);
            if (locationSometimesEmpty!= null && locationSometimesEmpty.length() <= 0) {
                locationSometimesEmpty = null;
            }

            stops.add(new Stop(stopID, result.getString(colName), null,
                    locationSometimesEmpty, type, splitLinesString(lines),
                    result.getDouble(colLat), result.getDouble(colLon),
                    result.getString(colGtfsID))
            );
        }
        return stops;
    }

    public static boolean insertBranchesIntoDB(@NonNull Context context, @NonNull List<Route> routesToInsert){
        final NextGenDB nextGenDB = NextGenDB.getInstance(context);
        //ContentValues[] values = new ContentValues[routesToInsert.size()];
        ArrayList<ContentValues> branchesValues = new ArrayList<>(routesToInsert.size());
        ArrayList<ContentValues> connectionsVals = new ArrayList<>(routesToInsert.size());
        long starttime,endtime;
        for (Route r:routesToInsert){
            //if it has received an interrupt, stop
            if(Thread.interrupted()) return false;
            //otherwise, build contentValues
            final ContentValues cv = createContentValuesBranch(r);
            branchesValues.add(cv);
            if(r.getStopsList() != null)
                for(int i=0; i<r.getStopsList().size();i++){
                    String stop = r.getStopsList().get(i);
                    final ContentValues connVal = new ContentValues();
                    connVal.put(ConnectionsTable.COLUMN_STOP_ID,stop);
                    connVal.put(ConnectionsTable.COLUMN_ORDER,i);
                    connVal.put(ConnectionsTable.COLUMN_BRANCH,r.branchid);

                    //add to global connVals
                    connectionsVals.add(connVal);
                }
        }
        //ContentResolver cr = context.getContentResolver();

        starttime = System.currentTimeMillis();
        //cr.bulkInsert(Uri.parse("content://" + AppDataProvider.AUTHORITY + "/branches/"), branchesValues.toArray(new ContentValues[0]));
        int rows = nextGenDB.insertBatchContent(branchesValues.toArray(new ContentValues[0]), BranchesTable.TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE);
        endtime = System.currentTimeMillis();
        Log.d("DataDownload", "Inserted branches, took " + (endtime - starttime) + " ms, inserted "+ rows + " rows");
        if(rows == RESULT_SQLITE_ERROR){
            return false;
        }


        if (!connectionsVals.isEmpty()) {
            starttime = System.currentTimeMillis();
            ContentValues[] valArr = connectionsVals.toArray(new ContentValues[0]);
            Log.d("DataDownloadInsert", "inserting " + valArr.length + " connections");
            rows = nextGenDB.insertBatchContent(valArr, ConnectionsTable.TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE);
            endtime = System.currentTimeMillis();
            Log.d("DataDownload", "Inserted connections found, took " + (endtime - starttime) + " ms, inserted " + rows + " rows");
            if(rows == RESULT_SQLITE_ERROR){
                return false;
            }
        }
        //nextGenDB.close();
        return true;
    }

    public boolean updateDataStops(List<Palina> palinas, HashMap<String, Set<String>> routesStoppingByStop){
        SQLiteDatabase db = getWritableDatabase();
        boolean completed = false;
        if(!db.isOpen()){
            //catch errors like: java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase
            //we have to abort the work and restart it
            return completed;
        }
        int patternsStopsHits = 0;
        long startTime = System.currentTimeMillis();

        try {
            //TODO: Get the type of stop from the lines
            //Empty the needed tables

            db.beginTransaction();
            //put new data

            Log.d(DEBUG_TAG, "Inserting " + palinas.size() + " stops");
            String routesStoppingString = "";

            for (final Palina p : palinas) {
                final ContentValues cv = new ContentValues();

                cv.put(StopsTable.COL_ID, p.ID);
                cv.put(StopsTable.COL_NAME, p.getStopDefaultName());
                if (p.location != null)
                    cv.put(StopsTable.COL_LOCATION, p.location);
                cv.put(StopsTable.COL_LAT, p.getLatitude());
                cv.put(StopsTable.COL_LONG, p.getLongitude());
                if (p.getAbsurdGTTPlaceName() != null)
                    cv.put(StopsTable.COL_PLACE, p.getAbsurdGTTPlaceName());
                if (p.gtfsID != null && routesStoppingByStop.containsKey(p.gtfsID)) {
                    final ArrayList<String> routesSs = new ArrayList<>(routesStoppingByStop.get(p.gtfsID));
                    routesStoppingString = Palina.buildRoutesStringFromNames(routesSs);
                    patternsStopsHits++;
                } else {
                    routesStoppingString = p.routesThatStopHereToString();
                }
                cv.put(StopsTable.COL_LINES_STOPPING, routesStoppingString);
                if (p.type != null) cv.put(StopsTable.COL_TYPE, p.type.getCode());
                if (p.gtfsID != null) cv.put(StopsTable.COL_GTFS_ID, p.gtfsID);
                //Log.d(DEBUG_TAG,cv.toString());
                //cpOp.add(ContentProviderOperation.newInsert(uritobeused).withValues(cv).build());
                //valuesArr[i] = cv;
                db.replace(StopsTable.TABLE_NAME, null, cv);

            }
            db.setTransactionSuccessful();
            completed = true;
        }catch (SQLException exc){
            Log.w(DEBUG_TAG, "SQlite Exception: " + exc.getMessage());
        } finally {
            db.endTransaction();
        }

        long endTime = System.currentTimeMillis();
        Log.d(DEBUG_TAG, "Inserting stops took: " + ((double) (endTime - startTime) / 1000) + " s, successful: "+completed);
        Log.d(DEBUG_TAG, "\t"+patternsStopsHits+" routes string were built from the patterns");
        if(completed){
            invalidationTracker.notifyInvalidation(StopsTable.TABLE_NAME);
        }
        return completed;
    }

    @NonNull
    private static ContentValues createContentValuesBranch(Route r) {
        final ContentValues cv = new ContentValues();
        cv.put(BranchesTable.COL_BRANCHID, r.branchid);
        cv.put(LinesTable.COLUMN_NAME, r.getName());
        cv.put(BranchesTable.COL_DIRECTION, r.destinazione);
        cv.put(BranchesTable.COL_DESCRIPTION, r.description);
        for (int day : r.serviceDays) {
            switch (day){
                case Calendar.MONDAY:
                    cv.put(BranchesTable.COL_LUN,1);
                    break;
                case Calendar.TUESDAY:
                    cv.put(BranchesTable.COL_MAR,1);
                    break;
                case Calendar.WEDNESDAY:
                    cv.put(BranchesTable.COL_MER,1);
                    break;
                case Calendar.THURSDAY:
                    cv.put(BranchesTable.COL_GIO,1);
                    break;
                case Calendar.FRIDAY:
                    cv.put(BranchesTable.COL_VEN,1);
                    break;
                case Calendar.SATURDAY:
                    cv.put(BranchesTable.COL_SAB,1);
                    break;
                case Calendar.SUNDAY:
                    cv.put(BranchesTable.COL_DOM,1);
                    break;
            }
        }
        if(r.type!=null) cv.put(BranchesTable.COL_TYPE, r.type.getCode());
        cv.put(BranchesTable.COL_FESTIVO, r.festivo.getCode());
        return cv;
    }
    /*
    static ArrayList<Stop> createStopListFromCursor(Cursor data){
        ArrayList<Stop> stopList = new ArrayList<>();
        final int col_id = data.getColumnIndex(StopsTable.COL_ID);
        final int latInd = data.getColumnIndex(StopsTable.COL_LAT);
        final int lonInd = data.getColumnIndex(StopsTable.COL_LONG);
        final int nameindex = data.getColumnIndex(StopsTable.COL_NAME);
        final int typeIndex = data.getColumnIndex(StopsTable.COL_TYPE);
        final int linesIndex = data.getColumnIndex(StopsTable.COL_LINES_STOPPING);

        data.moveToFirst();
        for(int i=0; i<data.getCount();i++){
            String[] routes = data.getString(linesIndex).split(",");

            stopList.add(new Stop(data.getString(col_id),data.getString(nameindex),null,null,
                            Route.Type.fromCode(data.getInt(typeIndex)),
                            Arrays.asList(routes), //the routes should be compact, not normalized yet
                            data.getDouble(latInd),data.getDouble(lonInd)
                    )
            );
            //Log.d("NearbyStopsFragment","Got stop with id "+data.getString(col_id)+
            //" and name "+data.getString(nameindex));
            data.moveToNext();
        }
        return stopList;
    }
     */

    /**
     * Insert batch content, already prepared as
     * @param content ContentValues array
     * @return number of lines inserted
     */
    public int insertBatchContent(ContentValues[] content,String tableName, int sqliteConflictStrategy) {

        final SQLiteDatabase db = this.getWritableDatabase();
        int success = 0;
        try{
            db.beginTransaction();
            for(ContentValues cv:content){
                db.insertWithOnConflict(tableName, null, cv, sqliteConflictStrategy);
            }
            db.setTransactionSuccessful();
            success = content.length;
        } catch (SQLException e) {
            Log.w(DEBUG_TAG, "Error inserting batch content into table "+tableName, e);
            success = RESULT_SQLITE_ERROR;
        } finally {
            db.endTransaction();
        }
        if (success > 0) {
            invalidationTracker.notifyInvalidation(tableName); // ✅ Notify only after confirmed commit
        }
        return success;
    }

    public static List<String> splitLinesString(String linesStr){
        return Arrays.asList(linesStr.split("\\s*,\\s*"));
    }

    public static final class Contract{
        //Ok, I get it, it really is a pain in the ass..
        // But it's the only way to have maintainable code
        public interface DataTables {
            String getTableName();
            String[] getFields();
        }

        public static final class LinesTable implements BaseColumns, DataTables {
            //The fields
            public static final String TABLE_NAME = "lines";
            public static final String COLUMN_NAME = "line_name";
            public static final String COLUMN_DESCRIPTION = "line_description";
            public static final String COLUMN_TYPE = "line_bacino";

            @Override
            public String getTableName() {
                return TABLE_NAME;
            }

            @Override
            public String[] getFields() {
                return new String[]{COLUMN_NAME,COLUMN_DESCRIPTION,COLUMN_TYPE};
            }
        }
        public static final class BranchesTable implements BaseColumns, DataTables {
            public static final String TABLE_NAME = "branches";
            public static final String COL_BRANCHID = "branchid";
            public static final String COL_LINE = "lineid";
            public static final String COL_DESCRIPTION = "branch_description";
            public static final String COL_DIRECTION = "branch_direzione";
            public static final String COL_FESTIVO = "branch_festivo";
            public static final String COL_TYPE = "branch_type";
            public static final String COL_LUN="runs_lun";
            public static final String COL_MAR="runs_mar";
            public static final String COL_MER="runs_mer";
            public static final String COL_GIO="runs_gio";
            public static final String COL_VEN="runs_ven";
            public static final String COL_SAB="runs_sab";
            public static final String COL_DOM="runs_dom";

            @Override
            public String getTableName() {
                return TABLE_NAME;
            }
            @Override
            public String[] getFields() {
                return new String[]{COL_BRANCHID,COL_LINE,COL_DESCRIPTION,
                        COL_DIRECTION,COL_FESTIVO,COL_TYPE,
                        COL_LUN,COL_MAR,COL_MER,COL_GIO,COL_VEN,COL_SAB,COL_DOM
                };
            }

        }
        public static final class ConnectionsTable implements DataTables {
            public static final String TABLE_NAME = "connections";
            public static final String COLUMN_BRANCH = "branchid";
            public static final String COLUMN_STOP_ID = "stopid";
            public static final String COLUMN_ORDER = "ordine";

            @Override
            public String getTableName() {
                return TABLE_NAME;
            }
            @Override
            public String[] getFields() {
                return new String[]{COLUMN_STOP_ID,COLUMN_BRANCH,COLUMN_ORDER};
            }
        }
        public static final class StopsTable implements DataTables {
            public static final String TABLE_NAME = "stops";
            public static final String COL_ID = "stopid"; //integer
            public static final String COL_TYPE = "stop_type";
            public static final String COL_NAME = "stop_name";
            public static final String COL_GTFS_ID = "gtfs_id";
            public static final String COL_LAT = "stop_latitude";
            public static final String COL_LONG = "stop_longitude";
            public static final String COL_LOCATION = "stop_location";
            public static final String COL_PLACE = "stop_placeName";
            public static final String COL_LINES_STOPPING = "stop_lines";


            @Override
            public String getTableName() {
                return TABLE_NAME;
            }
            @Override
            public String[] getFields() {
                return new String[]{COL_ID,COL_TYPE,COL_NAME,COL_GTFS_ID,COL_LAT,COL_LONG,COL_LOCATION,COL_PLACE,COL_LINES_STOPPING};
            }
        }
    }

    public static final class DBUpdatingException extends Exception{
        public DBUpdatingException(String message) {
            super(message);
        }
    }
}
