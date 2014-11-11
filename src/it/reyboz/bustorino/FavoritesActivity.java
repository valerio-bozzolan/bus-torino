package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.HashMap;

import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class FavoritesActivity extends ActionBarActivity {
	private DBBusTo mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_favorites);

		mDbHelper = new DBBusTo(this);
		db = mDbHelper.getWritableDatabase();

		ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();

		String query = DBBusTo.SELECT_ALL_FROM
				+ DBBusTo.BusStop.TABLE_NAME
				+ DBBusTo.WHERE
				+ DBBusTo
						.somethingEqualsInt(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ISFAVORITE)
				+ DBBusTo.ORDER_BY + DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID
				+ DBBusTo.ASC;
		Cursor cursor = db.rawQuery(query, new String[] { "1" });
		while (cursor.moveToNext()) {
			HashMap<String, Object> singleEntry = new HashMap<String, Object>();

			int busStopIDIndex = cursor
					.getColumnIndex(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID);
			int busStopNameIndex = cursor
					.getColumnIndex(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_NAME);

			int busStopID = cursor.getInt(busStopIDIndex);
			String busStopName = cursor.getString(busStopNameIndex);

			singleEntry.put("icon", busStopID);
			singleEntry.put("line-name", busStopName);
			data.add(singleEntry);
		}
		cursor.close();

		ListView favoriteListView = (ListView) findViewById(R.id.favoriteListView);

		// If no data is found show a friendly message
		if (data.isEmpty()) {
			favoriteListView.setVisibility(View.INVISIBLE);
			TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
			favoriteTipTextView.setVisibility(View.VISIBLE);
		}

		// Show results
		String[] from = { "icon", "line-name" };
		int[] to = { R.id.busLineIcon, R.id.busLine };
		SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
				data, R.layout.bus_stop_entry, from, to);
		favoriteListView.setAdapter(adapter);

		// Close DB connection
		db.close();
	}
}
