package it.reyboz.bustorino;

import it.reyboz.bustorino.GTTSiteSucker.ArrivalsAtBusStop;
import it.reyboz.bustorino.GTTSiteSucker.TimePassage;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

	private EditText bus_stop_number_editText;
	private TextView result_textView;
	private ProgressBar annoyingFedbackProgressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		bus_stop_number_editText = (EditText)findViewById(R.id.busStopNumberEditText);
		result_textView = (TextView)findViewById(R.id.result_textView);
		annoyingFedbackProgressBar = (ProgressBar)findViewById(R.id.annoyingFedbackProgress);
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
	 * @author boz
	 */
	public class MyAsyncWget extends AsyncWget {
		protected void onPostExecute(String result) {
			ArrivalsAtBusStop[] arrivalsAtBusStop = GTTSiteSucker.arrivalTimesBylineHTMLSucker(result);
			String out = "";
			for(ArrivalsAtBusStop arrivalAtBusStop:arrivalsAtBusStop) {
				out += String.format(getResources().getString(R.string.passages), arrivalAtBusStop.getLineaGTT()) + "\n";
				for(TimePassage timePassage:arrivalAtBusStop.getTimePassages()) {
					out += "-\t" + timePassage.getTime() + (timePassage.isInRealTime() ? "*" : "") + "\n";
				}
				
				if(arrivalAtBusStop.getTimePassages().size() == 0) {
					out += "\t :( \n";
				}

				out += "\n";
			}

			if(arrivalsAtBusStop.length == 0) {
				Toast.makeText(getApplicationContext(), R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
			}

			annoyingFedbackProgressBar.setVisibility(View.INVISIBLE);
			result_textView.setText(out);
		}
	}

	public void searchClick(View v) {
		if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
		} else {
			annoyingFedbackProgressBar.setVisibility(View.VISIBLE);
			new MyAsyncWget().execute(GTTSiteSucker.arrivalTimesByLineQuery(bus_stop_number_editText.getText().toString()));
		}
	}
}
