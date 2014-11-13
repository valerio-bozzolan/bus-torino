package it.reyboz.bustorino;

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
	private DBBusTo mDbHelper;
	private SQLiteDatabase db;
	private ListView favoriteListView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_favorites);

		mDbHelper = new DBBusTo(this);
		db = mDbHelper.getWritableDatabase();

		createFavoriteList();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	      super.onCreateContextMenu(menu, v, menuInfo);
	      if (v.getId()==R.id.favoriteListView) {
	          MenuInflater inflater = getMenuInflater();
	          inflater.inflate(R.menu.menu_favourites_entry, menu);
	      }
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
	      AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	      String busStopID = ((TextView) (info.targetView).findViewById(R.id.busStopID)).getText().toString();
	      switch(item.getItemId()) {
	         case R.id.action_favourite_entry_delete:
	        	 db.delete(DBBusTo.BusStop.TABLE_NAME, DBBusTo.somethingEqualsInt(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID), new String[] {busStopID});
	        	 createFavoriteList();
	            return true;
	          default:
	                return super.onContextItemSelected(item);
	      }
	}

	void createFavoriteList() {
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

			singleEntry.put("bus-stop-ID", busStopID);
			singleEntry.put("bus-stop-name", busStopName);
			data.add(singleEntry);
		}
		cursor.close();

		favoriteListView = (ListView) findViewById(R.id.favoriteListView);

		// If no data is found show a friendly message
		if (data.isEmpty()) {
			favoriteListView.setVisibility(View.INVISIBLE);
			TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
			favoriteTipTextView.setVisibility(View.VISIBLE);
		}

		// Show results
		String[] from = { "bus-stop-ID", "bus-stop-name" };
		int[] to = { R.id.busStopID, R.id.busStopName };
		SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
				data, R.layout.bus_stop_entry, from, to);
		favoriteListView.setAdapter(adapter);
		favoriteListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> av, View view,
					int i, long l) {
				View child = av.getChildAt(i);
				TextView busStopIDTextView = (TextView) child
						.findViewById(R.id.busStopID);
				String busStopID = busStopIDTextView.getText().toString();
				Log.d("bus-torino", "bustorino tapped on busstop: " + busStopID);
				Intent intent = new Intent(FavoritesActivity.this, MainActivity.class);
				Bundle b = new Bundle();
				b.putString("busStopID", busStopID);
				intent.putExtras(b);
				startActivity(intent);
				finish();
			}
		});
		registerForContextMenu(favoriteListView);
	}
}
