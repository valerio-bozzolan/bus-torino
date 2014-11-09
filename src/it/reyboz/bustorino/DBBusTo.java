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
	public static final int DATABASE_VERSION = 5;
	public static final String DATABASE_NAME = "bustorino.db";

	private static String COMMA = ", ";
	private static final String CREATE_TABLE = "CREATE TABLE ";
	private static final String DROP_TABLE = "DROP TABLE IF EXISTS ";
	private static final String TEXT_TYPE = " TEXT";
	private static final String INTEGER_TYPE = " INTEGER";
	private static final String PRIMARY_KEY = " PRIMARY KEY";

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

		private static final String SQL_CREATE_ENTRIES = CREATE_TABLE
				+ TABLE_NAME + " (" + COLUMN_NAME_BUSSTOP_ID
				+ INTEGER_TYPE + PRIMARY_KEY + COMMA
				+ COLUMN_NAME_BUSSTOP_NAME + TEXT_TYPE + " )";

		private static final String SQL_DELETE_ENTRIES = DROP_TABLE
				+ TABLE_NAME;
	}

}
