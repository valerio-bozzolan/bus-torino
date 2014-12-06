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

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.GTTSiteSucker.BusLine;
import it.reyboz.bustorino.GTTSiteSucker.BusStop;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;

public class MainActivity extends ActionBarActivity {

	/*
	 * Various layout elements
	 */
	private EditText busStopIDEditText;
	private TextView busStopNameTextView;
	private ProgressBar progressBar;
	private TextView legendTextView;
	private TextView howDoesItWorkTextView;
	private Button hideHintButton;
	private MenuItem actionHelpMenuItem;
	private ListView resultsListView;
	private SwipeRefreshLayout swipeRefreshLayout;

	/*
	 * @see swipeRefreshLayout
	 */
	private Handler handler = new Handler();
	private final Runnable refreshing = new Runnable() {
		public void run() {
			launchSearchAction(String.valueOf(lastSearchedBusStopID));
		}
	};

	/**
	 * Last successfully searched bus stop ID
	 */
	private Integer lastSearchedBusStopID;

	private MyAsyncWget myAsyncWget;
	private MyDB mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		busStopIDEditText = (EditText) findViewById(R.id.busStopIDEditText);
		busStopNameTextView = (TextView) findViewById(R.id.busStopNameTextView);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		legendTextView = (TextView) findViewById(R.id.legend);
		howDoesItWorkTextView = (TextView) findViewById(R.id.howDoesItWork);
		hideHintButton = (Button) findViewById(R.id.hideHint);
		resultsListView = (ListView) findViewById(R.id.resultsListView);
		swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
		myAsyncWget = new MyAsyncWget();

		// IME_ACTION_SEARCH keyboard option
		busStopIDEditText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId,
							KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_SEARCH) {
							onSearchClick(v);
							return true;
						}
						return false;
					}
				});

		// Get data in write mode
		mDbHelper = new MyDB(this);
		db = mDbHelper.getWritableDatabase();

		// Called when the layout is pulled down
		swipeRefreshLayout
				.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
					@Override
					public void onRefresh() {
						handler.post(refreshing);
					}
				});

		// Intercept calls from other activities
		Bundle b = getIntent().getExtras();
		if (b != null) {
			String busStopID = b.getString("busStopID");
			if (busStopID != null) {
				busStopIDEditText.setText(busStopID);
				launchSearchAction(busStopID);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);

		actionHelpMenuItem = menu.findItem(R.id.action_help);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		case R.id.action_about:
			startActivity(new Intent(MainActivity.this, AboutActivity.class));
			return true;
		case R.id.action_favorites:
			startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
			return true;
		case R.id.action_help:
			showHints();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Catch the Async action!
	 * 
	 * @author boz
	 */
	public class MyAsyncWget extends AsyncWget {
		protected void onPostExecute(String htmlResponse) {

			// Network errors?
			if (htmlResponse == null || exceptions != null) {
				NetworkTools.showNetworkError(MainActivity.this);
				hideSpinner();
				Log.e("MainActivity", "Network error!");
				return;
			}

			// Try parsing arrivals
			BusStop busStop = GTTSiteSucker.getBusStopSuckingHTML(db,
					htmlResponse);

			// Parse errors?
			if (busStop == null) {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
				hideSpinner();
				Log.e("MainActivity", "Parse error! htmlResponse: "
						+ htmlResponse);
				return;
			}

			// Order bus lines by first arrival time :D
			busStop.orderBusLinesByFirstArrival();

			// Remember last successfully searched busStopID
			Integer busStopID = busStop.getBusStopID();
			lastSearchedBusStopID = busStopID;

			// Retrieve passages
			BusLine[] busLines = busStop.getBusLines();

			// No passages?
			if (busLines.length == 0) {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
				hideSpinner();
				Log.d("MainActivity", "No passages!");
				return;
			}

			String busStopName = busStop.getBusStopName();
			String busStopNameDisplay;
			if (busStopName == null) {
				busStopNameDisplay = String.valueOf(busStopID);
			} else {
				busStopNameDisplay = busStopName;
			}
			busStopNameTextView.setText(String.format(
					getString(R.string.passages), busStopNameDisplay));

			// Insert GTTBusStop info in the DB
			MyDB.BusStop.addBusStop(db, busStopID, busStopName);

			// Populate the stupid ListView SimpleAdapter
			ArrayList<HashMap<String, Object>> entries = new ArrayList<HashMap<String, Object>>();
			for (BusLine busLine : busLines) {
				HashMap<String, Object> entry = new HashMap<String, Object>();
				String passages = busLine.getTimePassagesString();
				if (passages == null) {
					passages = getString(R.string.no_passages);
				}
				entry.put("icon", busLine.getBusLineName());
				entry.put("passages", passages);
				entries.add(entry);
			}

			// Hide the keyboard before showing passages
			hideKeyboard();

			// Shows hints + bus stop name + results + enable
			if (getOption("show_legend", true)) {
				showHints();
			} else {
				hideHints();
			}
			busStopNameTextView.setVisibility(View.VISIBLE);
			swipeRefreshLayout.setEnabled(true);
			resultsListView.setVisibility(View.VISIBLE);

			// Stops annoying spinner
			hideSpinner();

			// Show results using the stupid SimpleAdapter
			String[] from = { "icon", "passages" };
			int[] to = { R.id.busLineIcon, R.id.busLineNames };
			SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
					entries, R.layout.bus_line_passage_entry, from, to);
			resultsListView.setAdapter(adapter);
			resultsListView.invalidate();
			resultsListView
					.setOnItemClickListener(new AdapterView.OnItemClickListener() {
						public void onItemClick(AdapterView<?> av, View view,
								int i, long l) {
							String busLine = ((TextView) av.getChildAt(i)
									.findViewById(R.id.busLineIcon)).getText()
									.toString();
							Log.d("MainActivity", "Tapped busLine: " + busLine);
						}
					});
		}
	}

	public void onSearchClick(View v) {
		launchSearchAction(busStopIDEditText.getText().toString());
	}

	public void onHideHint(View v) {
		hideHints();
		setOption("show_legend", false);
	}

	public void launchSearchAction(String busStopID) {
		if (busStopID.isEmpty()) {
			Toast.makeText(getApplicationContext(),
					R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT)
					.show();
		} else if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			showSpinner();
			myAsyncWget.cancel(true);
			myAsyncWget = new MyAsyncWget();
			myAsyncWget.execute(GTTSiteSucker
					.arrivalTimesByLineQuery(busStopID));
		}
	}

	public void addInFavorites(View v) {
		if (lastSearchedBusStopID != null) {
			ContentValues newValues = new ContentValues();
			newValues.put(MyDB.BusStop.COLUMN_NAME_BUSSTOP_ISFAVORITE, 1);
			db.update(MyDB.BusStop.TABLE_NAME, newValues, MyDB
					.somethingEqualsInt(MyDB.BusStop.COLUMN_NAME_BUSSTOP_ID),
					new String[] { String.valueOf(lastSearchedBusStopID) });
			Toast.makeText(getApplicationContext(),
					R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
		}
	}

	private void setOption(String optionName, boolean value) {
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putBoolean(optionName, value);
		editor.commit();
	}

	private boolean getOption(String optionName, boolean optDefault) {
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		return preferences.getBoolean(optionName, optDefault);
	}

	private void showSpinner() {
		swipeRefreshLayout.setRefreshing(true);
		progressBar.setVisibility(View.VISIBLE);
	}

	private void hideSpinner() {
		swipeRefreshLayout.setRefreshing(false);
		progressBar.setVisibility(View.INVISIBLE);
	}

	private void hideKeyboard() {
		View view = getCurrentFocus();
		if (view != null) {
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
					.hideSoftInputFromWindow(view.getWindowToken(),
							InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	private void showHints() {
		Log.d("MainActivity", "HINTS mostrati");
		howDoesItWorkTextView.setVisibility(View.VISIBLE);
		legendTextView.setVisibility(View.VISIBLE);
		hideHintButton.setVisibility(View.VISIBLE);
		actionHelpMenuItem.setVisible(false);
	}

	private void hideHints() {
		Log.d("MainActivity", "HINTS nascosti");
		howDoesItWorkTextView.setVisibility(View.GONE);
		legendTextView.setVisibility(View.GONE);
		hideHintButton.setVisibility(View.GONE);
		actionHelpMenuItem.setVisible(true);
	}
}