package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.GTTSiteSucker.ArrivalsAtBusStop;
import it.reyboz.bustorino.GTTSiteSucker.BusStop;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

	private TextView busStationName;
	private EditText bus_stop_number_editText;
	private ProgressBar annoyingFedbackProgressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		busStationName = (TextView) findViewById(R.id.busStationName);
		bus_stop_number_editText = (EditText) findViewById(R.id.busStopNumberEditText);
		annoyingFedbackProgressBar = (ProgressBar) findViewById(R.id.annoyingFedbackProgress);
		annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
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
		int id = item.getItemId();
		if (id == R.id.action_settings) {
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

			BusStop busStop = GTTSiteSucker.arrivalTimesBylineHTMLSucker(result);
			
			ArrivalsAtBusStop[] arrivalsAtBusStop = busStop.getArrivalsAtBusStop();

			for (ArrivalsAtBusStop arrivalAtBusStop : arrivalsAtBusStop) {
				HashMap<String, Object> personMap = new HashMap<String, Object>();
				personMap.put("icon", arrivalAtBusStop.getLineaGTT());
				personMap.put("line-name", arrivalAtBusStop.getTimePassagesString());
				data.add(personMap);
			}

			if (arrivalsAtBusStop.length == 0) {
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
			} else {
				busStationName.setText(String.format(getResources().getString(R.string.passages), busStop.getStationName()));
			}

			String[] from = { "icon", "line-name" };
			int[] to = { R.id.busLineIcon, R.id.busLine };
			SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(),
					data,
					R.layout.bus_stop_entry,
					from, to);

			ListView tpm = ((ListView) findViewById(R.id.listView1));
			tpm.setAdapter(adapter);

			annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
		}
	}

	public void searchClick(View v) {
		if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			annoyingFedbackProgressBar.setVisibility(View.VISIBLE);
			new MyAsyncWget().execute(GTTSiteSucker
					.arrivalTimesByLineQuery(bus_stop_number_editText.getText()
							.toString()));
		}
	}
}
