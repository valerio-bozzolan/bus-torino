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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
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
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.melnykov.fab.FloatingActionButton;

import java.io.UnsupportedEncodingException;
import java.util.List;

import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTScraperFetcher;
import it.reyboz.bustorino.backend.FiveTStopsFetcher;
import it.reyboz.bustorino.backend.GTTJSONFetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsFinderByName;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.MyDB;
import it.reyboz.bustorino.lab.asyncwget.AsyncWgetBusStopSuggestions;
import it.reyboz.bustorino.middleware.AsyncArrivalsFetcherAll;
import it.reyboz.bustorino.middleware.AsyncStopsFinderByName;
import it.reyboz.bustorino.middleware.PalinaAdapter;
import it.reyboz.bustorino.middleware.StopAdapter;

public class ActivityMain extends AppCompatActivity {

    private class RecursionHelper {
        private int pos = 0;
        public AsyncTask<?, ?, ?> RunningInstance = null; // the only one that hasn't been cancelled

        public void reset() {
            this.pos = 0;
            if(RunningInstance != null) {
                RunningInstance.cancel(true);
            }
        }

        public void increment() {
            pos++;
        }

        public int getPos() {
            return pos;
        }
    }

    private boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /*
     * Layout elements
     */
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private TextView busStopNameTextView;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ListView resultsListView;
    private FloatingActionButton floatingActionButton;

    /*
     * Serach mode
     */
    private static final int SEARCH_BY_NAME = 0;
    private static final int SEARCH_BY_ID = 1;
    private static final int SEARCH_BY_ROUTE = 2; // TODO: implement this (bug #1512948)
    private int searchMode;

    /*
     * Options
     */
    private final String OPTION_SHOW_LEGEND = "show_legend";

    /**
     * Last successfully searched bus stop ID
     */
    private String lastSuccessfullySearchedBusStopID = null;

    /* // useful for testing:
    public class MockFetcher implements ArrivalsFetcher {
        @Override
        public Palina ReadArrivalTimesAll(String routeID, AtomicReference<result> res) {
            SystemClock.sleep(5000);
            res.set(result.SERVER_ERROR);
            return new Palina();
        }
    }
    private ArrivalsFetcher[] ArrivalFetchers = {new MockFetcher(), new MockFetcher(), new MockFetcher(), new MockFetcher(), new MockFetcher()};*/

     /*
      * I tried to wrap everything in a class and it didn't work right no matter what.
      * Or it kinda worked, but accessing the stuff became very ugly, especially if you wanted to
      * hide the internal structure of the class.
      * At least I've managed to shoehorn two variables and a method in there, the array will stay
      * outside.
      *
      * I don't know if I should be surprised that handling recursion the C way looks a lot cleaner
      * than wrapping everything the Java way.
      */
    private ArrivalsFetcher[] ArrivalFetchers = {new GTTJSONFetcher(), new FiveTScraperFetcher()};
    private RecursionHelper ArrivalFetchersRecursionHelper = new RecursionHelper();

    private StopsFinderByName[] StopsFindersByName = {new FiveTStopsFetcher()};
    private RecursionHelper StopsFindersByNameRecursionHelper = new RecursionHelper();

    private SQLiteDatabase db;

    ///////////////////////////////// EVENT HANDLERS ///////////////////////////////////////////////

    /*
     * @see swipeRefreshLayout
     */
    private Handler handler = new Handler();
    private final Runnable refreshing = new Runnable() {
        public void run() {
            asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStopID, true);
        }
    };

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
        resultsListView = (ListView) findViewById(R.id.resultsListView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);
        if (floatingActionButton != null) {
            floatingActionButton.attachToListView(resultsListView);
        }

        busStopSearchByIDEditText.setSelectAllOnFocus(true);
        busStopSearchByIDEditText
                .setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        // IME_ACTION_SEARCH alphabetical option
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            onSearchClick(v);
                            return true;
                        }
                        return false;
                    }
                });
        busStopSearchByNameEditText
                .setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        // IME_ACTION_SEARCH alphabetical option
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            onSearchClick(v);
                            return true;
                        }
                        return false;
                    }
                });

        // Get database in write mode
        MyDB mDbHelper = new MyDB(this);
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
         * @author Marco Gagino!!!
         */
        swipeRefreshLayout.setColorSchemeColors(R.color.blue_500, R.color.orange_500); // setColorScheme is deprecated, setColorSchemeColors isn't

        setSearchModeBusStopID();

        //---------------------------- START INTENT CHECK QUEUE ------------------------------------

        // Intercept calls from URL intent
        boolean tryedFromIntent = false;

        String busStopID = null;
        Uri data = getIntent().getData();
        if (data != null) {
            busStopID = getBusStopIDFromUri(data);
            tryedFromIntent = true;
        }

        // Intercept calls from other activities
        if (!tryedFromIntent) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                busStopID = b.getString("bus-stop-ID");

                /**
                 * I'm not very sure if you are coming from an Intent.
                 * Some launchers work in strange ways.
                 */
                tryedFromIntent = busStopID != null;
            }
        }

        //---------------------------- END INTENT CHECK QUEUE --------------------------------------

        if (busStopID == null) {
            // Show keyboard if can't start from intent
            showKeyboard();

            // You haven't obtained anything... from an intent?
            if (tryedFromIntent) {

                // This shows a luser warning
                // TODO: did I accidentally remove the warning?
                ArrivalFetchersRecursionHelper.reset();
            }
        } else {
            // If you are here an intent has worked successfully
            setBusStopSearchByIDEditText(busStopID);
            asyncWgetBusStopFromBusStopID(busStopID, true);
        }

        Log.d("MainActivity", "Created");
    }

    /**
     * Reload bus stop timetable when it's fulled resumed from background.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        Log.d("ActivityMain", "onPostResume fired. Last successfully bus stop ID: " + lastSuccessfullySearchedBusStopID);
        if (searchMode == SEARCH_BY_ID && lastSuccessfullySearchedBusStopID != null && lastSuccessfullySearchedBusStopID.length() != 0) {
            setBusStopSearchByIDEditText(lastSuccessfullySearchedBusStopID);
            asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStopID, true);
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
            case android.R.id.home:
                // Respond to the action bar's Up/Home button
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_help:
                showHints();
                return true;
            case R.id.action_favorites:
                startActivity(new Intent(ActivityMain.this, ActivityFavorites.class));
                return true;
            case R.id.action_about:
                startActivity(new Intent(ActivityMain.this, ActivityAbout.class));
                return true;
            case R.id.action_news:
                openIceweasel("http://blog.reyboz.it/tag/busto/");
                return true;
            case R.id.action_bugs:
                openIceweasel("https://bugs.launchpad.net/bus-torino");
                return true;
            case R.id.action_source:
                openIceweasel("https://code.launchpad.net/bus-torino");
                return true;
            case R.id.action_licence:
                openIceweasel("http://www.gnu.org/licenses/gpl-3.0.html");
                return true;
            case R.id.action_author:
                openIceweasel("http://boz.reyboz.it?lovebusto");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * OK this is pure shit
     *
     * @param v View clicked
     */
    public void onSearchClick(View v) {
        // TODO: do we need toggleSpinner(true) here?
        if (searchMode == SEARCH_BY_ID) {
            String busStopID = busStopSearchByIDEditText.getText().toString();
            asyncWgetBusStopFromBusStopID(busStopID, true);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            asyncWgetBusStopSuggestions(query, true);
        }
    }

    /**
     * QR scan button clicked
     *
     * @param v View QRButton clicked
     */
    public void onQRButtonClick(View v) {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.initiateScan();
    }

    /**
     * Receive the Barcode Scanner Intent
     *
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        Uri uri;
        try {
            uri = Uri.parse(scanResult != null ? scanResult.getContents() : null); // this apparently prevents NullPointerException. Somehow.
        } catch (NullPointerException e) {
            Toast.makeText(getApplicationContext(),
                    R.string.no_qrcode, Toast.LENGTH_SHORT).show();
            return;
        }

        String busStopID = getBusStopIDFromUri(uri);
        busStopSearchByIDEditText.setText(busStopID);
        asyncWgetBusStopFromBusStopID(busStopID, true);
    }

    public void onHideHint(View v) {
        hideHints();
        setOption(OPTION_SHOW_LEGEND, false);
    }

    public void onToggleKeyboardLayout(View v) {
        if (searchMode == SEARCH_BY_NAME) {
            setSearchModeBusStopID();
            if (busStopSearchByIDEditText.requestFocus()) {
                showKeyboard();
            }
        } else { // searchMode == SEARCH_BY_ID
            setSearchModeBusStopName();
            if (busStopSearchByNameEditText.requestFocus()) {
                showKeyboard();
            }
        }
    }

    ///////////////////////////////// STOPS FINDER BY NAME /////////////////////////////////////////

    /**
     * See the other asyncWget method for documentation\comments.
     */
    private void asyncWgetBusStopSuggestions(String query, boolean reset) {
        toggleSpinner(true); // TODO: remove whatever else is starting the spinner before this

        if(reset) {
            StopsFindersByNameRecursionHelper.reset();
        }

        if(query == null || query.length() == 0) {
            Toast.makeText(getApplicationContext(),
                    R.string.insert_bus_stop_name_error, Toast.LENGTH_SHORT).show();
            toggleSpinner(false);
            return;
        }

        // TODO: implement offline search (it's possibile.)
//        if (!NetworkTools.isConnected(getApplicationContext())) {
//            NetworkTools.showNetworkError(getApplicationContext());
//            toggleSpinner(false);
//            return;
//        }


        if(StopsFindersByNameRecursionHelper.getPos() >= ArrivalFetchers.length) {
            // TODO: error message (tryed every fetcher and failed)
            StopsFindersByNameRecursionHelper.reset();
            toggleSpinner(false);
            return;
        }

        if(StopsFindersByNameRecursionHelper.RunningInstance != null) {
            StopsFindersByNameRecursionHelper.RunningInstance.cancel(true);
        }

        StopsFindersByNameRecursionHelper.RunningInstance = new AsyncStopsFinderByName(StopsFindersByName[StopsFindersByNameRecursionHelper.getPos()], query, new AsyncStopsFinderByNameCallbackImplementation()).execute();
    }

    private class AsyncStopsFinderByNameCallbackImplementation implements AsyncStopsFinderByName.AsyncStopsFinderByNameCallback {
        public void call(List<Stop> stops, Fetcher.result res, String query) {
            toggleSpinner(false);

            switch (res) {
                case CLIENT_OFFLINE:
                    Toast.makeText(getApplicationContext(),
                            R.string.network_error, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case SERVER_ERROR: // TODO: better error message for this case
                    if(isConnected()) {
                        Toast.makeText(getApplicationContext(),
                                R.string.parsing_error, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                R.string.network_error, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case PARSER_ERROR:
                    Toast.makeText(getApplicationContext(),
                            R.string.parsing_error, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case EMPTY_RESULT_SET:
                    Toast.makeText(getApplicationContext(),
                            R.string.no_bus_stop_have_this_name, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case OK:
                    //for (BusStop busStop : busStops) {
                    //    MyDB.DBBusStop.addBusStop(db, busStop);
                    //}

                    populateBusStopsLayout(stops);
                    toggleSpinner(false); // recursion terminated, remove spinner
                    return; // RETURN.
            }
            ArrivalFetchersRecursionHelper.increment();
            asyncWgetBusStopFromBusStopID(query, false);
        }
    }

    private void populateBusStopsLayout(List<Stop> busStops) {
        hideKeyboard();

        busStopNameTextView.setVisibility(View.GONE);

        prepareGUIForBusStops();

        resultsListView.setAdapter(new StopAdapter(this, busStops));
        resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                /**
                 * Casting because of Javamerda
                 * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                 */
                Stop busStop = (Stop) parent.getItemAtPosition(position);

                Intent intent = new Intent(ActivityMain.this, ActivityMain.class);

                Bundle b = new Bundle();
                b.putString("bus-stop-ID", busStop.ID);
                intent.putExtras(b);
                startActivity(intent);
            }
        });
    }

    ///////////////////////////////// ARRIVALS FETCHER /////////////////////////////////////////////

    /**
     * Try every fetcher until you get something usable.
     *
     * @param busStopID the ID
     * @param reset set to true if you want to live (set to false during recursion)
     */
    private void asyncWgetBusStopFromBusStopID(String busStopID, boolean reset) {
        toggleSpinner(true);

        if(reset) {
            ArrivalFetchersRecursionHelper.reset();
        }

        if(busStopID == null || busStopID.length() == 0) {
            Toast.makeText(getApplicationContext(),
                    R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            toggleSpinner(false);
            return;
        }

        if(ArrivalFetchersRecursionHelper.getPos() >= ArrivalFetchers.length) {
            // TODO: error message (tryed every fetcher and failed)
            ArrivalFetchersRecursionHelper.reset();
            toggleSpinner(false);
            return;
        }

        // cancel whatever is still running
        if(ArrivalFetchersRecursionHelper.RunningInstance != null) {
            // cancel() should hopefully only kill the background thread, not the one we're using for this same recursion...
            ArrivalFetchersRecursionHelper.RunningInstance.cancel(true);
        }

        // create new task, run it, store the running instance. Don't try to reorder these calls even though it's unreadable, trust me.
        ArrivalFetchersRecursionHelper.RunningInstance = new AsyncArrivalsFetcherAll(ArrivalFetchers[ArrivalFetchersRecursionHelper.getPos()], busStopID, new AsyncArrivalsFetcherAllCallbackImplementation()).execute();
    }

    private class AsyncArrivalsFetcherAllCallbackImplementation implements AsyncArrivalsFetcherAll.AsyncArrivalsFetcherAllCallback {
        public void call(Palina p, Fetcher.result res, String stopID) {
            switch (res) {
                case CLIENT_OFFLINE:
                    Toast.makeText(getApplicationContext(),
                            R.string.network_error, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case SERVER_ERROR: // TODO: better error message for this case
                    if(isConnected()) {
                        Toast.makeText(getApplicationContext(),
                                R.string.parsing_error, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                R.string.network_error, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case PARSER_ERROR:
                    Toast.makeText(getApplicationContext(),
                            R.string.parsing_error, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case EMPTY_RESULT_SET:
                    Toast.makeText(getApplicationContext(),
                            R.string.no_passages, Toast.LENGTH_SHORT).show();
                    break; // goto recursion
                case OK:
                    lastSuccessfullySearchedBusStopID = stopID;
                    hideKeyboard();
                    //MyDB.DBBusStop.addBusStop(db, busStop); // TODO: determine why this was needed
                    populateBusLinesLayout(p, stopID);
                    toggleSpinner(false); // recursion terminated, remove spinner
                    return; // RETURN.
            }
            // TODO: what do we do if a fetcher says no such stop/route exists? try next fetcher or give up?
            // indirect recursion through a callback. ...yeha.
            ArrivalFetchersRecursionHelper.increment();
            asyncWgetBusStopFromBusStopID(stopID, false);
        }
    }

    private void populateBusLinesLayout(Palina p, String stopID) {

         /* TODO: determine if there was a reason to store everything in the database, other than
          * showing the last timetable when "resuming" the app (which wasn't currently done, I think
          * it just fetches new timetables every time)
          */
        //BusStop dbBusStop = MyDB.DBBusStop.getBusStop(db, busStop.getBusStopID());

        // TODO: implement favorites username
        /*if(dbBusStop != null && dbBusStop.getBusStopUsername() != null) {
            busStopNameDisplay = dbBusStop.getBusStopUsername();
        } else if (busStop.getBusStopName() != null) {
            busStopNameDisplay = busStop.getBusStopName();
        } else {
            busStopNameDisplay = String.valueOf(busStopID);
        }*/

        hideKeyboard();

        busStopNameTextView.setText(String.format(
                getString(R.string.passages), p.getStopName().length() == 0 ? stopID : stopID.concat(" - ").concat(p.getStopName())));

        // keyboard is already hidden, at this point

        // Shows hints
        if(getOption(OPTION_SHOW_LEGEND, true)) {
            showHints();
        } else {
            hideHints();
        }

        prepareGUIForBusLines();

        resultsListView.setAdapter(new PalinaAdapter(this, p));

        // TODO: do something useful on click (e.g. refresh timetables and show only that route)
        /*resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                *//*
                 * Casting because of Javamerda
                 * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                 *//*
                BusLine busLine = (BusLine) parent.getItemAtPosition(position);

                Log.i("ActivityMain", "Tapped busLine " + busLine.toString());
            }
        });*/
    }

    ///////////////////////////////// OTHER STUFF //////////////////////////////////////////////////
    // TODO: try to move these 3 methods to a separate class

    public void addInFavorites(View v) {
        if (lastSuccessfullySearchedBusStopID != null) {
            BusStop busStop = new BusStop(lastSuccessfullySearchedBusStopID);
            busStop.setIsFavorite(true);
            MyDB.DBBusStop.addBusStop(db, busStop);

            BusStop dbBusStop = MyDB.DBBusStop.getBusStop(db, busStop.getBusStopID());
            if (dbBusStop == null || dbBusStop.getBusStopLocality() == null) {
                // This will also scrape the busStopLocality
                try {
                    new AsyncWgetBusStopSuggestions(busStop.getBusStopID()) {
                        @Override
                        public void onReceivedBusStopNames(BusStop[] busStops, int status) {
                            if (status == AsyncWgetBusStopSuggestions.ERROR_NONE) {
                                for (BusStop busStop : busStops) {
                                    MyDB.DBBusStop.addBusStop(db, busStop);
                                }
                            }
                        }
                    };
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

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

    ////////////////////////////////////// GUI HELPERS /////////////////////////////////////////////

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = searchMode == SEARCH_BY_ID ? busStopSearchByIDEditText : busStopSearchByNameEditText;
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
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
        searchMode = SEARCH_BY_ID;
        busStopSearchByNameEditText.setVisibility(View.GONE);
        busStopSearchByNameEditText.setText("");
        busStopSearchByIDEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.alphabetical);
    }

    private void setSearchModeBusStopName() {
        searchMode = SEARCH_BY_NAME;
        busStopSearchByIDEditText.setVisibility(View.GONE);
        busStopSearchByIDEditText.setText("");
        busStopSearchByNameEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.numeric);
    }

    /**
     * Having that cursor at the left of the edit text makes me cancer.
     * @param busStopID bus stop ID
     */
    private void setBusStopSearchByIDEditText(String busStopID) {
        busStopSearchByIDEditText.setText(busStopID);
        busStopSearchByIDEditText.setSelection(busStopID.length());
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

    private void toggleSpinner(boolean enable) {
        if (enable) {
            //already set by the RefreshListener when needed
            //swipeRefreshLayout.setRefreshing(true);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            swipeRefreshLayout.setRefreshing(false);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void prepareGUIForBusLines() {
        busStopNameTextView.setVisibility(View.VISIBLE);
        busStopNameTextView.setClickable(true);
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        resultsListView.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        busStopNameTextView.setText(getString(R.string.results));
        busStopNameTextView.setClickable(false);
        busStopNameTextView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        resultsListView.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(false);
    }

    /**
     * Open an URL in the default browser.
     *
     * @param url URL
     */
    public void openIceweasel(String url) {
        Intent browserIntent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent1);
    }

    ///////////////////// INTENT HELPER ////////////////////////////////////////////////////////////

    /**
     * Try to extract the bus stop ID from a URi
     *
     * @param uri The URL
     * @return bus stop ID or null
     */
    public static String getBusStopIDFromUri(Uri uri) {
        String busStopID;
        switch (uri.getHost()) {
            case "m.gtt.to.it":
                // http://m.gtt.to.it/m/it/arrivi.jsp?n=1254
                busStopID = uri.getQueryParameter("n");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?n from: " + uri);
                }
                break;
            case "www.gtt.to.it":
            case "gtt.to.it":
                // http://www.gtt.to.it/cms/percorari/arrivi?palina=1254
                busStopID = uri.getQueryParameter("palina");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?palina from: " + uri);
                }
                break;
            default:
                Log.e("ActivityMain", "Unexpected intent URL: " + uri);
                busStopID = null;
        }
        return busStopID;
    }
}