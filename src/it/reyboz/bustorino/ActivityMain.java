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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.snackbar.Snackbar;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.data.DBUpdateWorker;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.data.UserDB;
import it.reyboz.bustorino.fragments.*;
import it.reyboz.bustorino.middleware.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ActivityMain extends GeneralActivity implements FragmentListenerMain {

    /*
     * Layout elements
     */
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem, experimentsMenuItem;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton floatingActionButton;
    private FragmentManager framan;
    private Snackbar snackbar;

    /*
     * Search mode
     */
    private static final int SEARCH_BY_NAME = 0;
    private static final int SEARCH_BY_ID = 1;
    private static final int SEARCH_BY_ROUTE = 2; // TODO: implement this -- https://gitpull.it/T12
    private int searchMode;
    private ImageButton addToFavorites;

    /*
     * Options
     */
    private final String OPTION_SHOW_LEGEND = "show_legend";

    private static final String DEBUG_TAG = "BusTO - MainActivity";
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
    private ArrivalsFetcher[] arrivalsFetchers = new ArrivalsFetcher[]{new FiveTAPIFetcher(), new GTTJSONFetcher(), new FiveTScraperFetcher()};
    private StopsFinderByName[] stopsFinderByNames = new StopsFinderByName[]{new GTTStopsFetcher(), new FiveTStopsFetcher()};
    /*
     * Position
     */
    //Fine location criteria
    private final Criteria cr = new Criteria();
    private boolean pendingNearbyStopsRequest = false;
    private LocationManager locmgr;
    private FragmentHelper fh;

    ///////////////////////////////// EVENT HANDLERS ///////////////////////////////////////////////

    /*
     * @see swipeRefreshLayout
     */
    private final Handler theHandler = new Handler();
    private final Runnable refreshing = new Runnable() {
        public void run() {
            if (framan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
                ArrivalsFragment fragment = (ArrivalsFragment) framan.findFragmentById(R.id.resultFrame);
                String stopName = fragment.getStopID();

                new AsyncDataDownload(fh, fragment.getCurrentFetchersAsArray()).execute(stopName);
            } else //we create a new fragment, which is WRONG
                new AsyncDataDownload(fh, arrivalsFetchers).execute();
        }
    };


    //// MAIN METHOD ///

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        framan = getSupportFragmentManager();
        final SharedPreferences theShPr = getMainSharedPreferences();
        /*
         * UI
         */
        setContentView(R.layout.activity_main);
        Toolbar defToolbar = findViewById(R.id.that_toolbar);
        setSupportActionBar(defToolbar);
        busStopSearchByIDEditText = findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = findViewById(R.id.busStopSearchByNameEditText);
        progressBar = findViewById(R.id.progressBar);
        howDoesItWorkTextView = findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = findViewById(R.id.hideHintButton);
        swipeRefreshLayout = findViewById(R.id.listRefreshLayout);
        floatingActionButton = findViewById(R.id.floatingActionButton);

        framan.addOnBackStackChangedListener(() -> Log.d("MainActivity, BusTO", "BACK STACK CHANGED"));

        busStopSearchByIDEditText.setSelectAllOnFocus(true);
        busStopSearchByIDEditText
                .setOnEditorActionListener((v, actionId, event) -> {
                    // IME_ACTION_SEARCH alphabetical option
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        onSearchClick(v);
                        return true;
                    }
                    return false;
                });
        busStopSearchByNameEditText
                .setOnEditorActionListener((v, actionId, event) -> {
                    // IME_ACTION_SEARCH alphabetical option
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        onSearchClick(v);
                        return true;
                    }
                    return false;
                });

        // Called when the layout is pulled down
        swipeRefreshLayout
                .setOnRefreshListener(() -> theHandler.post(refreshing));

        /**
         * @author Marco Gagino!!!
         */
        //swipeRefreshLayout.setColorSchemeColors(R.color.blue_500, R.color.orange_500); // setColorScheme is deprecated, setColorSchemeColors isn't
        swipeRefreshLayout.setColorSchemeResources(R.color.blue_500, R.color.orange_500);
        fh = new FragmentHelper(this, R.id.listRefreshLayout, R.id.resultFrame);
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
            // JUST DON'T
            // showKeyboard();

            // You haven't obtained anything... from an intent?
            if (tryedFromIntent) {

                // This shows a luser warning
                Toast.makeText(getApplicationContext(),
                        R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            // If you are here an intent has worked successfully
            setBusStopSearchByIDEditText(busStopID);
            /*
            //THIS PART SHOULDN'T BE NECESSARY SINCE THE LAST SUCCESSFULLY SEARCHED BUS
            // STOP IS ADDED AUTOMATICALLY
            Stop nextStop = new Stop(busStopID);
            // forcing it as user name even though it could be standard name, it doesn't really matter
            nextStop.setStopUserName(busStopDisplayName);
            //set stop as last succe
            fh.setLastSuccessfullySearchedBusStop(nextStop);
            */
            requestArrivalsForStopID(busStopID);
        }
        //Try (hopefully) database update
        //TODO: Check if service shows the notification
        //Old code for the db update
        //DatabaseUpdateService.startDBUpdate(getApplicationContext());


        PeriodicWorkRequest wr = new PeriodicWorkRequest.Builder(DBUpdateWorker.class, 1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        final WorkManager workManager = WorkManager.getInstance(this);

        final int version = theShPr.getInt(DatabaseUpdate.DB_VERSION_KEY, -10);
        if (version >= 0)
        workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                ExistingPeriodicWorkPolicy.KEEP, wr);
        else workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, wr);
        /*
        Set database update
         */
        workManager.getWorkInfosForUniqueWorkLiveData(DBUpdateWorker.DEBUG_TAG)
                .observe(this, workInfoList -> {
                    // If there are no matching work info, do nothing
                    if (workInfoList == null || workInfoList.isEmpty()) {
                        return;
                    }
                    Log.d(DEBUG_TAG, "WorkerInfo: "+workInfoList);

                    boolean showProgress = false;
                    for (WorkInfo workInfo : workInfoList) {
                        if (workInfo.getState() == WorkInfo.State.RUNNING) {
                            showProgress = true;
                        }
                    }

                    if (showProgress) {
                        createDefaultSnackbar();
                    } else {
                        if(snackbar!=null) {
                            snackbar.dismiss();
                            snackbar = null;
                        }
                    }

                });



        //locationHandler = new GPSLocationAdapter(getApplicationContext());
        //--------- NEARBY STOPS--------//
        //SETUP LOCATION
        locmgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        cr.setAccuracy(Criteria.ACCURACY_FINE);
        cr.setAltitudeRequired(false);
        cr.setBearingRequired(false);
        cr.setCostAllowed(true);
        cr.setPowerRequirement(Criteria.NO_REQUIREMENT);
        //We want the nearby bus stops!
        theHandler.post(new NearbyStopsRequester());
        //If there are no providers available, then, wait for them


        Log.d("MainActivity", "Created");

    }

    /*
     * Reload bus stop timetable when it's fulled resumed from background.

     * @Override protected void onPostResume() {
     * super.onPostResume();
     * Log.d("ActivityMain", "onPostResume fired. Last successfully bus stop ID: " + fh.getLastSuccessfullySearchedBusStop());
     * if (searchMode == SEARCH_BY_ID && fh.getLastSuccessfullySearchedBusStop() != null) {
     * setBusStopSearchByIDEditText(fh.getLastSuccessfullySearchedBusStop().ID);
     * new AsyncDataDownload(AsyncDataDownload.RequestType.ARRIVALS,fh).execute();
     * } else {
     * //we have new activity or we don't have a new searched stop.
     * //Let's search stops nearby
     * LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
     * Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.resultFrame);
     * <p>
     * <p>
     * }
     * //show the FAB since it remains hidden
     * floatingActionButton.show();
     * <p>
     * }
     **/


    @Override
    protected void onPause() {
        super.onPause();
        fh.stopLastRequestIfNeeded();
        fh.setBlockAllActivities(true);
        locmgr.removeUpdates(locListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fh.setBlockAllActivities(false);
        //TODO: check if current LiveData-bound observer works
        if (pendingNearbyStopsRequest)
            theHandler.post(new NearbyStopsRequester());
        ActionBar bar = getSupportActionBar();
        if(bar!=null) bar.show();
        else Log.w(DEBUG_TAG, "ACTION BAR IS NULL");

        //check if we can display the experiments or not
        SharedPreferences shPr = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean exper_On = shPr.getBoolean(getString(R.string.pref_key_experimental), false);
        //Log.w(DEBUG_TAG, "Preference experimental is "+exper_On);
        //MenuItem experimentsItem =
        if (experimentsMenuItem != null)
            experimentsMenuItem.setVisible(exper_On);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        actionHelpMenuItem = menu.findItem(R.id.action_help);
        experimentsMenuItem = menu.findItem(R.id.action_experiments);
        SharedPreferences shPr = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean exper_On = shPr.getBoolean(getString(R.string.pref_key_experimental), false);
        experimentsMenuItem.setVisible(exper_On);
        return true;
    }

    /**
     * Callback fired when a MenuItem is selected
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Resources res = getResources();
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
            case R.id.action_map:
                //ensure storage permission is granted
                final String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                int result = askForPermissionIfNeeded(permission, STORAGE_PERMISSION_REQ);
                switch (result) {
                    case PERMISSION_OK:
                        startActivity(new Intent(ActivityMain.this, ActivityMap.class));
                        break;
                    case PERMISSION_ASKING:
                        permissionDoneRunnables.put(permission,
                                () -> startActivity(new Intent(ActivityMain.this, ActivityMap.class)));
                        break;
                    case PERMISSION_NEG_CANNOT_ASK:
                        String storage_perm = res.getString(R.string.storage_permission);
                        String text = res.getString(R.string.too_many_permission_asks,  storage_perm);
                        Toast.makeText(getApplicationContext(),text, Toast.LENGTH_LONG).show();
                }
                return true;

            case R.id.action_about:
                startActivity(new Intent(ActivityMain.this, ActivityAbout.class));
                return true;
            case R.id.action_hack:
                openIceweasel(res.getString(R.string.hack_url));
                return true;
            case R.id.action_source:
                openIceweasel("https://gitpull.it/source/libre-busto/");
                return true;
            case R.id.action_licence:
                openIceweasel("https://www.gnu.org/licenses/gpl-3.0.html");
                return true;
            case R.id.action_settings:
                Log.d("MAINBusTO", "Pressed button preferences");
                startActivity(new Intent(ActivityMain.this, ActivitySettings.class));
                return true;
            case R.id.action_experiments:
                startActivity(new Intent(this, ActivityPrincipal.class));
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
            requestArrivalsForStopID(busStopID);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            //new asyncWgetBusStopSuggestions(query, stopsDB, StopsFindersByNameRecursionHelper);
            new AsyncDataDownload(fh, stopsFinderByNames).execute(query);
        }
    }

    /**
     * PERMISSION STUFF
     **/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_POSITION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setOption(LOCATION_PERMISSION_GIVEN, true);
                    //if we sent a request for a new NearbyStopsFragment
                    if (pendingNearbyStopsRequest) {
                        pendingNearbyStopsRequest = false;
                        theHandler.post(new NearbyStopsRequester());
                    }

                } else {
                    //permission denied
                    setOption(LOCATION_PERMISSION_GIVEN, false);
                }
                //add other cases for permissions
                break;
            case STORAGE_PERMISSION_REQ:
                final String storageKey = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(DEBUG_TAG, "Permissions check: " + Arrays.toString(permissions));

                    if (permissionDoneRunnables.containsKey(storageKey)) {
                        Runnable toRun = permissionDoneRunnables.get(storageKey);
                        if (toRun != null)
                            toRun.run();
                        permissionDoneRunnables.remove(storageKey);
                    }
                } else {
                    //permission denied
                    showToastMessage(R.string.permission_storage_maps_msg, false);
                    /*final int canGetPermission = askForPermissionIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, STORAGE_PERMISSION_REQ);
                    switch (canGetPermission) {
                        case PERMISSION_ASKING:

                            break;
                        case PERMISSION_NEG_CANNOT_ASK:
                            permissionDoneRunnables.remove(storageKey);
                            showToastMessage(R.string.closing_act_crash_msg, false);
                    }*/
                }
        }

    }


    @Override
    public void requestArrivalsForStopID(String ID) {
        if (ID == null || ID.length() <= 0) {
            // we're still in UI thread, no need to mess with Progress
            showToastMessage(R.string.insert_bus_stop_number_error, true);
            toggleSpinner(false);
        } else  if (framan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
            ArrivalsFragment fragment = (ArrivalsFragment) framan.findFragmentById(R.id.resultFrame);
            if (fragment !=null && fragment.getStopID() != null && fragment.getStopID().equals(ID)){
                // Run with previous fetchers
                //fragment.getCurrentFetchers().toArray()
                new AsyncDataDownload(fh,fragment.getCurrentFetchersAsArray()).execute(ID);
            } else{
                new AsyncDataDownload(fh, arrivalsFetchers).execute(ID);
            }
        }
        else {
            new AsyncDataDownload(fh,arrivalsFetchers).execute(ID);
            Log.d("MainActiv", "Started search for arrivals of stop " + ID);
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
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
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
        requestArrivalsForStopID(busStopID);
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

    private void createDefaultSnackbar() {
        if (snackbar == null) {
            snackbar = Snackbar.make(findViewById(R.id.searchButton), R.string.database_update_message, Snackbar.LENGTH_INDEFINITE);
        }
        snackbar.show();
    }
    ///////////////////////////////// POSITION STUFF//////////////////////////////////////////////

    private void resolveStopRequest(String provider) {
        Log.d(DEBUG_TAG, "Provider " + provider + " got enabled");
        if (locmgr != null && pendingNearbyStopsRequest && locmgr.getProvider(provider).meetsCriteria(cr)) {
            pendingNearbyStopsRequest = false;
            theHandler.post(new NearbyStopsRequester());
        }
    }

    final LocationListener locListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(DEBUG_TAG, "Location changed");
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(DEBUG_TAG, "Location provider status: " + status);
            if (status == LocationProvider.AVAILABLE) {
                resolveStopRequest(provider);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            resolveStopRequest(provider);
        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    /**
     * Run location requests separately and asynchronously
     */
    class NearbyStopsRequester implements Runnable {
        @Override
        public void run() {
            final boolean canRunPosition = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || getOption(LOCATION_PERMISSION_GIVEN, false);
            final boolean noPermission = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

            //if we don't have the permission, we have to ask for it, if we haven't
            // asked too many times before
            if (noPermission) {
                if (!canRunPosition) {
                    pendingNearbyStopsRequest = true;
                    assertLocationPermissions();
                    Log.w(DEBUG_TAG, "Cannot get position: Asking permission, noPositionFromSys: " + noPermission);
                    return;
                } else {
                    Toast.makeText(getApplicationContext(), "Asked for permission position too many times", Toast.LENGTH_LONG).show();
                }
            } else setOption(LOCATION_PERMISSION_GIVEN, true);

            LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locManager == null) {
                Log.e(DEBUG_TAG, "location manager is nihil, cannot create NearbyStopsFragment");
                return;
            }
            if (anyLocationProviderMatchesCriteria(locManager, cr, true)
                    && fh.getLastSuccessfullySearchedBusStop() == null
                    && !framan.isDestroyed()) {
                //Go ahead with the request
                Log.d("mainActivity", "Recreating stop fragment");
                swipeRefreshLayout.setVisibility(View.VISIBLE);
                NearbyStopsFragment fragment = NearbyStopsFragment.newInstance(NearbyStopsFragment.TYPE_STOPS);
                Fragment oldFrag = framan.findFragmentById(R.id.resultFrame);
                FragmentTransaction ft = framan.beginTransaction();
                if (oldFrag != null)
                    ft.remove(oldFrag);
                ft.add(R.id.resultFrame, fragment, "nearbyStop_correct");
                ft.commit();
                framan.executePendingTransactions();
                pendingNearbyStopsRequest = false;
            } else if (!anyLocationProviderMatchesCriteria(locManager, cr, true)) {
                //Wait for the providers
                Log.d(DEBUG_TAG, "Queuing position request");
                pendingNearbyStopsRequest = true;

                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0.1f, locListener);
            }

        }
    }

    private boolean anyLocationProviderMatchesCriteria(LocationManager mng, Criteria cr, boolean enabled) {
        List<String> providers = mng.getProviders(cr, enabled);
        Log.d(DEBUG_TAG, "Getting enabled location providers: ");
        for (String s : providers) {
            Log.d(DEBUG_TAG, "Provider " + s);
        }
        return providers.size() > 0;
    }


    ///////////////////////////////// OTHER STUFF //////////////////////////////////////////////////
    /**
     * Check if the last Bus Stop is in the favorites
     *
     * @return
     */
    public boolean isStopInFavorites(String busStopId) {
        boolean found = false;

        // no stop no party
        if (busStopId != null) {
            SQLiteDatabase userDB = new UserDB(getApplicationContext()).getReadableDatabase();
            found = UserDB.isStopInFavorites(userDB, busStopId);
        }

        return found;
    }

    /**
     * Add the last Stop to favorites
     */


    @Override
    public void showFloatingActionButton(boolean yes) {
        if (yes) floatingActionButton.show();
        else floatingActionButton.hide();
    }

    @Override
    public void enableRefreshLayout(boolean yes) {
        swipeRefreshLayout.setEnabled(yes);
    }

    ////////////////////////////////////// GUI HELPERS /////////////////////////////////////////////
    public void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = searchMode == SEARCH_BY_ID ? busStopSearchByIDEditText : busStopSearchByNameEditText;
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
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
     *
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
    @Override
    public void toggleSpinner(boolean enable) {
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
     * This provides a temporary fix to make the transition
     * to a single asynctask go smoother
     *
     * @param fragmentType the type of fragment created
     */
    @Override
    public void readyGUIfor(FragmentKind fragmentType) {
        hideKeyboard();
        //if we are getting results, already, stop waiting for nearbyStops
        if (pendingNearbyStopsRequest && (fragmentType == FragmentKind.ARRIVALS || fragmentType == FragmentKind.STOPS)) {
            locmgr.removeUpdates(locListener);
            pendingNearbyStopsRequest = false;
        }
        if (fragmentType == null) Log.e("ActivityMain", "Problem with fragmentType");
        else
            switch (fragmentType) {
                case ARRIVALS:
                    prepareGUIForBusLines();
                    if (getOption(OPTION_SHOW_LEGEND, true)) {
                        showHints();
                    }
                    break;
                case STOPS:
                    prepareGUIForBusStops();
                    break;
                default:
                    Log.e("BusTO Activity", "Called readyGUI with unsupported type of Fragment");
                    return;
            }
        // Shows hints

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
        if (host == null) {
            Log.e("ActivityMain", "Not an URL: " + uri);
            return null;
        }

        switch (host) {
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