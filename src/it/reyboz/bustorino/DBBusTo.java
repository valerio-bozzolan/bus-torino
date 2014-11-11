package it.reyboz.bustorino;

import android.provider.BaseColumns;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;

/**
 * Manage SQLite
 * 
 * @author boz
 */
public class DBBusTo extends SQLiteOpenHelper {

	// If you change the database schema, you must increment the database
	// version.
	public static final int DATABASE_VERSION = 6;
	public static final String DATABASE_NAME = "bustorino.db";

	public static String COMMA = ", ";
	public static final String CREATE_TABLE = "CREATE TABLE ";
	public static final String DROP_TABLE = "DROP TABLE IF EXISTS ";
	public static final String TEXT_TYPE = " TEXT";
	public static final String INTEGER_TYPE = " INTEGER";
	public static final String PRIMARY_KEY = " PRIMARY KEY";
	public static final String NOT_NULL = " NOT NULL";
	public static final String UPDATE = "UPDATE ";
	public static final String WHERE = " WHERE ";
	public static final String SELECT_ALL_FROM = "SELECT * FROM ";
	public static final String ORDER_BY = " ORDER BY ";
	public static final String ASC = " ASC";
	public static final String DESC = " DESC";

	public static String getDefault(int i) {
		return " DEFAULT " + String.valueOf(i);
	}

	public static String somethingEqualsString(String s) {
		return " " + s + "=\"?\"";
	}

	public static String somethingEqualsInt(String s) {
		return " " + s + "=?";
	}

	public static String somethingEquals(String s1, String s2) {
		return " " + s1 + "=\"" + s2 + "\"";
	}

	public static String somethingEquals(String s1, int value) {
		return " " + s1 + "=" + value;
	}

	public DBBusTo(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(BusStop.SQL_CREATE_ENTRIES);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// This database is only a cache for online data, so its upgrade policy
		// is
		// to simply to discard the data and start over
		db.execSQL(BusStop.SQL_DELETE_ENTRIES);
		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	public static abstract class BusStop implements BaseColumns {
		public static final String TABLE_NAME = "busstop";
		public static final String COLUMN_NAME_BUSSTOP_ID = "busstop_ID";
		public static final String COLUMN_NAME_BUSSTOP_NAME = "busstop_name";
		public static final String COLUMN_NAME_BUSSTOP_ISFAVORITE = "busstop_isfavorite";

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE
				+ TABLE_NAME + " (" + COLUMN_NAME_BUSSTOP_ID + INTEGER_TYPE
				+ PRIMARY_KEY + NOT_NULL + COMMA + COLUMN_NAME_BUSSTOP_NAME
				+ TEXT_TYPE + NOT_NULL + COMMA + COLUMN_NAME_BUSSTOP_ISFAVORITE
				+ INTEGER_TYPE + NOT_NULL + getDefault(0) + " )";

		private static final String SQL_DELETE_ENTRIES = DROP_TABLE
				+ TABLE_NAME;
	}

}
