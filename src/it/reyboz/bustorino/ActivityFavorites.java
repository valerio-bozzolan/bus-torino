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

import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.MyDB;
import it.reyboz.bustorino.lab.MyDB.DBBusStop;
import it.reyboz.bustorino.lab.adapters.AdapterBusStops;

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

public class ActivityFavorites extends ActionBarActivity {
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
				db.delete(DBBusStop.TABLE_NAME,
						MyDB.somethingEqualsWithoutQuotes(DBBusStop.COLUMN_NAME_BUSSTOP_ID),
						new String[] { busStopID });
                db.delete(MyDB.DBBusStopServeLine.TABLE_NAME,
                        MyDB.somethingEqualsWithoutQuotes(MyDB.DBBusStopServeLine.COLUMN_NAME_BUSSTOP_ID),
                        new String[] { busStopID });
				    createFavoriteList();
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	void createFavoriteList() {
		BusStop[] busStops = MyDB.DBBusStop.getFavoriteBusStops(db);

		// If no data is found show a friendly message
		if (busStops.length == 0) {
			favoriteListView.setVisibility(View.INVISIBLE);
			TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
			favoriteTipTextView.setVisibility(View.VISIBLE);
		}

		// Show results
        favoriteListView.setAdapter(new AdapterBusStops(this, R.layout.entry_bus_stop, busStops));
		favoriteListView
				.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        /**
                         * Casting because of Javamerda
                         * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                         */
                        BusStop busStop = (BusStop) parent.getItemAtPosition(position);

						Intent intent = new Intent(ActivityFavorites.this,
								ActivityMain.class);

						Bundle b = new Bundle();
						b.putString("busStopID", busStop.getBusStopID());
						intent.putExtras(b);
						intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_NEW_TASK);

                        Log.d("FavoritesActivity", "Tapped on bus stop: "
                                + busStop.getBusStopID());

						startActivity(intent);

						finish();
					}
				});
		registerForContextMenu(favoriteListView);
	}
}
