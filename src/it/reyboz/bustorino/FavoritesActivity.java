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

import it.reyboz.bustorino.MyDB.BusLine;
import it.reyboz.bustorino.MyDB.BusStop;
import it.reyboz.bustorino.MyDB.BusStopServeLine;

import java.util.ArrayList;
import java.util.HashMap;

import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class FavoritesActivity extends ActionBarActivity {
	private ListView favoriteListView;

	private MyDB mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_favorites);

		mDbHelper = new MyDB(this);
		db = mDbHelper.getWritableDatabase();

		favoriteListView = (ListView) findViewById(R.id.favoriteListView);

		createFavoriteList();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if (v.getId() == R.id.favoriteListView) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu_favourites_entry, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		String busStopID = ((TextView) (info.targetView)
				.findViewById(R.id.busStopID)).getText().toString();
		switch (item.getItemId()) {
		case R.id.action_favourite_entry_delete:
			db.delete(BusStop.TABLE_NAME,
					MyDB.somethingEqualsInt(BusStop.COLUMN_NAME_BUSSTOP_ID),
					new String[] { busStopID });
			createFavoriteList();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	void createFavoriteList() {
		ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();

		String query = MyDB.SELECT_ALL_FROM
				+ BusStop.TABLE_NAME
				+ MyDB.WHERE
				+ MyDB.somethingEqualsInt(BusStop.COLUMN_NAME_BUSSTOP_ISFAVORITE)
				+ MyDB.ORDER_BY + BusStop.COLUMN_NAME_BUSSTOP_NAME + MyDB.ASC;
		Cursor cursor = db
				.rawQuery(query, new String[] { BusStop.IS_FAVORITE });
		while (cursor.moveToNext()) {
			HashMap<String, Object> singleEntry = new HashMap<String, Object>();

			int busStopID = cursor.getInt(cursor
					.getColumnIndex(MyDB.BusStop.COLUMN_NAME_BUSSTOP_ID));
			String busStopName = cursor.getString(cursor
					.getColumnIndex(MyDB.BusStop.COLUMN_NAME_BUSSTOP_NAME));

			// Get bus Lines
			String queryLines = MyDB.SELECT
					+ BusLine.COLUMN_NAME_BUSLINE_NAME
					+ MyDB.FROM
					+ BusStopServeLine.TABLE_NAME
					+ MyDB.COMMA
					+ BusLine.TABLE_NAME
					+ MyDB.WHERE
					+ MyDB.somethingEqualsInt(BusStopServeLine
							.getColumn(BusStopServeLine.COLUMN_NAME_BUSSTOP_ID))
					+ MyDB.AND
					+ BusStopServeLine
							.getColumn(BusStopServeLine.COLUMN_NAME_BUSLINE_ID)
					+ MyDB.EQUALS
					+ BusLine.getColumn(BusLine.COLUMN_NAME_BUSLINE_ID)
					+ MyDB.ORDER_BY + BusLine.COLUMN_NAME_BUSLINE_NAME
					+ MyDB.ASC;
			Cursor cursorLines = db.rawQuery(queryLines,
					new String[] { String.valueOf(busStopID) });
			String busLineNames = "";
			while (cursorLines.moveToNext()) {
				if (busLineNames != "") {
					busLineNames += ", ";
				}
				busLineNames += cursorLines.getString(0);
				Log.d("FavoritesActivity",
						"Bus line name: " + cursorLines.getString(0));
			}
			cursorLines.close();

			singleEntry.put("bus-stop-ID", busStopID);
			singleEntry.put("bus-stop-name", busStopName);
			singleEntry.put("bus-line-names", String.format(getResources()
					.getString(R.string.lines), busLineNames));
			data.add(singleEntry);
		}
		cursor.close();

		// If no data is found show a friendly message
		if (data.isEmpty()) {
			favoriteListView.setVisibility(View.INVISIBLE);
			TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
			favoriteTipTextView.setVisibility(View.VISIBLE);
		}

		// Show results
		String[] from = { "bus-stop-ID", "bus-stop-name", "bus-line-names" };
		int[] to = { R.id.busStopID, R.id.busStopName, R.id.busLineNames };
		SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
				data, R.layout.bus_stop_entry, from, to);
		favoriteListView.setAdapter(adapter);
		favoriteListView
				.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					public void onItemClick(AdapterView<?> av, View view,
							int i, long l) {
						String busStopID = ((TextView) view
								.findViewById(R.id.busStopID)).getText()
								.toString();

						Log.d("FavoritesActivity", "Tapped on bus stop: "
								+ busStopID);

						Intent intent = new Intent(FavoritesActivity.this,
								MainActivity.class);
						Bundle b = new Bundle();
						b.putString("busStopID", busStopID);
						intent.putExtras(b);
						intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
								| Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
						finish();
					}
				});
		registerForContextMenu(favoriteListView);
	}
}
