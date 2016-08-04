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
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.*;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.melnykov.fab.FloatingActionButton;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTScraperFetcher;
import it.reyboz.bustorino.backend.FiveTStopsFetcher;
import it.reyboz.bustorino.backend.GTTJSONFetcher;
import it.reyboz.bustorino.backend.GTTStopsFetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsFinderByName;
import it.reyboz.bustorino.fragments.ResultListFragment;
import it.reyboz.bustorino.middleware.*;

public class ActivityMain extends AppCompatActivity implements ResultListFragment.OnFragmentInteractionListener{

    /*
     * Layout elements
     */
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton floatingActionButton;
    private FragmentManager framan;

    /*
     * Search mode
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
    public @Nullable Stop lastSuccessfullySearchedBusStop = null;

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

    private RecursionHelper<ArrivalsFetcher> ArrivalFetchersRecursionHelper = new RecursionHelper<>(new ArrivalsFetcher[] {new GTTJSONFetcher(), new FiveTScraperFetcher()});
    private RecursionHelper<StopsFinderByName> StopsFindersByNameRecursionHelper = new RecursionHelper<>(new StopsFinderByName[] {new GTTStopsFetcher(), new FiveTStopsFetcher()});

    private StopsDB stopsDB;
    private UserDB userDB;

    ///////////////////////////////// EVENT HANDLERS ///////////////////////////////////////////////

    /*
     * @see swipeRefreshLayout
     */
    private Handler handler = new Handler();
    private final Runnable refreshing = new Runnable() {
        public void run() {
            if (lastSuccessfullySearchedBusStop == null) {
                Toast.makeText(getApplicationContext(),
                        R.string.query_too_short, Toast.LENGTH_SHORT).show();
            } else {
                new asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStop.ID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
            }
        }
    };

    //// MAIN METHOD ///

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        framan = getSupportFragmentManager();
        this.stopsDB = new StopsDB(getApplicationContext());
        this.userDB = new UserDB(getApplicationContext());
        setContentView(R.layout.activity_main);
        busStopSearchByIDEditText = (EditText) findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = (EditText) findViewById(R.id.busStopSearchByNameEditText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        howDoesItWorkTextView = (TextView) findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = (Button) findViewById(R.id.hideHintButton);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.listRefreshLayout);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);

        framan.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                Log.d("MainActivity, BusTO", "BACK STACK CHANGED");
            }
        });

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
        String busStopDisplayName = null;
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
                busStopDisplayName = b.getString("bus-stop-display-name");

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
                ArrivalFetchersRecursionHelper.reset();
                Toast.makeText(getApplicationContext(),
                        R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            // If you are here an intent has worked successfully
            setBusStopSearchByIDEditText(busStopID);
            this.lastSuccessfullySearchedBusStop = new Stop(busStopID);
            // forcing it as user name even though it could be standard name, it doesn't really matter
            this.lastSuccessfullySearchedBusStop.setStopUserName(busStopDisplayName);
            new asyncWgetBusStopFromBusStopID(busStopID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
        }

        Log.d("MainActivity", "Created");
    }

    /**
     * Reload bus stop timetable when it's fulled resumed from background.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        Log.d("ActivityMain", "onPostResume fired. Last successfully bus stop ID: " + lastSuccessfullySearchedBusStop);
        if (searchMode == SEARCH_BY_ID && lastSuccessfullySearchedBusStop != null) {
            setBusStopSearchByIDEditText(lastSuccessfullySearchedBusStop.ID);
            new asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStop.ID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
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
        if (searchMode == SEARCH_BY_ID) {
            String busStopID = busStopSearchByIDEditText.getText().toString();
            new asyncWgetBusStopFromBusStopID(busStopID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            new asyncWgetBusStopSuggestions(query, stopsDB, StopsFindersByNameRecursionHelper);
        }
    }

    /**
     * @author Fabio Mazza
     * This is necessary for the fragment to update the StopDB
     * @return the last succeffuly searched bus stop, if not null
     */
    public Stop getLastSuccessfullySearchedBusStop() {
        if(lastSuccessfullySearchedBusStop!=null)
        return lastSuccessfullySearchedBusStop;
        else return null;
    }

    @Override
    public void createFragmentForStop(String ID) {
        new asyncWgetBusStopFromBusStopID(ID, ArrivalFetchersRecursionHelper,lastSuccessfullySearchedBusStop);
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
        new asyncWgetBusStopFromBusStopID(busStopID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
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



    /*
    *****************************************************************************************
    ****                            STOP FINDER BY NAME                                  ****
    *****************************************************************************************
    */

    private class asyncWgetBusStopSuggestions extends AsyncWget<StopsFinderByName> {
        private final String query;
        private AtomicReference<Fetcher.result> res;
        private List<Stop> stopList;
        private StopsDB db;
        String fragTag;

        public asyncWgetBusStopSuggestions(@Nullable String query, @NonNull StopsDB sdb, @NonNull RecursionHelper<StopsFinderByName> r) {
            super(getApplicationContext(), r);

            toggleSpinner(true);
            this.query = query;
            this.res = new AtomicReference<>();
            this.db = sdb;

            r.reset();

            if(query == null || query.length() <= 0) {
                Toast.makeText(this.c,
                        R.string.insert_bus_stop_name_error, Toast.LENGTH_SHORT).show();
                toggleSpinner(false);
            } else {
                fragTag = "stop_search_"+query;
                this.execute();
            }
        }

        @Override
        protected boolean tryFetcher(StopsFinderByName f) {
            // gets opened multiple times, whatever.
            this.db.openIfNeeded();
            this.stopList = f.FindByName(this.query, this.db, this.res);
            this.db.closeIfNeeded();

            switch (this.res.get()) {
                case CLIENT_OFFLINE:
                    publishProgress(R.string.network_error);
                    return false;
                case SERVER_ERROR:
                    if (isConnected()) {
                        publishProgress(R.string.parsing_error);
                    } else {
                        publishProgress(R.string.network_error);
                    }
                    return false;
                case PARSER_ERROR:
                default:
                    publishProgress(R.string.internal_error);
                    return false;
                case QUERY_TOO_SHORT:
                    publishProgress(R.string.query_too_short);
                    return false;
                case EMPTY_RESULT_SET:
                    publishProgress(R.string.no_bus_stop_have_this_name);
                    return false;
                case OK:
                    return true;
            }
        }

        @Override
        protected void onPostExecute(Void useless) {
            // something really bad happened
            if(this.isCancelled() || !this.terminated || this.stopList == null) {
                toggleSpinner(false);
                this.onCancelled();

                return;
            }

            // no results
            if(this.failedAll) {
                toggleSpinner(false);
                // TODO: an error message would be a good idea, here
                return;
            }

            hideKeyboard();

            //CREATE THE FRAGMENT
            FragmentTransaction transaction = framan.beginTransaction();
            ResultListFragment listfragment = ResultListFragment.newInstance(ResultListFragment.TYPE_STOPS);
            transaction.replace(R.id.resultFrame,listfragment, fragTag);
            transaction.addToBackStack(fragTag);
            transaction.commit();
            //Apparently, the fragment creation is asynchronous, but we need the fragment, NOW!
            framan.executePendingTransactions();

            prepareGUIForBusStops();

            listfragment.setTextViewMessage(getString(R.string.results));
            listfragment.setListAdapter(new StopAdapter(this.c, this.stopList));
            listfragment.attachFABToListView();

            toggleSpinner(false);
        }

        @Override
        protected void onCancelled() {
            toggleSpinner(false);
        }
    }

    /*
    *****************************************************************************************
    ****                            ARRIVALS FETCHER                                     ****
    *****************************************************************************************
    */

    private class asyncWgetBusStopFromBusStopID extends AsyncWget<ArrivalsFetcher> {
        private final String stopID;
        private AtomicReference<Fetcher.result> res;
        private Palina p;
        String fragmentTag;
        /**
         * Begins its life as a copy of lastSuccessfullySearchedBusStop, ends as null if it needs
         * replacing or stays the same if it stayed the same
         */
        private Stop lastSearchedBusStop;

        asyncWgetBusStopFromBusStopID(@Nullable String stopID, @NonNull RecursionHelper<ArrivalsFetcher> r, @Nullable Stop lastSearchedBusStop) {
            super(getApplicationContext(), r);

            toggleSpinner(true);
            this.stopID = stopID;
            this.lastSearchedBusStop = lastSearchedBusStop;
            this.res = new AtomicReference<>();

            if(stopID == null || stopID.length() <= 0) {
                // we're still in UI thread, no need to mess with Progress
                Toast.makeText(this.c,
                        R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
                toggleSpinner(false);
            } else {
                //In case the query works, the fragment tag is the stop ID
                fragmentTag = stopID;
                this.execute();
            }

        }

        @Override
        protected boolean tryFetcher(ArrivalsFetcher fetcher) {
            this.p = fetcher.ReadArrivalTimesAll(this.stopID, this.res);

            switch(this.res.get()) {
                case CLIENT_OFFLINE:
                    publishProgress(R.string.network_error);
                    return false;
                case SERVER_ERROR:
                    if(isConnected()) {
                        publishProgress(R.string.parsing_error);
                    } else {
                        publishProgress(R.string.network_error);
                    }
                    return false;
                case QUERY_TOO_SHORT: // should never happen here, but to err on the side of caution...
                    publishProgress(R.string.query_too_short);
                    return false;
                case PARSER_ERROR:
                default: // there are no other cases but Android Studio is still complaining...
                    publishProgress(R.string.internal_error);
                    return false;
                case EMPTY_RESULT_SET:
                    publishProgress(R.string.no_passages);
                    return false;
                case OK:
                    // WARNING: lots of branches ahead.

                    // did we search for anything?
                    if(this.lastSearchedBusStop != null) {
                        // check if we have the same stop
                        if(!this.lastSearchedBusStop.ID.equals(p.ID)) {
                            // remove it, get new name
                            this.lastSearchedBusStop = null;
                            getNameOrGetRekt();
                        } else {
                            // searched and it's the same
                            String sn = lastSearchedBusStop.getStopDisplayName();
                            if(sn == null) {
                                // something really bad happened, start from scratch
                                this.lastSearchedBusStop = null;
                                getNameOrGetRekt();
                            } else {
                                // "merge" Stop over Palina and we're good to go
                                this.p.mergeNameFrom(lastSearchedBusStop);
                            }
                        }
                    } else {
                        // we haven't searched anything yet
                        getNameOrGetRekt();
                    }
                    return true;
            }
        }

        /**
         * Run this in a background thread.<br>
         * Sets a stop name for this.palina, guaranteed not to be null!
         */
        private void getNameOrGetRekt() {
            String nameMaybe;
            SQLiteDatabase udb = userDB.getReadableDatabase();

            // does it already have a name (for fetchers that support it, or already got from favorites)?
            nameMaybe = this.p.getStopDisplayName();
            if(nameMaybe != null && nameMaybe.length() > 0) {
                return;
            }

            // ok, let's search favorites.

            String usernameMaybe = UserDB.getStopUserName(udb, this.p.ID);
            if(usernameMaybe != null && usernameMaybe.length() > 0) {
                this.p.setStopUserName(usernameMaybe);
                return;
            }


            // let's try StopsDB, then.

            StopsDB db = new StopsDB(this.c);

            db.openIfNeeded();
            nameMaybe = db.getNameFromID(this.p.ID);
            db.closeIfNeeded();

            if(nameMaybe != null && nameMaybe.length() > 0) {
                this.p.setStopName(nameMaybe);
                return;
            }

            // no name to be found anywhere, don't bother searching it next time
            this.p.setStopName("");
        }

        @Override
        protected void onPostExecute(Void useless) {
            // something really bad happened
            if(this.isCancelled() || !this.terminated || this.p == null) {
                toggleSpinner(false);
                this.onCancelled();
                return;
            }

            // no results
            if(this.failedAll) {
                toggleSpinner(false);
                // TODO: an error message would be a good idea, here
                return;
            }

            hideKeyboard();

            //DO THE FRAGMENT TRANSACTION
            FragmentTransaction transaction = framan.beginTransaction();
            ResultListFragment listfragment = ResultListFragment.newInstance(ResultListFragment.TYPE_LINES);
            transaction.replace(R.id.resultFrame,listfragment, fragmentTag);
            transaction.addToBackStack(fragmentTag);
            transaction.commit();
            //Apparently, the fragment creation is asynchronous, but we need the fragment, NOW!
            framan.executePendingTransactions();

            if(this.lastSearchedBusStop == null) {
                // did we gather any new information about this Stop? Then update it (casting from Palina)
                lastSuccessfullySearchedBusStop = this.p;
            }

            String displayName = p.getStopDisplayName();
            String displayStuff;

            if (displayName != null && displayName.length() > 0) {
                displayStuff = p.ID.concat(" - ").concat(displayName);
            } else {
                displayStuff = p.ID;
            }
            listfragment.setTextViewMessage(String.format(
                    getString(R.string.passages), displayStuff));

            // Shows hints
            if (getOption(OPTION_SHOW_LEGEND, true)) {
                showHints();
            } //else {
              //  hideHints();
            //}

            prepareGUIForBusLines();

            listfragment.setListAdapter(new PalinaAdapter(this.c, p));
            listfragment.attachFABToListView();

            toggleSpinner(false);
        }

        @Override
        protected void onCancelled() {
            toggleSpinner(false);
        }
    }

    ///////////////////////////////// OTHER STUFF //////////////////////////////////////////////////
    private boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * This method is not really necessary anymore...
     */
    @Deprecated
    public void addToFavorites(View v) {
        if(lastSuccessfullySearchedBusStop != null) {
            new AsyncAddToFavorites(this).execute(lastSuccessfullySearchedBusStop);
        }
    }


    // TODO: try to move these 2 methods to a separate class

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
    //TODO: toggle spinner from mainActivity
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
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
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

        // everithing catches fire when passing null to a switch.
        String host = uri.getHost();
        if(host == null) {
            Log.e("ActivityMain", "Not an URL: " + uri);
            return null;
        }

        switch(host) {
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