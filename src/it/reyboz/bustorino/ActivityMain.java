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

import java.io.UnsupportedEncodingException;

import it.reyboz.bustorino.lab.AsyncWget;
import it.reyboz.bustorino.lab.GTTSiteSucker;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusLine;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.MyDB;
import it.reyboz.bustorino.lab.NetworkTools;

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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;
import com.melnykov.fab.FloatingActionButton;

public class ActivityMain extends ActionBarActivity {

	private final boolean DOUBLE_SPINNER = true;
	private final boolean NORMAL_SPINNER = false;

	/*
	 * Various layout elements
	 */
	private EditText busStopSearchByIDEditText;
	private EditText busStopSearchByNameEditText;
	private TextView busStopNameTextView;
	private ProgressBar progressBar;
	private TextView howDoesItWorkTextView;
	private Button hideHintButton;
	private MenuItem actionHelpMenuItem;
	private it.reyboz.bustorino.layouts.BusListView resultsListView;
	private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton floatingActionButton;

	/*
	 * @see swipeRefreshLayout
	 */
	private Handler handler = new Handler();
	private final Runnable refreshing = new Runnable() {
		public void run() {
			runSearchTask(String.valueOf(lastSearchedBusStopID), DOUBLE_SPINNER);
		}
	};

	/**
	 * Last successfully searched bus stop ID
	 */
	private Integer lastSearchedBusStopID;

	private final boolean SEARCH_BY_ID = true;
	private final boolean SEARCH_BY_NAME = false;
	private boolean searchMode;

	private MyAsyncWget myAsyncWget;
	private MyDB mDbHelper;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		busStopSearchByIDEditText = (EditText) findViewById(R.id.busStopSearchByIDEditText);
		busStopSearchByNameEditText = (EditText) findViewById(R.id.busStopSearchByNameEditText);
		busStopNameTextView = (TextView) findViewById(R.id.busStopNameTextView);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		howDoesItWorkTextView = (TextView) findViewById(R.id.howDoesItWorkTextView);
		hideHintButton = (Button) findViewById(R.id.hideHintButton);
		resultsListView = (it.reyboz.bustorino.layouts.BusListView) findViewById(R.id.resultsListView);
		swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);
		myAsyncWget = new MyAsyncWget();

        floatingActionButton.attachToListView(resultsListView);

		// IME_ACTION_SEARCH keyboard option
		busStopSearchByIDEditText
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

		// IME_ACTION_SEARCH keyboard option
		busStopSearchByNameEditText
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

		// Get database in write mode
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

		/**
		 * Deprecated! D:
		 * 
		 * @author Marco Gagino!!!
		 * @deprecated
		 * @see https://developer.android.com/reference/android/support/v4/widget/SwipeRefreshLayout.html#setColorSchemeResources%28int...%29
		 */
		swipeRefreshLayout.setColorScheme(R.color.blue_500, R.color.orange_500);

        searchMode = SEARCH_BY_ID;
		setSearchModeBusStopID();

		// Intercept calls from other activities
		Bundle b = getIntent().getExtras();
		if (b != null) {
			String busStopID = b.getString("busStopID");
			if (busStopID != null) {
				busStopSearchByIDEditText.setText(busStopID);
				runSearchTask(busStopID, NORMAL_SPINNER);
			}
		} else {
			showKeyboard();
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
			startActivity(new Intent(ActivityMain.this, ActivityAbout.class));
			return true;
		case R.id.action_favorites:
			startActivity(new Intent(ActivityMain.this, ActivityFavorites.class));
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

			if (htmlResponse == null || exceptions != null) {
                // Network error
				NetworkTools.showNetworkError(ActivityMain.this);
				hideSpinner();
				Log.e("MainActivity", "Network error!");
				return;
			}

			// Try parsing arrivals
			BusStop[] busStops = GTTSiteSucker.getBusStopsSuckingHTML(db,
                    htmlResponse);

			if (busStops == null || busStops.length == 0) {
                // Parse errors
				Toast.makeText(getApplicationContext(),
						R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
				hideSpinner();
				Log.e("MainActivity", "Parse error! htmlResponse: "
						+ htmlResponse);
			} else if(busStops.length == 1) {
				// There is only one bus stop to show

				BusStop busStop = busStops[0];

				busStop.orderBusLinesByFirstArrival();
	
				// Remember as last successfully searched busStopID
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
	
				hideKeyboard();
	
				// Shows hints
				if (getOption("show_legend", true)) {
					showHints();
				} else {
					hideHints();
				}

				busStopNameTextView.setVisibility(View.VISIBLE);
				swipeRefreshLayout.setEnabled(true);
				swipeRefreshLayout.setVisibility(View.VISIBLE);
				resultsListView.setVisibility(View.VISIBLE);
	
                resultsListView.showBusLines(busLines, getApplicationContext());
                resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> av, View view,
                                            int i, long l) {
                        String busLine = ((TextView) av.getChildAt(i)
                                .findViewById(R.id.busLineIcon)).getText()
                                .toString();
                        Log.d("MainActivity", "Tapped busLine: " + busLine);
                    }
                });

                hideSpinner();
			} else {
				// Show a list of bus stops

				hideKeyboard();

				busStopNameTextView.setText(getString(R.string.results));
				busStopNameTextView.setVisibility(View.VISIBLE);
				swipeRefreshLayout.setEnabled(false);
				swipeRefreshLayout.setVisibility(View.VISIBLE);
				resultsListView.setVisibility(View.VISIBLE);

				// Show results
                resultsListView.showBusStops(busStops, getApplicationContext());
				resultsListView
						.setOnItemClickListener(new AdapterView.OnItemClickListener() {
							public void onItemClick(AdapterView<?> av, View view,
									int i, long l) {
								String busStopID = ((TextView) view
										.findViewById(R.id.busStopID)).getText()
										.toString();

								Log.d("MainActivity", "Tapped on bus stop ID: "
										+ busStopID);

								setSearchModeBusStopID();
								busStopSearchByIDEditText.setText(busStopID);

								runSearchTask(busStopID, NORMAL_SPINNER);
							}
						});

                hideSpinner();
			}
		}
	}

	public void onSearchClick(View v) {
		String query;
		if (searchMode == SEARCH_BY_ID) {
			query = busStopSearchByIDEditText.getText().toString();
		} else {
			query = busStopSearchByNameEditText.getText().toString();		
		}
		runSearchTask(query, NORMAL_SPINNER);
	}

	public void onHideHint(View v) {
		hideHints();
		setOption("show_legend", false);
	}

    public void onToggleKeyboardLayout(View v) {
        searchMode = !searchMode;
        if(searchMode == SEARCH_BY_ID) {
            setSearchModeBusStopID();
            if (busStopSearchByIDEditText.requestFocus()) {
                showKeyboard();
            }
        } else {
            setSearchModeBusStopName();
            if (busStopSearchByNameEditText.requestFocus()) {
                showKeyboard();
            }
        }
    }

	public void runSearchTask(String busStopQuery, boolean spinnerType) {
		if (busStopQuery.isEmpty()) {
			Toast.makeText(getApplicationContext(),
					R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT)
					.show();
			return;
		} else if (!NetworkTools.isConnected(this)) {
			NetworkTools.showNetworkError(this);
			return;
		} else {
			showSpinner(spinnerType);
			myAsyncWget.cancel(true);
			myAsyncWget = new MyAsyncWget();
			try {
				myAsyncWget.execute(GTTSiteSucker
					.busLinesByQuery(busStopQuery));
			} catch (UnsupportedEncodingException e) {
				Toast.makeText(getApplicationContext(),
						R.string.encoding_error, Toast.LENGTH_SHORT).show();
			}
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

	private void showKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(busStopSearchByNameEditText,
				InputMethodManager.SHOW_IMPLICIT);
	}

	private void hideKeyboard() {
		View view = getCurrentFocus();
		if (view != null) {
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
					.hideSoftInputFromWindow(view.getWindowToken(),
							InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	private void setSearchModeBusStopID() {
		busStopSearchByNameEditText.setVisibility(View.GONE);
		busStopSearchByIDEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.ic_keyboard_white_24dp);
	}

	private void setSearchModeBusStopName() {
		busStopSearchByIDEditText.setVisibility(View.GONE);
		busStopSearchByNameEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.ic_looks_one_white_24dp);
	}

	private void showHints() {
		howDoesItWorkTextView.setVisibility(View.VISIBLE);
		hideHintButton.setVisibility(View.VISIBLE);
		actionHelpMenuItem.setVisible(false);
	}

	private void hideHints() {
		howDoesItWorkTextView.setVisibility(View.GONE);
		hideHintButton.setVisibility(View.GONE);
		actionHelpMenuItem.setVisible(true);
	}

	private void showSpinner(boolean swipeSpinner) {
		if (swipeSpinner == DOUBLE_SPINNER) {
			swipeRefreshLayout.setRefreshing(true);
		}
		progressBar.setVisibility(View.VISIBLE);
	}

	private void hideSpinner() {
		swipeRefreshLayout.setRefreshing(false);
		progressBar.setVisibility(View.INVISIBLE);
	}
}