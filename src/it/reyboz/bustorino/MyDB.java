/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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
package it.reyboz.bustorino;

import android.provider.BaseColumns;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.content.Context;

/**
 * Manage SQLite
 * 
 * @author boz
 */
public class MyDB extends SQLiteOpenHelper {

	// If you change the database schema, you must increment the database
	// version.
	public static final int DATABASE_VERSION = 7;
	public static final String DATABASE_NAME = "bustorino.db";

	public static String COMMA = ", ";
	public static String DOT = ".";
	public static final String EQUALS = " = ";
	public static final String CREATE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS ";
	public static final String DROP_TABLE = "DROP TABLE IF EXISTS ";
	public static final String TEXT_TYPE = " TEXT";
	public static final String INTEGER_TYPE = " INTEGER";
	public static final String PRIMARY_KEY = " PRIMARY KEY";
	public static final String NOT_NULL = " NOT NULL";
	public static final String UPDATE = "UPDATE ";
	public static final String WHERE = " WHERE ";
	public static final String AND = " AND ";
	public static final String SELECT = "SELECT ";
	public static final String FROM = " FROM ";
	public static final String SELECT_ALL_FROM = "SELECT * FROM ";
	public static final String ORDER_BY = " ORDER BY ";
	public static final String ASC = " ASC";
	public static final String DESC = " DESC";

	public static String getDefault(int i) {
		return " DEFAULT " + String.valueOf(i);
	}

	public static String somethingEqualsString(String tableName) {
		return " " + tableName + "=\"?\"";
	}

	public static String somethingEqualsInt(String tableName) {
		return " " + tableName + "=?";
	}

	public MyDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(BusStop.SQL_CREATE_ENTRIES);
		db.execSQL(BusLine.SQL_CREATE_ENTRIES);
		db.execSQL(BusStopServeLine.SQL_CREATE_ENTRIES);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 6 && newVersion == 7) {
			// Added two tables, its all OK
			oldVersion = 7;
		} else {
			// This probably is never done, but it discard the data and start
			// over strange DB versions
			db.execSQL(BusStop.SQL_DELETE_ENTRIES);
			db.execSQL(BusLine.SQL_DELETE_ENTRIES);
			db.execSQL(BusStopServeLine.SQL_DELETE_ENTRIES);
		}

		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	/**
	 * Store bus stops.
	 * 
	 * @author boz
	 */
	public static abstract class BusStop implements BaseColumns {
		public static final String TABLE_NAME = "busstop";
		public static final String COLUMN_NAME_BUSSTOP_ID = "busstop_ID";
		public static final String COLUMN_NAME_BUSSTOP_NAME = "busstop_name";
		public static final String COLUMN_NAME_BUSSTOP_ISFAVORITE = "busstop_isfavorite";

		public static final String IS_FAVORITE = "1";
		public static final String IS_NOT_FAVORITE = "0";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE_IF_NOT_EXISTS
				+ TABLE_NAME
				+ " ("
				+ COLUMN_NAME_BUSSTOP_ID
				+ INTEGER_TYPE
				+ PRIMARY_KEY
				+ NOT_NULL
				+ COMMA
				+ COLUMN_NAME_BUSSTOP_NAME
				+ TEXT_TYPE
				+ NOT_NULL
				+ COMMA
				+ COLUMN_NAME_BUSSTOP_ISFAVORITE
				+ INTEGER_TYPE + NOT_NULL + getDefault(0) + ")";

		private static final String SQL_DELETE_ENTRIES = DROP_TABLE
				+ TABLE_NAME;

		public static long addBusStop(SQLiteDatabase db, Integer ID, String name) {
			ContentValues values = new ContentValues();
			values.put(COLUMN_NAME_BUSSTOP_ID, ID);
			values.put(COLUMN_NAME_BUSSTOP_NAME, name);
			long lastInserted = db.insertWithOnConflict(TABLE_NAME, null,
					values, SQLiteDatabase.CONFLICT_IGNORE);
			Log.d("DBBusTo", "last busStopID inserted: " + lastInserted);
			return lastInserted;
		}

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}
	}

	/**
	 * Store bus lines.
	 * 
	 * @author boz
	 */
	public static abstract class BusLine implements BaseColumns {
		public static final String TABLE_NAME = "busline";
		public static final String COLUMN_NAME_BUSLINE_ID = "busline_ID";
		public static final String COLUMN_NAME_BUSLINE_NAME = "busline_name"; // Is
																				// there
																				// one?
		public static final String COLUMN_NAME_BUSLINE_ISFAVORITE = "busline_isfavorite";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE_IF_NOT_EXISTS
				+ TABLE_NAME
				+ " ("
				+ COLUMN_NAME_BUSLINE_ID
				+ INTEGER_TYPE
				+ PRIMARY_KEY
				+ NOT_NULL
				+ COMMA
				+ COLUMN_NAME_BUSLINE_NAME
				+ TEXT_TYPE
				+ NOT_NULL
				+ COMMA
				+ COLUMN_NAME_BUSLINE_ISFAVORITE
				+ INTEGER_TYPE + NOT_NULL + getDefault(0) + ")";

		private static final String SQL_DELETE_ENTRIES = DROP_TABLE
				+ TABLE_NAME;

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}

		public static long addBusLine(SQLiteDatabase db, Integer busLineID,
				String busLineName) {
			ContentValues values = new ContentValues();
			values.put(COLUMN_NAME_BUSLINE_ID, busLineID);
			values.put(COLUMN_NAME_BUSLINE_NAME, busLineName);
			return db.insertWithOnConflict(TABLE_NAME, null, values,
					SQLiteDatabase.CONFLICT_IGNORE);
		}
	}

	/**
	 * Associate every bus stop on every bus line served
	 * 
	 * @author boz
	 * 
	 */
	public static abstract class BusStopServeLine {
		public static final String TABLE_NAME = "busstopserveline";
		public static final String COLUMN_NAME_BUSSTOP_ID = "busstop_ID";
		public static final String COLUMN_NAME_BUSLINE_ID = "busline_ID";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE_IF_NOT_EXISTS
				+ TABLE_NAME
				+ " ("
				+ COLUMN_NAME_BUSSTOP_ID
				+ INTEGER_TYPE
				+ NOT_NULL
				+ COMMA
				+ COLUMN_NAME_BUSLINE_ID
				+ INTEGER_TYPE
				+ NOT_NULL
				+ COMMA
				+ PRIMARY_KEY
				+ " ("
				+ COLUMN_NAME_BUSSTOP_ID
				+ COMMA
				+ COLUMN_NAME_BUSLINE_ID
				+ "))"; // Add primary key on two columns

		private static final String SQL_DELETE_ENTRIES = DROP_TABLE
				+ TABLE_NAME;

		public static void addBusStopServeLine(SQLiteDatabase db,
				Integer busStopID, Integer busLineID) {
			ContentValues values = new ContentValues();
			values.put(COLUMN_NAME_BUSSTOP_ID, busStopID);
			values.put(COLUMN_NAME_BUSLINE_ID, busLineID);
			long inserted = db.insertWithOnConflict(TABLE_NAME, null, values,
					SQLiteDatabase.CONFLICT_IGNORE);
			Log.d("MySB", "Last inserted busStopServeLine: " + inserted);
		}

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}
	}

}
