package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.GTTSiteSucker.PassagesBusLine;
import it.reyboz.bustorino.GTTSiteSucker.BusStop;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;

public class MainActivity extends ActionBarActivity {

	private TextView busStationName;
	private EditText busStopNumberEditText;
	private ProgressBar annoyingFedbackProgressBar;
	private DBBusTo mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		busStationName = (TextView) findViewById(R.id.busStationName);
		busStopNumberEditText = (EditText) findViewById(R.id.busStopNumberEditText);
		annoyingFedbackProgressBar = (ProgressBar) findViewById(R.id.annoyingFedbackProgress);
		annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
		
		busStopNumberEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
		        	searchClick(v);
		            return true;
		        }
		        return false;
		    }
		});

		// Gets the data repository in write mode
		mDbHelper = new DBBusTo(this);
		try {
			db = mDbHelper.getWritableDatabase();
		} catch(Exception e) {
			Log.e("bustorino", "bustorinoDB: " + e);
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

			PassagesBusLine[] passagesBusLine = busStop
					.getPassagesBusLine();

			for (PassagesBusLine passageBusLine : passagesBusLine) {
				HashMap<String, Object> personMap = new HashMap<String, Object>();
				personMap.put("icon", passageBusLine.getBusLineName());
				personMap.put("line-name",
						passageBusLine.getTimePassagesString());
				data.add(personMap);
			}

			if (passagesBusLine.length != 0) {
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
					values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_ID, busStopID);
					values.put(DBBusTo.BusStop.COLUMN_NAME_BUSSTOP_NAME, busStopName);
					long lastInserted = db.insertWithOnConflict(DBBusTo.BusStop.TABLE_NAME,
						null, values, SQLiteDatabase.CONFLICT_IGNORE);
					Log.d("bus-torino", "bustorinoDB last inserted: " + lastInserted);
				}

				// Show results
				String[] from = { "icon", "line-name" };
				int[] to = { R.id.busLineIcon, R.id.busLine };
				SimpleAdapter adapter = new SimpleAdapter(
						getApplicationContext(), data, R.layout.bus_stop_entry,
						from, to);

				ListView tpm = ((ListView) findViewById(R.id.resultsListView));
				tpm.setAdapter(adapter);
			} else {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
			}

			annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
		}
	}

	public void searchClick(View v) {
		String query = busStopNumberEditText.getText().toString();
		if (query.isEmpty()) {
			Toast.makeText(getApplicationContext(),
					R.string.insert_bus_stop_number, Toast.LENGTH_SHORT).show();
		} else if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			annoyingFedbackProgressBar.setVisibility(View.VISIBLE);
			new MyAsyncWget().execute(GTTSiteSucker
					.arrivalTimesByLineQuery(query));
		}
	}
}
