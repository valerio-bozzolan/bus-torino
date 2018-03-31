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
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.LinearGradient;
import android.provider.BaseColumns;
import android.util.Log;

import static it.reyboz.bustorino.middleware.NextGenDB.Contract.*;

public class NextGenDB extends SQLiteOpenHelper{
    public static final String DATABASE_NAME = "bustodatabase.db";
    public static final int DATABASE_VERSION = 1;
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


    public NextGenDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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

    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA foreign_keys=ON");

    }

    /**
     * Insert batch content, already prepared as
     * @param content ContentValues array
     * @return number of lines inserted
     */
    public int insertBatchContent(ContentValues[] content,String tableName){
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

        public static final class LinesTable implements BaseColumns{
            //The fields
            public static final String TABLE_NAME = "lines";
            public static final String COLUMN_NAME = "name";
            public static final String COLUMN_DESCRIPTION = "line_description";
            public static final String COLUMN_TYPE = "bacino";
        }
        public static final class BranchesTable implements BaseColumns{
            public static final String TABLE_NAME = "branches";
            public static final String COL_BRANCHID = "branchid";
            public static final String COL_LINE = "lineid";
            public static final String COL_DESCRIPTION = "branch_description";
            public static final String COL_DIRECTION = "direzione";
            public static final String COL_FESTIVO = "festivo";
            public static final String COL_TYPE = "type";
            public static final String COL_LUN="lun";
            public static final String COL_MAR="mar";
            public static final String COL_MER="mer";
            public static final String COL_GIO="gio";
            public static final String COL_VEN="ven";
            public static final String COL_SAB="sab";
            public static final String COL_DOM="dom";

        }
        public static final class ConnectionsTable {
            public static final String TABLE_NAME = "connections";
            public static final String COLUMN_BRANCH = "branch";
            public static final String COLUMN_STOP_ID = "stopid";
            static final String COLUMN_ORDER = "ordine";
        }
        public static final class StopsTable {
            public static final String TABLE_NAME = "stops";
            public static final String COL_ID = "id"; //integer
            public static final String COL_TYPE = "type";
            public static final String COL_NAME = "name";
            public static final String COL_LAT = "lat";
            public static final String COL_LONG = "longitude";
            public static final String COL_LOCATION = "location";
            public static final String COL_PLACE = "placeName";
            public static final String COL_LINES_STOPPING = "lines";

        }
    }
}
