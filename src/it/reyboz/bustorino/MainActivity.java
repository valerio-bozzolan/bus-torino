package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.GTTSiteSucker.BusLinePassages;
import it.reyboz.bustorino.GTTSiteSucker.BusStop;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
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

	private EditText busStopIDEditText;
	private TextView busStopNameTextView;
	private TextView legend;
	private Button hideHint;
	private ProgressBar annoyingSpinner;
	private ListView resultsListView;

	private MyAsyncWget myAsyncWget;
	private DBBusTo mDbHelper;
	private SQLiteDatabase db;

	private Integer lastSearchedBusStopID;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		busStopIDEditText = (EditText) findViewById(R.id.busStopIDEditText);
		busStopNameTextView = (TextView) findViewById(R.id.busStopNameTextView);
		legend = (TextView) findViewById(R.id.legend);
		hideHint = (Button) findViewById(R.id.hideHint);
		annoyingSpinner = (ProgressBar) findViewById(R.id.annoyingSpinner);
		resultsListView = (ListView) findViewById(R.id.resultsListView);
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
		mDbHelper = new DBBusTo(this);
		db = mDbHelper.getWritableDatabase();

		// Intercept calls from other part of the apps
		Bundle b = getIntent().getExtras();
		if (b != null) {
			String busStopID = b.getString("busStopID");
			if (busStopID != null) {
				launchSearchAction(busStopID);
				busStopIDEditText.setText(busStopID);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		/*
		 * case R.id.action_settings: Intent intentSettings = new
		 * Intent(MainActivity.this,AboutActivity.class);
		 * startActivity(intentSettings); return true;
		 */
		case R.id.action_about:
			Intent intentAbout = new Intent(MainActivity.this,
					AboutActivity.class);
			startActivity(intentAbout);
			return true;
		case R.id.action_favorites:
			Intent intentFavorites = new Intent(MainActivity.this,
					FavoritesActivity.class);
			startActivity(intentFavorites);
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
				stopSpinner();
				Log.e("MainActivity", "Network error!");
				return;
			}

			// Try sucking arrivals
			BusStop busStop = GTTSiteSucker.getBusStopSuckingHTML(htmlResponse);

			// Parse errors?
			if (busStop == null) {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
				stopSpinner();
				Log.e("MainActivity", "Parse error! htmlResponse: "
						+ htmlResponse);
				return;
			}

			// Remember last successfully searched busStopID
			Integer busStopID = busStop.getBusStopID();
			lastSearchedBusStopID = busStopID;

			// Retrieve passages
			BusLinePassages[] passagesBusLine = busStop.getPassagesBusLine();

			// No passages?
			if (passagesBusLine.length == 0) {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
				stopSpinner();
				Log.d("MainActivity", "No passages!");
				return;
			}

			// Hide the keyboard before showing passages
			hideKeyboard();

			String busStopName = busStop.getBusStopName();
			String busStopNameDisplay;
			if (busStopName == null) {
				busStopNameDisplay = String.valueOf(busStopID);
			} else {
				busStopNameDisplay = busStopName;
			}
			busStopNameTextView.setText(String.format(
					getString(R.string.passages), busStopNameDisplay));
			if (isFirstTime()) {
				legend.setVisibility(View.VISIBLE);
				hideHint.setVisibility(View.VISIBLE);
			}
			else {
				legend.setVisibility(View.GONE);
			}

			// Insert GTTBusStop info in the DB
			ContentValues values = new ContentValues();
			values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID, busStopID);
			values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_NAME, busStopName);
			long lastInserted = db.insertWithOnConflict(
					DBBusTo.BusStop.TABLE_NAME, null, values,
					SQLiteDatabase.CONFLICT_IGNORE);
			Log.d("MainActivity", "DBBusTo last BusStopID inserted: "
					+ lastInserted);

			// Populate the stupid ListView SimpleAdapter
			ArrayList<HashMap<String, Object>> entries = new ArrayList<HashMap<String, Object>>();
			for (BusLinePassages passageBusLine : passagesBusLine) {
				HashMap<String, Object> entry = new HashMap<String, Object>();
				String passages = passageBusLine.getTimePassagesString();
				if (passages == null) {
					passages = getString(R.string.no_passages);
				}
				entry.put("icon", passageBusLine.getBusLineName());
				entry.put("passages", passages);
				entries.add(entry);
			}

			// Show results using the stupid SimpleAdapter
			String[] from = { "icon", "passages" };
			int[] to = { R.id.busLineIcon, R.id.busLine };
			SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
					entries, R.layout.bus_line_passage_entry, from, to);
			resultsListView.setAdapter(adapter);
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

			stopSpinner();
		}
	}

	public void launchSearchAction(String busStopID) {
		if (busStopID.isEmpty()) {
			Toast.makeText(getApplicationContext(),
					R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
		} else if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			startSpinner();
			myAsyncWget.cancel(true);
			myAsyncWget = new MyAsyncWget();
			myAsyncWget.execute(GTTSiteSucker
					.arrivalTimesByLineQuery(busStopID));
		}
	}

	//hides hint
	public void hideHint (View v) {
		if (lastSearchedBusStopID != null) {
			legend.setVisibility(View.GONE);
			hideHint.setVisibility(View.GONE);
		}
    }
	public void addInFavorites(View v) {
		if (lastSearchedBusStopID != null) {
			ContentValues newValues = new ContentValues();
			newValues.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ISFAVORITE, 1);
			db.update(
					DBBusTo.BusStop.TABLE_NAME,
					newValues,
					DBBusTo.somethingEqualsInt(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID),
					new String[] { String.valueOf(lastSearchedBusStopID) });
			Toast.makeText(getApplicationContext(),
					R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
		}
	}

	public void onSearchClick(View v) {
		launchSearchAction(busStopIDEditText.getText().toString());
	}

	private void stopSpinner() {
		annoyingSpinner.setVisibility(View.INVISIBLE);
	}

	private void startSpinner() {
		annoyingSpinner.setVisibility(View.VISIBLE);
	}

	private void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		// Check if no view has focus
		View view = getCurrentFocus();
		if (view != null) {
			inputManager.hideSoftInputFromWindow(view.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}
	
	private boolean isFirstTime() {
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		boolean ranBefore = preferences.getBoolean("RanBefore", false);
		if (!ranBefore) {
			// first time
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean("RanBefore", true);
			editor.commit();
		}
		return !ranBefore;
	}
}