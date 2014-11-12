package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.GTTSiteSucker.PassagesBusLine;
import it.reyboz.bustorino.GTTSiteSucker.BusStop;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;

public class MainActivity extends ActionBarActivity {

	private MyAsyncWget myAsyncWget;
	private TextView busStationName;
	private EditText busStopNumberEditText;
	private ProgressBar annoyingFedbackProgressBar;
	private Integer lastSearchedBusStopID;
	private DBBusTo mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		busStationName = (TextView) findViewById(R.id.busStationName);
		busStopNumberEditText = (EditText) findViewById(R.id.busStopNumberEditText);
		annoyingFedbackProgressBar = (ProgressBar) findViewById(R.id.annoyingFedbackProgress);
		annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
		myAsyncWget = new MyAsyncWget();

		busStopNumberEditText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId,
							KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_SEARCH) {
							searchClick(v);
							return true;
						}
						return false;
					}
				});

		// Gets the data repository in write mode
		mDbHelper = new DBBusTo(this);
		db = mDbHelper.getWritableDatabase();

		// Intercept calls from other part of the apps
		Bundle b = getIntent().getExtras();
		if(b != null) {
			String busStopID = b.getString("busStopID");
			if(busStopID != null) {
				launchSearchAction(busStopID);
				busStopNumberEditText.setText(busStopID);
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
		protected void onPostExecute(String result) {
			ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();

			BusStop busStop = GTTSiteSucker
					.arrivalTimesBylineHTMLSucker(result);

			// Remember that the user searched this at last
			lastSearchedBusStopID = busStop.getBusStopID();

			PassagesBusLine[] passagesBusLine = busStop.getPassagesBusLine();

			for (PassagesBusLine passageBusLine : passagesBusLine) {
				HashMap<String, Object> singleEntry = new HashMap<String, Object>();
				singleEntry.put("icon", passageBusLine.getBusLineName());
				singleEntry.put("line-name",
						passageBusLine.getTimePassagesString());
				data.add(singleEntry);
			}

			if (passagesBusLine.length != 0) {
				// Hide the keyboard before showing results
				hideKeyboard();

				Integer busStopID = busStop.getBusStopID();

				String busStopName = busStop.getBusStopName();
				String busStopNameDisplay = "---";
				if (busStopName == null && busStopID != null) {
					busStopNameDisplay = String.valueOf((busStopID));
				} else {
					busStopNameDisplay = busStopName;
				}
				busStationName.setText(String.format(
						getResources().getString(R.string.passages),
						busStopNameDisplay));

				// Insert the GTTBusStop in the DB
				if (busStopID != null) {
					ContentValues values = new ContentValues();
					values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID,
							busStopID);
					values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_NAME,
							busStopName);
					long lastInserted = db.insertWithOnConflict(
							DBBusTo.BusStop.TABLE_NAME, null, values,
							SQLiteDatabase.CONFLICT_IGNORE);
					Log.d("bus-torino", "bustorinoDB last BusStop inserted: "
							+ lastInserted);
				}

				// Show results
				String[] from = { "icon", "line-name" };
				int[] to = { R.id.busLineIcon, R.id.busLine };
				SimpleAdapter adapter = new SimpleAdapter(
						getApplicationContext(), data, R.layout.bus_line_passage_entry,
						from, to);

				ListView tpm = (ListView) findViewById(R.id.resultsListView);
				tpm.setAdapter(adapter);
				tpm.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					public void onItemClick(AdapterView<?> av, View view,
							int i, long l) {
						View child = av.getChildAt(i);
						TextView busLineIcon = (TextView) child
								.findViewById(R.id.busLineIcon);
						String busLine = busLineIcon.getText().toString();
						Log.d("bus-torino", "bustorino tapped on busline: " + busLine);
						//Toast.makeText(MainActivity.this,
						//		"myPos " + i + " " + busLineIcon.getText(),
						//		Toast.LENGTH_LONG).show();
					}
				});
			} else {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
			}

			annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
		}
	}

	public void searchClick(View v) {
		String busStopID = busStopNumberEditText.getText().toString();
		launchSearchAction(busStopID);
	}

	public void launchSearchAction(String busStopID) {
		if (busStopID.isEmpty()) {
			Toast.makeText(getApplicationContext(),
					R.string.insert_bus_stop_number, Toast.LENGTH_SHORT).show();
		} else if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			annoyingFedbackProgressBar.setVisibility(View.VISIBLE);
			myAsyncWget.cancel(true);
			myAsyncWget = new MyAsyncWget();
			myAsyncWget.execute(GTTSiteSucker.arrivalTimesByLineQuery(busStopID));
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

	private void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) this
				.getSystemService(Context.INPUT_METHOD_SERVICE);

		// Check if no view has focus:
		View view = this.getCurrentFocus();
		if (view != null) {
			inputManager.hideSoftInputFromWindow(view.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}
	/*
	 * private void spamThePoorUser() { AlertDialog.Builder alert = new
	 * AlertDialog.Builder(this); alert.setTitle(R.string.app_name);
	 * alert.setMessage(R.string.app_description);
	 * alert.setNegativeButton(android.R.string.ok, new
	 * DialogInterface.OnClickListener() { public void onClick(DialogInterface
	 * dialog, int whichButton) { } });
	 * 
	 * alert.show(); }
	 */
}
