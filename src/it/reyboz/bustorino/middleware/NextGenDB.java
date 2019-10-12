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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;
import it.reyboz.bustorino.R;

import static it.reyboz.bustorino.middleware.NextGenDB.Contract.*;

public class NextGenDB extends SQLiteOpenHelper{
    public static final String DATABASE_NAME = "bustodatabase.db";
    public static final int DATABASE_VERSION = 2;
    //Singleton instance
    private static volatile NextGenDB instance = null;
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
            Contract.StopsTable.COL_LOCATION+" TEXT, "+Contract.StopsTable.COL_PLACE+" TEXT, "+
            Contract.StopsTable.COL_LINES_STOPPING +" TEXT )";

    private static final String SQL_CREATE_STOPS_TABLE_TO_COMPLETE = " ("+
            Contract.StopsTable.COL_ID+" TEXT PRIMARY KEY, "+ Contract.StopsTable.COL_TYPE+" INTEGER, "+Contract.StopsTable.COL_LAT+" REAL NOT NULL, "+
            Contract.StopsTable.COL_LONG+" REAL NOT NULL, "+ Contract.StopsTable.COL_NAME+" TEXT NOT NULL, "+
            Contract.StopsTable.COL_LOCATION+" TEXT, "+Contract.StopsTable.COL_PLACE+" TEXT, "+
            Contract.StopsTable.COL_LINES_STOPPING +" TEXT )";

    private Context appContext;

    private NextGenDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
    }

    /**
     * Lazy initialization singleton getter, thread-safe with double checked locking
     * from https://en.wikipedia.org/wiki/Singleton_pattern
     * @param context needed context
     * @return the instance
     */
    public static NextGenDB getInstance(Context context){
        if(instance==null){
            synchronized (NextGenDB.class){
                if(instance==null){
                    instance = new NextGenDB(context);
                }
            }
        }
        return instance;
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

            DatabaseUpdateService.startDBUpdate(appContext,0,true);
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
     * Insert batch content, already prepared as
     * @param content ContentValues array
     * @return number of lines inserted
     */
    public int insertBatchContent(ContentValues[] content,String tableName) throws SQLiteException {

        final SQLiteDatabase db = this.getWritableDatabase();
        int success = 0;

        db.beginTransaction();

        for (final ContentValues cv : content) {
            try {
                db.replaceOrThrow(tableName, null, cv);
                success++;
            } catch (SQLiteConstraintException d){
                Log.w("NextGenDB_Insert","Failed insert with FOREIGN KEY... \n"+d.getMessage());

            } catch (Exception e) {
                Log.w("NextGenDB_Insert", e);
            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        return success;
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
                return new String[]{COL_ID,COL_TYPE,COL_NAME,COL_LAT,COL_LONG,COL_LOCATION,COL_PLACE,COL_LINES_STOPPING};
            }
        }
    }

    public static final class DBUpdatingException extends Exception{
        public DBUpdatingException(String message) {
            super(message);
        }
    }

}
