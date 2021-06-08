package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;

import java.util.Map;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.FiveTAPIFetcher;
import it.reyboz.bustorino.backend.FiveTScraperFetcher;
import it.reyboz.bustorino.backend.FiveTStopsFetcher;
import it.reyboz.bustorino.backend.GTTJSONFetcher;
import it.reyboz.bustorino.backend.GTTStopsFetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.StopsFinderByName;
import it.reyboz.bustorino.middleware.AppLocationManager;
import it.reyboz.bustorino.middleware.AsyncDataDownload;
import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.Permissions;

import static it.reyboz.bustorino.util.Permissions.LOCATION_PERMISSIONS;
import static it.reyboz.bustorino.util.Permissions.LOCATION_PERMISSION_GIVEN;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainScreenFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainScreenFragment extends BaseFragment implements  FragmentListenerMain{


    private static final String OPTION_SHOW_LEGEND = "show_legend";
    private static final String SAVED_FRAGMENT="saved_fragment";

    private static final String DEBUG_TAG = "BusTO - MainFragment";

    public final static String FRAGMENT_TAG = "MainScreenFragment";

    /// UI ELEMENTS //
    private ImageButton addToFavorites;
    private FragmentHelper fragmentHelper;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem;
    private FloatingActionButton floatingActionButton;

    private boolean setupOnAttached = true;
    private boolean suppressArrivalsReload = false;
    //private Snackbar snackbar;
    /*
     * Search mode
     */
    private static final int SEARCH_BY_NAME = 0;
    private static final int SEARCH_BY_ID = 1;
    private static final int SEARCH_BY_ROUTE = 2; // TODO: implement this -- https://gitpull.it/T12
    private int searchMode;
    //private ImageButton addToFavorites;
    private final ArrivalsFetcher[] arrivalsFetchers = new ArrivalsFetcher[]{new FiveTAPIFetcher(), new GTTJSONFetcher(), new FiveTScraperFetcher()};
    //// HIDDEN BUT IMPORTANT ELEMENTS ////
    FragmentManager fragMan;
    Handler mainHandler;
    private final Runnable refreshStop = new Runnable() {
        public void run() {
            if (fragMan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
                ArrivalsFragment fragment = (ArrivalsFragment) fragMan.findFragmentById(R.id.resultFrame);
                if (fragment == null){
                    //we create a new fragment, which is WRONG
                    new AsyncDataDownload(fragmentHelper, arrivalsFetchers,getContext()).execute();
                } else{
                    String stopName = fragment.getStopID();

                    new AsyncDataDownload(fragmentHelper, fragment.getCurrentFetchersAsArray(), getContext()).execute(stopName);
                }
            } else //we create a new fragment, which is WRONG
                new AsyncDataDownload(fragmentHelper, arrivalsFetchers, getContext()).execute();
        }
    };

    /// LOCATION STUFF ///
    boolean pendingNearbyStopsRequest = false;
    boolean locationPermissionGranted, locationPermissionAsked = false;
    AppLocationManager locationManager;


    private final LocationCriteria cr = new LocationCriteria(2000, 10000);
    //Location
    private AppLocationManager.LocationRequester requester = new AppLocationManager.LocationRequester() {
        @Override
        public void onLocationChanged(Location loc) {

        }

        @Override
        public void onLocationStatusChanged(int status) {

            if(status == AppLocationManager.LOCATION_GPS_AVAILABLE && !isNearbyFragmentShown()){
                //request Stops
                pendingNearbyStopsRequest = false;

                mainHandler.post(new NearbyStopsRequester(getContext(), cr));
            }
        }

        @Override
        public long getLastUpdateTimeMillis() {
            return 50;
        }

        @Override
        public LocationCriteria getLocationCriteria() {
            return cr;
        }

        @Override
        public void onLocationProviderAvailable() {
            //Log.w(DEBUG_TAG, "pendingNearbyStopRequest: "+pendingNearbyStopsRequest);
            if(!isNearbyFragmentShown()){
                pendingNearbyStopsRequest = false;
                mainHandler.post(new NearbyStopsRequester(getContext(), cr));
            }
        }

        @Override
        public void onLocationDisabled() {

        }
    };
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    if(result==null || result.get(Manifest.permission.ACCESS_COARSE_LOCATION) == null
                    ||result.get(Manifest.permission.ACCESS_FINE_LOCATION) ) return;

                    if(result.get(Manifest.permission.ACCESS_COARSE_LOCATION) && result.get(Manifest.permission.ACCESS_FINE_LOCATION)){
                        locationPermissionGranted = true;
                        Log.w(DEBUG_TAG, "Starting position");
                        if (mListener!= null && getContext()!=null){
                            if (locationManager==null)
                                locationManager = AppLocationManager.getInstance(getContext());
                            locationManager.addLocationRequestFor(requester);
                        }
                    }
                }
            });


    //// ACTIVITY ATTACHED (LISTENER ///
    private CommonFragmentListener mListener;

    private String pendingStopID = null;

    public MainScreenFragment() {
        // Required empty public constructor
    }


    public static MainScreenFragment newInstance() {
        MainScreenFragment fragment = new MainScreenFragment();
        Bundle args = new Bundle();
        //args.putString(ARG_PARAM1, param1);
        //args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            //do nothing
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_main_screen, container, false);
        addToFavorites = (ImageButton) root.findViewById(R.id.addToFavorites);
        busStopSearchByIDEditText = root.findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = root.findViewById(R.id.busStopSearchByNameEditText);
        progressBar = root.findViewById(R.id.progressBar);
        howDoesItWorkTextView = root.findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = root.findViewById(R.id.hideHintButton);
        swipeRefreshLayout = root.findViewById(R.id.listRefreshLayout);
        floatingActionButton = root.findViewById(R.id.floatingActionButton);
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

        swipeRefreshLayout
                .setOnRefreshListener(() -> mainHandler.post(refreshStop));
        swipeRefreshLayout.setColorSchemeResources(R.color.blue_500, R.color.orange_500);

        floatingActionButton.setOnClickListener((this::onToggleKeyboardLayout));
        hideHintButton.setOnClickListener(this::onHideHint);

        AppCompatImageButton qrButton = root.findViewById(R.id.QRButton);
        qrButton.setOnClickListener(this::onQRButtonClick);

        AppCompatImageButton searchButton = root.findViewById(R.id.searchButton);
        searchButton.setOnClickListener(this::onSearchClick);

        // Fragment stuff
        fragMan = getChildFragmentManager();
        fragMan.addOnBackStackChangedListener(() -> Log.d("BusTO Main Fragment", "BACK STACK CHANGED"));

        fragmentHelper = new FragmentHelper(this, getChildFragmentManager(), getContext(), R.id.resultFrame);
        setSearchModeBusStopID();


        cr.setAccuracy(Criteria.ACCURACY_FINE);
        cr.setAltitudeRequired(false);
        cr.setBearingRequired(false);
        cr.setCostAllowed(true);
        cr.setPowerRequirement(Criteria.NO_REQUIREMENT);

       locationManager = AppLocationManager.getInstance(getContext());

        Log.d(DEBUG_TAG, "OnCreateView, savedInstanceState null: "+(savedInstanceState==null));



        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(DEBUG_TAG, "onViewCreated, SwipeRefreshLayout visible: "+(swipeRefreshLayout.getVisibility()==View.VISIBLE));
        Log.d(DEBUG_TAG, "Setup on attached: "+setupOnAttached);
        //Restore instance state
        if (savedInstanceState!=null){
            Fragment fragment = getChildFragmentManager().getFragment(savedInstanceState, SAVED_FRAGMENT);
            if (fragment!=null){
                getChildFragmentManager().beginTransaction().add(R.id.resultFrame, fragment).commit();
                setupOnAttached = false;
            }
        }
        if (getChildFragmentManager().findFragmentById(R.id.resultFrame)!= null){
            swipeRefreshLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
        if (fragment!=null)
        getChildFragmentManager().putFragment(outState, SAVED_FRAGMENT, fragment);
    }

    public void setSuppressArrivalsReload(boolean value){
       suppressArrivalsReload = value;
        // we have to suppress the reloading of the (possible) ArrivalsFragment
        /*if(value) {
            Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
            if (fragment instanceof ArrivalsFragment) {
                ArrivalsFragment frag = (ArrivalsFragment) fragment;
                frag.setReloadOnResume(false);
            }
        }

         */
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.d(DEBUG_TAG, "OnAttach called, setupOnAttach: "+setupOnAttached);
        mainHandler = new Handler();
        if (context instanceof CommonFragmentListener) {
            mListener = (CommonFragmentListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement CommonFragmentListener");
        }
        if (setupOnAttached) {
            if (pendingStopID==null)
            //We want the nearby bus stops!
            mainHandler.post(new NearbyStopsRequester(context, cr));
            else{
                ///TODO: if there is a stop displayed, we need to hold the update
            }

            setupOnAttached = false;
        }


    }
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    //    setupOnAttached = true;
    }


    @Override
    public void onResume() {

        final Context con = getContext();
        Log.w(DEBUG_TAG, "OnResume called");
        if (con != null) {
            if(locationManager==null)
                locationManager = AppLocationManager.getInstance(con);

            if(Permissions.locationPermissionGranted(con)){
                Log.d(DEBUG_TAG, "Location permission OK");
                if(!locationManager.isRequesterRegistered(requester))
                    locationManager.addLocationRequestFor(requester);
            } else if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
                //we have already asked for the location, and we should show an explanation in order
                // to ask again (TODO)
                //do nothing
            } else{
                //request permission
                requestPermissionLauncher.launch(Permissions.LOCATION_PERMISSIONS);
            }
        }
        else {
            Log.w(DEBUG_TAG, "Context is null at onResume");
        }
        super.onResume();
        // if we have a pending stopID request, do it
        Log.d(DEBUG_TAG, "Pending stop ID for arrivals: "+pendingStopID);
        //this is the second time we are attaching this fragment
        Log.d(DEBUG_TAG, "Waiting for new stop request: "+ suppressArrivalsReload);
        if (suppressArrivalsReload){
            // we have to suppress the reloading of the (possible) ArrivalsFragment
            Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
            if (fragment instanceof ArrivalsFragment){
                ArrivalsFragment frag = (ArrivalsFragment) fragment;
                frag.setReloadOnResume(false);
            }
            suppressArrivalsReload = false;
        }
        if(pendingStopID!=null){
            requestArrivalsForStopID(pendingStopID);
            pendingStopID = null;
        }
        mListener.readyGUIfor(FragmentKind.MAIN_SCREEN_FRAGMENT);
    }

    @Override
    public void onPause() {
        //mainHandler = null;
        locationManager.removeLocationRequestFor(requester);
        super.onPause();
    }

    /*
    GUI METHODS
     */
    /**
     * QR scan button clicked
     *
     * @param v View QRButton clicked
     */
    public void onQRButtonClick(View v) {
        IntentIntegrator integrator = new IntentIntegrator(getActivity());
        integrator.initiateScan();
    }
    public void onHideHint(View v) {

        hideHints();
        setOption(OPTION_SHOW_LEGEND, false);
    }
    /**
     * OK this is pure shit
     *
     * @param v View clicked
     */
    public void onSearchClick(View v) {
        final StopsFinderByName[] stopsFinderByNames = new StopsFinderByName[]{new GTTStopsFetcher(), new FiveTStopsFetcher()};
        if (searchMode == SEARCH_BY_ID) {
            String busStopID = busStopSearchByIDEditText.getText().toString();
            requestArrivalsForStopID(busStopID);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            //new asyncWgetBusStopSuggestions(query, stopsDB, StopsFindersByNameRecursionHelper);
            new AsyncDataDownload(fragmentHelper, stopsFinderByNames, getContext()).execute(query);
        }
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
    @Override
    public void enableRefreshLayout(boolean yes) {
        swipeRefreshLayout.setEnabled(yes);
    }

    ////////////////////////////////////// GUI HELPERS /////////////////////////////////////////////
    public void showKeyboard() {
        if(getActivity() == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
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
    protected boolean isNearbyFragmentShown(){
        Fragment fragment = getChildFragmentManager().findFragmentByTag(NearbyStopsFragment.FRAGMENT_TAG);
        return (fragment!= null && fragment.isVisible());
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
        //actionHelpMenuItem.setVisible(false);
    }

    private void hideHints() {
        howDoesItWorkTextView.setVisibility(View.GONE);
        hideHintButton.setVisibility(View.GONE);
        //actionHelpMenuItem.setVisible(true);
    }

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
        //actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        //actionHelpMenuItem.setVisible(false);
    }

    void showNearbyStopsFragment(){
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        NearbyStopsFragment fragment = NearbyStopsFragment.newInstance(NearbyStopsFragment.TYPE_STOPS);
        Fragment oldFrag = fragMan.findFragmentById(R.id.resultFrame);
        FragmentTransaction ft = fragMan.beginTransaction();
        if (oldFrag != null)
            ft.remove(oldFrag);
        ft.add(R.id.resultFrame, fragment, NearbyStopsFragment.FRAGMENT_TAG);
        ft.commit();
    }


    @Override
    public void showFloatingActionButton(boolean yes) {
        mListener.showFloatingActionButton(yes);
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
            locationManager.removeLocationRequestFor(requester);
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
                    Log.d(DEBUG_TAG, "Fragment type is unknown");
                    return;
            }
        // Shows hints


    }

    /**
     * Main method for stops requests
     * @param ID the Stop ID
     */
    @Override
    public void requestArrivalsForStopID(String ID) {
        if (!isResumed()){
            //defer request
            pendingStopID = ID;
            Log.d(DEBUG_TAG, "Deferring update for stop "+ID);
            return;
        }
        final boolean delayedRequest = !(pendingStopID==null);
        final FragmentManager framan = getChildFragmentManager();
        if (getContext()==null){
            Log.e(DEBUG_TAG, "Asked for arrivals with null context");
            return;
        }
        if (ID == null || ID.length() <= 0) {
            // we're still in UI thread, no need to mess with Progress
            showToastMessage(R.string.insert_bus_stop_number_error, true);
            toggleSpinner(false);
        } else  if (framan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
            ArrivalsFragment fragment = (ArrivalsFragment) framan.findFragmentById(R.id.resultFrame);
            if (fragment != null && fragment.getStopID() != null && fragment.getStopID().equals(ID)){
                // Run with previous fetchers
                //fragment.getCurrentFetchers().toArray()
                new AsyncDataDownload(fragmentHelper,fragment.getCurrentFetchersAsArray(), getContext()).execute(ID);
            } else{
                new AsyncDataDownload(fragmentHelper, arrivalsFetchers, getContext()).execute(ID);
            }
        }
        else {
            new AsyncDataDownload(fragmentHelper,arrivalsFetchers, getContext()).execute(ID);
            Log.d(DEBUG_TAG, "Started search for arrivals of stop " + ID);
        }
    }
    /////////// LOCATION METHODS //////////

    /*
    private void startStopRequest(String provider) {
        Log.d(DEBUG_TAG, "Provider " + provider + " got enabled");
        if (locmgr != null && mainHandler != null && pendingNearbyStopsRequest && locmgr.getProvider(provider).meetsCriteria(cr)) {

        }
    }

     */

    /**
     * Run location requests separately and asynchronously
     */
    class NearbyStopsRequester implements Runnable {
        Context appContext;
        Criteria cr;

        public NearbyStopsRequester(Context appContext, Criteria criteria) {
            this.appContext = appContext.getApplicationContext();
            this.cr = criteria;
        }

        @Override
        public void run() {
            if(isNearbyFragmentShown()) {
                //nothing to do
                Log.w(DEBUG_TAG, "launched nearby fragment request but we already are showing");
                return;
            }

            final boolean isOldVersion = Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
            final boolean noPermission = ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

            //if we don't have the permission, we have to ask for it, if we haven't
            // asked too many times before
            if (noPermission) {
                if (!isOldVersion) {
                    pendingNearbyStopsRequest = true;
                    //Permissions.assertLocationPermissions(appContext,getActivity());
                    requestPermissionLauncher.launch(LOCATION_PERMISSIONS);
                    Log.w(DEBUG_TAG, "Cannot get position: Asking permission, noPositionFromSys: " + noPermission);
                    return;
                } else {
                    Toast.makeText(appContext, "Asked for permission position too many times", Toast.LENGTH_LONG).show();
                }
            } else setOption(LOCATION_PERMISSION_GIVEN, true);

            AppLocationManager appLocationManager = AppLocationManager.getInstance(appContext);
            final boolean haveProviders = appLocationManager.anyLocationProviderMatchesCriteria(cr);
            if (haveProviders
                    && fragmentHelper.getLastSuccessfullySearchedBusStop() == null
                    && !fragMan.isDestroyed()) {
                //Go ahead with the request
                Log.d("mainActivity", "Recreating stop fragment");
                showNearbyStopsFragment();
                pendingNearbyStopsRequest = false;
            } else if(!haveProviders){
                Log.e(DEBUG_TAG, "NO PROVIDERS FOR POSITION");
            }

        }
    }

}