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
package it.reyboz.bustorino.lab;

import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusLine;

import android.database.Cursor;
import android.database.SQLException;
import android.provider.BaseColumns;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.content.Context;

import java.util.ArrayList;

/**
 * Manage SQLite
 * 
 * @author Valerio Bozzolan
 */
public class MyDB extends SQLiteOpenHelper {

	// If you change the database schema, you must increment the database
	// version.
	public static final int DATABASE_VERSION = 8;
	public static final String DATABASE_NAME = "bustorino.db";

	public static final String COMMA = ", ";
	public static final String DOT = ".";
	public static final String EQUALS = " = ";
	public static final String CREATE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS ";
	public static final String DROP_TABLE = "DROP TABLE IF EXISTS ";
	public static final String TEXT_TYPE = " TEXT";
	public static final String INTEGER_TYPE = " INTEGER";
    public static final String REAL_TYPE = " REAL ";
	public static final String PRIMARY_KEY = " PRIMARY KEY";
	public static final String NOT_NULL = " NOT NULL";
	public static final String WHERE = " WHERE ";
	public static final String AND = " AND ";
	public static final String SELECT = "SELECT ";
	public static final String FROM = " FROM ";
	public static final String SELECT_ALL_FROM = "SELECT * FROM ";
	public static final String ORDER_BY = " ORDER BY ";
	public static final String ASC = " ASC";
	public static final String DESC = " DESC";

	public static String getDefault(String i) {
		return " DEFAULT " + String.valueOf(i);
	}

	/**
	 * @deprecated CAUSES CRASHES IN PARAMETRIC QUERIES
	 * @param columnName
	 * @return
	 */
	public static String somethingEqualsString(String columnName) {
		return " " + columnName + "=\"?\"";
	}

	public static String somethingEqualsWithoutQuotes(String columnName) {
		return " " + columnName + "=?";
	}

	public MyDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DBBusStop.SQL_CREATE_ENTRIES);
		db.execSQL(DBBusLine.SQL_CREATE_ENTRIES);
		db.execSQL(DBBusStopServeLine.SQL_CREATE_ENTRIES);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean somethingTerribleIsGoingToHappen = false;

		if(oldVersion == 6 && newVersion == 7) {
			// Added two tables. It's OK!
			oldVersion = 7;
		}
        if(oldVersion == 7 && newVersion == 8) {
            /****** TOO MANY CHANGES. SORRY ME! THIS WILL CAUSE THE DROP OF YOUR CURRENT DB! ******/
            somethingTerribleIsGoingToHappen = true;
        }

        if (oldVersion != DATABASE_VERSION || somethingTerribleIsGoingToHappen){
            // Do you hear these cries? :'(
			db.execSQL(DBBusStop.SQL_DROP_TABLE);
			db.execSQL(DBBusLine.SQL_DROP_TABLE);
			db.execSQL(DBBusStopServeLine.SQL_DROP_TABLE);
		}

		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	public static boolean rowExists(SQLiteDatabase db, String tableName, String columnName, String columnValue) {
		Cursor c = db.rawQuery(SELECT_ALL_FROM + tableName + WHERE + somethingEqualsWithoutQuotes(columnName), new String[]{columnValue});
		boolean success = c.moveToFirst();
		c.close();
		return success;
	}

	/**
	 * Store bus stops.
	 */
	public static abstract class DBBusStop implements BaseColumns {
		public static final String TABLE_NAME = "busstop";
		public static final String COLUMN_NAME_BUSSTOP_ID = "busstop_ID";
		public static final String COLUMN_NAME_BUSSTOP_NAME = "busstop_name";
        public static final String COLUMN_NAME_BUSSTOP_USERNAME = "busstop_username"; // User manually edit it
        public static final String COLUMN_NAME_BUSSTOP_LOCALITY = "busstop_locality";
        public static final String COLUMN_NAME_BUSSTOP_LATITUDE = "busstop_latitude";
        public static final String COLUMN_NAME_BUSSTOP_LONGITUDE = "busstop_longitude";
		public static final String COLUMN_NAME_BUSSTOP_ISFAVORITE = "busstop_isfavorite";

		public static final String IS_FAVORITE = "1";
		public static final String IS_NOT_FAVORITE = "0";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE_IF_NOT_EXISTS
				+ TABLE_NAME
				+ " ("
				+ COLUMN_NAME_BUSSTOP_ID + TEXT_TYPE + PRIMARY_KEY + NOT_NULL + COMMA
				+ COLUMN_NAME_BUSSTOP_NAME + TEXT_TYPE + NOT_NULL + COMMA
                + COLUMN_NAME_BUSSTOP_USERNAME + TEXT_TYPE + COMMA
				+ COLUMN_NAME_BUSSTOP_ISFAVORITE + INTEGER_TYPE + NOT_NULL + getDefault(IS_NOT_FAVORITE) + COMMA
                + COLUMN_NAME_BUSSTOP_LOCALITY + TEXT_TYPE + COMMA
                + COLUMN_NAME_BUSSTOP_LATITUDE + REAL_TYPE + COMMA
                + COLUMN_NAME_BUSSTOP_LONGITUDE + REAL_TYPE
                + ")";

		private static final String SQL_DROP_TABLE = DROP_TABLE + TABLE_NAME;

		public static boolean busStopExists(SQLiteDatabase db, String busStopID) {
			return rowExists(db, TABLE_NAME, getColumn(COLUMN_NAME_BUSSTOP_ID), busStopID);
		}

        public final static int FORCE_NULL_BUSSTOP_USERNAME = 1;

		/**
		 * Add a bus stop or replace the existing bus stop.
		 * It also add its bus lines!
		 *
		 * @param db
		 * @param busStop
         * @param forceNULL
		 * @return
		 * @throws SQLException
		 */
		public static void addBusStop(SQLiteDatabase db, BusStop busStop, int forceNULL) throws SQLException {
			ContentValues values = new ContentValues();

			if(busStop.getBusStopName() != null) {
				values.put(COLUMN_NAME_BUSSTOP_NAME, busStop.getBusStopName());
			}
            if(busStop.getBusStopUsername() != null) {
                values.put(COLUMN_NAME_BUSSTOP_USERNAME, busStop.getBusStopUsername());
            } else if((forceNULL & FORCE_NULL_BUSSTOP_USERNAME) != 0) {
                values.putNull(COLUMN_NAME_BUSSTOP_USERNAME);
            }
			if (busStop.getBusStopLocality() != null) {
				values.put(COLUMN_NAME_BUSSTOP_LOCALITY, busStop.getBusStopLocality());
			}
			if (busStop.getIsFavorite() != null) {
				values.put(COLUMN_NAME_BUSSTOP_ISFAVORITE, busStop.getIsFavorite() ? IS_FAVORITE : IS_NOT_FAVORITE);
			}
            if(busStop.getLatitude() != null && busStop.getLongitude() != null) {
                values.put(COLUMN_NAME_BUSSTOP_LATITUDE, busStop.getLatitude());
                values.put(COLUMN_NAME_BUSSTOP_LONGITUDE, busStop.getLongitude());
            }

			if(busStopExists(db, busStop.getBusStopID())) {
				db.update(TABLE_NAME, values, somethingEqualsWithoutQuotes(COLUMN_NAME_BUSSTOP_ID), new String[]{busStop.getBusStopID()});

				Log.d("MyDB", "busStop with busStopID " + busStop.getBusStopID() + " updated");
            } else {
				values.put(COLUMN_NAME_BUSSTOP_ID, busStop.getBusStopID());

				long lastInserted = db.insert(TABLE_NAME, null,
						values);

				Log.d("MyDB", "busStop with busStopID " + busStop.getBusStopID() + " inserted as " + lastInserted);
			}

            // Javamerda
            if(busStop.getBusLines() != null) {
                for (BusLine busLine : busStop.getBusLines()) {
                    DBBusLine.addBusLine(db, busLine);
                    DBBusStopServeLine.addBusStopServeLine(db, busStop.getBusStopID(), busLine.getBusLineID());
                }
            }
		}

        public static void addBusStop(SQLiteDatabase db, BusStop busStop) throws SQLException {
            addBusStop(db, busStop, 0);
        }

        public static BusStop[] getFavoriteBusStops(SQLiteDatabase db) {
            ArrayList<BusStop> busStops = new ArrayList<BusStop>();

            String query = MyDB.SELECT_ALL_FROM
                    + TABLE_NAME
                    + WHERE
                    + somethingEqualsWithoutQuotes(COLUMN_NAME_BUSSTOP_ISFAVORITE)
                    + ORDER_BY + COLUMN_NAME_BUSSTOP_NAME + ASC;

            Cursor c = db.rawQuery(query, new String[]{DBBusStop.IS_FAVORITE});

            while (c.moveToNext()) {
                String busStopID = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_ID));
                String busStopName = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_NAME));
                String busStopUsername = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_USERNAME));
                String busStopLocality = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_LOCALITY));

                BusLine[] busLines = DBBusStopServeLine.getBusLinesServedByBusStopID(db, busStopID);

                BusStop busStop = new BusStop(busStopID, busStopName, busLines);
                busStop.setBusStopUsername(busStopUsername);
                busStop.setBusStopLocality(busStopLocality);

                busStops.add(busStop);
            }
            c.close();

            return busStops.toArray(new BusStop[busStops.size()]);
        }

        public static BusStop getBusStop(SQLiteDatabase db, String searchBusStopID) {
            BusStop busStop = null;

            String query = MyDB.SELECT_ALL_FROM
                    + TABLE_NAME
                    + WHERE
                    + somethingEqualsWithoutQuotes(COLUMN_NAME_BUSSTOP_ID);

            Cursor c = db.rawQuery(query, new String[]{searchBusStopID});

            while (c.moveToNext()) {
                String busStopID = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_ID));
                String busStopName = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_NAME));
                String busStopUsername = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_USERNAME));
                String busStopLocality = c.getString(c.getColumnIndex(COLUMN_NAME_BUSSTOP_LOCALITY));

                BusLine[] busLines = DBBusStopServeLine.getBusLinesServedByBusStopID(db, busStopID);

                busStop = new BusStop(busStopID, busStopName, busLines);
                busStop.setBusStopUsername(busStopUsername);
                busStop.setBusStopLocality(busStopLocality);
            }
            c.close();

            return busStop;
        }

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}
	}

	/**
	 * Store bus lines.
	 */
	public static abstract class DBBusLine implements BaseColumns {
		public static final String TABLE_NAME = "busline";
		public static final String COLUMN_NAME_BUSLINE_ID = "busline_ID";
		public static final String COLUMN_NAME_BUSLINE_NAME = "busline_name";
        public static final String COLUMN_NAME_BUSLINE_USERNAME = "busline_username";
        public static final String COLUMN_NAME_BUSLINE_TYPE = "busline_type";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE_IF_NOT_EXISTS
				+ TABLE_NAME
				+ " ("
				+ COLUMN_NAME_BUSLINE_ID + TEXT_TYPE + PRIMARY_KEY + NOT_NULL + COMMA
				+ COLUMN_NAME_BUSLINE_NAME + TEXT_TYPE + NOT_NULL + COMMA
                + COLUMN_NAME_BUSLINE_USERNAME + TEXT_TYPE + COMMA
                + COLUMN_NAME_BUSLINE_TYPE + INTEGER_TYPE
                + ")";

		private static final String SQL_DROP_TABLE = DROP_TABLE	+ TABLE_NAME;

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}

		public static boolean busLineExists(SQLiteDatabase db, Integer busLineID) {
			return rowExists(db, TABLE_NAME, getColumn(COLUMN_NAME_BUSLINE_ID), busLineID.toString());
		}

		public static void addBusLine(SQLiteDatabase db, BusLine busLine) throws SQLException {
			ContentValues values = new ContentValues();

			values.put(COLUMN_NAME_BUSLINE_NAME, busLine.getBusLineName());

            if(busLine.getBusLineUsername() != null) {
                values.put(COLUMN_NAME_BUSLINE_USERNAME, busLine.getBusLineUsername());
            }
            if(busLine.getBusLineType() != null) {
                values.put(COLUMN_NAME_BUSLINE_TYPE, busLine.getBusLineType());
            }

			if(busLineExists(db, busLine.getBusLineID())) {
				db.update(TABLE_NAME, values, somethingEqualsWithoutQuotes(COLUMN_NAME_BUSLINE_ID), new String[] { String.valueOf(busLine.getBusLineID()) });

				Log.d("MyDB", "BusLine with busLineID " + busLine.getBusLineID() + " updated");
			} else {
				values.put(COLUMN_NAME_BUSLINE_ID, busLine.getBusLineID());

				long lastInserted = db.insert(TABLE_NAME, null, values);
				Log.d("MyDB", "BusLine with busLineID " + busLine.getBusLineID() + " inserted as " + lastInserted);
			}
		}
	}

	/**
	 * Associate every bus stop on every bus line served
	 */
	public static abstract class DBBusStopServeLine {
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

		private static final String SQL_DROP_TABLE = DROP_TABLE	+ TABLE_NAME;

		/**
		 * Serve a bus stop by a bus line (if it don't exists)
		 * @param db
		 * @param busStopID
		 * @param busLineID
		 */
		public static void addBusStopServeLine(SQLiteDatabase db,
				String busStopID, Integer busLineID) {

			String queryExists = SELECT
					+ getColumn(COLUMN_NAME_BUSSTOP_ID) + COMMA
					+ getColumn(COLUMN_NAME_BUSLINE_ID)
					+ FROM
					+ TABLE_NAME
					+ WHERE
					+ somethingEqualsWithoutQuotes(COLUMN_NAME_BUSSTOP_ID)
					+ AND
					+ somethingEqualsWithoutQuotes(COLUMN_NAME_BUSLINE_ID);
			Cursor cursor = db.rawQuery(queryExists,
					new String[] { String.valueOf(busStopID), String.valueOf(busLineID) });

			boolean exists = cursor.moveToNext();
			cursor.close();

			if(exists) {
				Log.d("MyDB", "busStopServeLine relation between busStopID " + busStopID + " and busLineID " + busLineID + " already inserted");
			} else {
				ContentValues values = new ContentValues();
				values.put(COLUMN_NAME_BUSSTOP_ID, busStopID);
				values.put(COLUMN_NAME_BUSLINE_ID, busLineID);
				long inserted = db.insert(TABLE_NAME, null, values);
				Log.d("MyDB", "busStopServeLine relation between busStopID " + busStopID + " and busLineID " + busLineID + " inserted as " + inserted);
			}
		}

		public static String getColumn(String columnName) {
			return TABLE_NAME + DOT + columnName;
		}

        /**
         * Get bus lines server by a bus stop.
         * @param db
         * @param busStopID
         * @return
         */
        public static BusLine[] getBusLinesServedByBusStopID(SQLiteDatabase db, String busStopID) {
            ArrayList<BusLine> busLines = new ArrayList<BusLine>();

            String queryLines = SELECT
                    + DBBusLine.getColumn(DBBusLine.COLUMN_NAME_BUSLINE_ID) + COMMA
                    + DBBusLine.getColumn(DBBusLine.COLUMN_NAME_BUSLINE_NAME)
                    + FROM
                    + TABLE_NAME + COMMA
                    + DBBusLine.TABLE_NAME
                    + WHERE
                    + somethingEqualsWithoutQuotes(getColumn(COLUMN_NAME_BUSSTOP_ID))
                    + AND
                    + getColumn(COLUMN_NAME_BUSLINE_ID)
                    + EQUALS
                    + DBBusLine.getColumn(DBBusLine.COLUMN_NAME_BUSLINE_ID)
                    + ORDER_BY
                    + DBBusLine.getColumn(DBBusLine.COLUMN_NAME_BUSLINE_NAME)
                    + ASC;

            Cursor cursor = db.rawQuery(queryLines,
                    new String[] { String.valueOf(busStopID) });

            while (cursor.moveToNext()) {
                Integer busLineID = cursor.getInt(0);
                String busLineName = cursor.getString(1);
                busLines.add(new BusLine(busLineID, busLineName));
            }
            cursor.close();

            return busLines.toArray(new BusLine[busLines.size()]);
        }
	}

}
