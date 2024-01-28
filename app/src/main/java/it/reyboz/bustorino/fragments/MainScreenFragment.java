
package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Map;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.data.PreferencesHolder;
import it.reyboz.bustorino.middleware.AppLocationManager;
import it.reyboz.bustorino.middleware.AsyncArrivalsSearcher;
import it.reyboz.bustorino.middleware.AsyncStopsSearcher;
import it.reyboz.bustorino.middleware.BarcodeScanContract;
import it.reyboz.bustorino.middleware.BarcodeScanOptions;
import it.reyboz.bustorino.middleware.BarcodeScanUtils;
import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.Permissions;
import org.jetbrains.annotations.NotNull;

import static it.reyboz.bustorino.backend.utils.getBusStopIDFromUri;
import static it.reyboz.bustorino.util.Permissions.LOCATION_PERMISSIONS;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainScreenFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainScreenFragment extends ScreenBaseFragment implements  FragmentListenerMain{


    private static final String SAVED_FRAGMENT="saved_fragment";

    private static final String DEBUG_TAG = "BusTO - MainFragment";

    public static final String PENDING_STOP_SEARCH="PendingStopSearch";

    public final static String FRAGMENT_TAG = "MainScreenFragment";

    private FragmentHelper fragmentHelper;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private ProgressBar progressBar;

    private MenuItem actionHelpMenuItem;
    private FloatingActionButton floatingActionButton;
    private FrameLayout resultFrameLayout;

    private boolean setupOnStart = true;
    private boolean suppressArrivalsReload = false;
    //private Snackbar snackbar;
    /*
     * Search mode
     */
    private static final int SEARCH_BY_NAME = 0;
    private static final int SEARCH_BY_ID = 1;
    //private static final int SEARCH_BY_ROUTE = 2; // implement this -- DONE!
    private int searchMode;
    //private ImageButton addToFavorites;
    //// HIDDEN BUT IMPORTANT ELEMENTS ////
    FragmentManager childFragMan;
    Handler mainHandler;
    private final Runnable refreshStop = new Runnable() {
        public void run() {
            if(getContext() == null) return;
            List<ArrivalsFetcher> fetcherList = utils.getDefaultArrivalsFetchers(getContext());
            ArrivalsFetcher[] arrivalsFetchers = new ArrivalsFetcher[fetcherList.size()];
            arrivalsFetchers = fetcherList.toArray(arrivalsFetchers);

            if (childFragMan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
                ArrivalsFragment fragment = (ArrivalsFragment) childFragMan.findFragmentById(R.id.resultFrame);
                if (fragment == null){
                    //we create a new fragment, which is WRONG
                    Log.e("BusTO-RefreshStop", "Asking for refresh when there is no fragment");
                    // AsyncDataDownload(fragmentHelper, arrivalsFetchers,getContext()).execute();
                } else{
                    String stopName = fragment.getStopID();

                    new AsyncArrivalsSearcher(fragmentHelper, fragment.getCurrentFetchersAsArray(), getContext()).execute(stopName);
                }
            } else //we create a new fragment, which is WRONG
                new AsyncArrivalsSearcher(fragmentHelper, arrivalsFetchers, getContext()).execute();
        }
    };
    //
    private final ActivityResultLauncher<BarcodeScanOptions> barcodeLauncher = registerForActivityResult(new BarcodeScanContract(),
            result -> {
                if(result!=null && result.getContents()!=null) {
                    //Toast.makeText(MyActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                    Uri uri;
                    try {
                        uri = Uri.parse(result.getContents()); // this apparently prevents NullPointerException. Somehow.
                    } catch (NullPointerException e) {
                        if (getContext()!=null)
                            Toast.makeText(getContext().getApplicationContext(),
                                R.string.no_qrcode, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String busStopID = getBusStopIDFromUri(uri);
                    busStopSearchByIDEditText.setText(busStopID);
                    requestArrivalsForStopID(busStopID);

                } else {
                    //Toast.makeText(MyActivity.this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
                    if (getContext()!=null)
                        Toast.makeText(getContext().getApplicationContext(),
                            R.string.no_qrcode, Toast.LENGTH_SHORT).show();



                }
            });

    /// LOCATION STUFF ///
    boolean pendingIntroRun = false;
    boolean pendingNearbyStopsFragmentRequest = false;
    boolean locationPermissionGranted, locationPermissionAsked = false;
    AppLocationManager locationManager;
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    if(result==null) return;

                    if(result.get(Manifest.permission.ACCESS_COARSE_LOCATION) == null ||
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION) == null)
                        return;

                    Log.d(DEBUG_TAG, "Permissions for location are: "+result);
                    if(Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))){
                        locationPermissionGranted = true;
                        Log.w(DEBUG_TAG, "Starting position");
                        if (mListener!= null && getContext()!=null){
                            if (locationManager==null)
                                locationManager = AppLocationManager.getInstance(getContext());
                            locationManager.addLocationRequestFor(requester);
                        }
                        // show nearby fragment
                        //showNearbyStopsFragment();
                        Log.d(DEBUG_TAG, "We have location permission");
                        if(pendingNearbyStopsFragmentRequest){
                            showNearbyFragmentIfPossible();
                            pendingNearbyStopsFragmentRequest = false;
                        }
                    }
                    if(pendingNearbyStopsFragmentRequest) pendingNearbyStopsFragmentRequest =false;
                }
            });


    private final LocationCriteria cr = new LocationCriteria(2000, 10000);
    //Location
    private AppLocationManager.LocationRequester requester = new AppLocationManager.LocationRequester() {
        @Override
        public void onLocationChanged(Location loc) {

        }

        @Override
        public void onLocationStatusChanged(int status) {

            if(status == AppLocationManager.LOCATION_GPS_AVAILABLE && !isNearbyFragmentShown() && checkLocationPermission()){
                //request Stops
                //pendingNearbyStopsRequest = false;
                if (getContext()!= null && !isNearbyFragmentShown())
                    //mainHandler.post(new NearbyStopsRequester(getContext(), cr));
                    showNearbyFragmentIfPossible();
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
            if(!isNearbyFragmentShown() && getContext()!=null){
                // we should have the location permission
                if(!checkLocationPermission())
                    Log.e(DEBUG_TAG, "Asking to show nearbystopfragment when " +
                            "we have no location permission");
                pendingNearbyStopsFragmentRequest = true;
                //mainHandler.post(new NearbyStopsRequester(getContext(), cr));
                showNearbyFragmentIfPossible();
            }
        }

        @Override
        public void onLocationDisabled() {

        }
    };



    //// ACTIVITY ATTACHED (LISTENER ///
    private CommonFragmentListener mListener;

    private String pendingStopID = null;
    private CoordinatorLayout coordLayout;

    public MainScreenFragment() {
        // Required empty public constructor
    }


    public static MainScreenFragment newInstance() {
        return new MainScreenFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            //do nothing
            Log.d(DEBUG_TAG, "ARGS ARE NOT NULL: "+getArguments());
            if (getArguments().getString(PENDING_STOP_SEARCH)!=null)
                pendingStopID = getArguments().getString(PENDING_STOP_SEARCH);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_main_screen, container, false);
        /// UI ELEMENTS //
        busStopSearchByIDEditText = root.findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = root.findViewById(R.id.busStopSearchByNameEditText);
        progressBar = root.findViewById(R.id.progressBar);

        swipeRefreshLayout = root.findViewById(R.id.listRefreshLayout);
        floatingActionButton = root.findViewById(R.id.floatingActionButton);
        resultFrameLayout = root.findViewById(R.id.resultFrame);
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

        coordLayout = root.findViewById(R.id.coord_layout);

        floatingActionButton.setOnClickListener((this::onToggleKeyboardLayout));

        AppCompatImageButton qrButton = root.findViewById(R.id.QRButton);
        qrButton.setOnClickListener(this::onQRButtonClick);

        AppCompatImageButton searchButton = root.findViewById(R.id.searchButton);
        searchButton.setOnClickListener(this::onSearchClick);

        // Fragment stuff
        childFragMan = getChildFragmentManager();
        childFragMan.addOnBackStackChangedListener(() -> Log.d("BusTO Main Fragment", "BACK STACK CHANGED"));

        fragmentHelper = new FragmentHelper(this, getChildFragmentManager(), getContext(), R.id.resultFrame);
        setSearchModeBusStopID();



        cr.setAccuracy(Criteria.ACCURACY_FINE);
        cr.setAltitudeRequired(false);
        cr.setBearingRequired(false);
        cr.setCostAllowed(true);
        cr.setPowerRequirement(Criteria.NO_REQUIREMENT);

        locationManager = AppLocationManager.getInstance(requireContext());

        Log.d(DEBUG_TAG, "OnCreateView, savedInstanceState null: "+(savedInstanceState==null));


        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(DEBUG_TAG, "onViewCreated, SwipeRefreshLayout visible: "+(swipeRefreshLayout.getVisibility()==View.VISIBLE));
        Log.d(DEBUG_TAG, "Saved instance state is: "+savedInstanceState);
        //Restore instance state
        /*if (savedInstanceState!=null){
            Fragment fragment = getChildFragmentManager().getFragment(savedInstanceState, SAVED_FRAGMENT);
            if (fragment!=null){
                getChildFragmentManager().beginTransaction().add(R.id.resultFrame, fragment).commit();
                setupOnStart = false;
            }
        }

         */
        if (getChildFragmentManager().findFragmentById(R.id.resultFrame)!= null){
            swipeRefreshLayout.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(DEBUG_TAG, "Saving instance state");
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
        if (fragment!=null)
            getChildFragmentManager().putFragment(outState, SAVED_FRAGMENT, fragment);
        if (fragmentHelper!=null) fragmentHelper.setBlockAllActivities(true);

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


    /**
     * Cancel the reload of the arrival times
     * because we are going to pop the fragment
     */
    public void cancelReloadArrivalsIfNeeded(){
        if(getContext()==null) return; //we are not attached

        //Fragment fr = getChildFragmentManager().findFragmentById(R.id.resultFrame);
        fragmentHelper.stopLastRequestIfNeeded(true);
        toggleSpinner(false);
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Log.d(DEBUG_TAG, "OnAttach called, setupOnAttach: "+ setupOnStart);
        mainHandler = new Handler();
        if (context instanceof CommonFragmentListener) {
            mListener = (CommonFragmentListener) context;
        } else {
            throw new RuntimeException(context
                    + " must implement CommonFragmentListener");
        }

    }
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    //    setupOnAttached = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(DEBUG_TAG, "onStart called, setupOnStart: "+setupOnStart);
        if (setupOnStart) {
            if (pendingStopID==null){

                if(PreferencesHolder.hasIntroFinishedOneShot(requireContext())){
                    Log.d(DEBUG_TAG, "Showing nearby stops");
                    if(!checkLocationPermission()){
                        requestLocationPermission();
                        pendingNearbyStopsFragmentRequest = true;
                    }
                    else {
                        showNearbyFragmentIfPossible();
                    }
                } else {
                    //The Introductory Activity is about to be started, hence pause the request and show later
                    pendingIntroRun = true;
                }

            }
            else{
                ///TODO: if there is a stop displayed, we need to hold the update
            }

            setupOnStart = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final Context con = requireContext();
        Log.w(DEBUG_TAG, "OnResume called, setupOnStart: "+ setupOnStart);
        if (locationManager == null)
            locationManager = AppLocationManager.getInstance(con);
        //recheck the introduction activity has been run
        if(pendingIntroRun && PreferencesHolder.hasIntroFinishedOneShot(con)){
            //request position permission if needed
            if(!checkLocationPermission()){
                requestLocationPermission();
                pendingNearbyStopsFragmentRequest = true;
            }
            else {
                showNearbyFragmentIfPossible();
            }
            //deactivate flag
            pendingIntroRun = false;
        }
        if(Permissions.bothLocationPermissionsGranted(con)){
            Log.d(DEBUG_TAG, "Location permission OK");
            if(!locationManager.isRequesterRegistered(requester))
                locationManager.addLocationRequestFor(requester);
        } //don't request permission
        // if we have a pending stopID request, do it
        Log.d(DEBUG_TAG, "Pending stop ID for arrivals: "+pendingStopID);
        //this is the second time we are attaching this fragment ->
        Log.d(DEBUG_TAG, "Waiting for new stop request: "+ suppressArrivalsReload);
        //TODO: if we come back to this from another fragment, and the user has given again the permission
        // for the Location, we should show the Nearby Stops
        if(!suppressArrivalsReload && pendingStopID==null){
            //none of the following cases are true
            // check if we are showing any fragment
            final Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
            if(fragment==null || swipeRefreshLayout.getVisibility() != View.VISIBLE){
                //we are not showing anything
                if(Permissions.anyLocationPermissionsGranted(getContext())){
                    showNearbyFragmentIfPossible();
                }
            }
        }
        if (suppressArrivalsReload){
            // we have to suppress the reloading of the (possible) ArrivalsFragment
            Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
            if (fragment instanceof ArrivalsFragment){
                ArrivalsFragment frag = (ArrivalsFragment) fragment;
                frag.setReloadOnResume(false);
            }
            //deactivate
            suppressArrivalsReload = false;
        }

        if(pendingStopID!=null){

            Log.d(DEBUG_TAG, "Pending request for arrivals at stop ID: "+pendingStopID);
            requestArrivalsForStopID(pendingStopID);
            pendingStopID = null;
        }
        mListener.readyGUIfor(FragmentKind.MAIN_SCREEN_FRAGMENT);

        fragmentHelper.setBlockAllActivities(false);

    }

    @Override
    public void onPause() {
        //mainHandler = null;
        locationManager.removeLocationRequestFor(requester);
        super.onPause();
        fragmentHelper.setBlockAllActivities(true);
        fragmentHelper.stopLastRequestIfNeeded(true);
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

        BarcodeScanOptions scanOptions = new BarcodeScanOptions();
        Intent intent = scanOptions.createScanIntent();
        if(!BarcodeScanUtils.checkTargetPackageExists(getContext(), intent)){
            BarcodeScanUtils.showDownloadDialog(null, this);
        }else {
            barcodeLauncher.launch(scanOptions);
        }
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
            fragmentHelper.stopLastRequestIfNeeded(true);
            requestArrivalsForStopID(busStopID);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            query = query.trim();
            if(getContext()!=null) {
                if (query.length() < 1) {
                    Toast.makeText(getContext(), R.string.insert_bus_stop_name_error, Toast.LENGTH_SHORT).show();
                } else if(query.length()< 2){
                    Toast.makeText(getContext(), R.string.query_too_short, Toast.LENGTH_SHORT).show();
                }
                else {
                    fragmentHelper.stopLastRequestIfNeeded(true);
                    new AsyncStopsSearcher(fragmentHelper, stopsFinderByNames).execute(query);
                }
            }
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
        return (fragment!= null && fragment.isResumed());
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

    @Nullable
    @org.jetbrains.annotations.Nullable
    @Override
    public View getBaseViewForSnackBar() {
        return coordLayout;
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

    private void actuallyShowNearbyStopsFragment(){
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        final Fragment existingFrag = childFragMan.findFragmentById(R.id.resultFrame);
        // fragment;
        if (!(existingFrag instanceof NearbyStopsFragment)){
            Log.d(DEBUG_TAG, "actually showing Nearby Stops Fragment");
            //there is no fragment showing
            final NearbyStopsFragment fragment = NearbyStopsFragment.newInstance(NearbyStopsFragment.FragType.STOPS);

            FragmentTransaction ft = childFragMan.beginTransaction();

            ft.replace(R.id.resultFrame, fragment, NearbyStopsFragment.FRAGMENT_TAG);
            if (getActivity()!=null && !getActivity().isFinishing())
            ft.commit();
            else Log.e(DEBUG_TAG, "Not showing nearby fragment because activity null or is finishing");
        }
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


        //if we are getting results, already, stop waiting for nearbyStops
        if (fragmentType == FragmentKind.ARRIVALS || fragmentType == FragmentKind.STOPS) {
            hideKeyboard();

            if (pendingNearbyStopsFragmentRequest) {
                locationManager.removeLocationRequestFor(requester);
                pendingNearbyStopsFragmentRequest = false;
            }
        }

        if (fragmentType == null) Log.e("ActivityMain", "Problem with fragmentType");
        else
            switch (fragmentType) {
                case ARRIVALS:
                    prepareGUIForBusLines();
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

    @Override
    public void showLineOnMap(String routeGtfsId) {
        //pass to activity
        mListener.showLineOnMap(routeGtfsId);
    }

    @Override
    public void showMapCenteredOnStop(Stop stop) {
        if(mListener!=null) mListener.showMapCenteredOnStop(stop);
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
            Log.d(DEBUG_TAG, "Deferring update for stop "+ID+ " saved: "+pendingStopID);
            return;
        }
        final boolean delayedRequest = !(pendingStopID==null);
        final FragmentManager framan = getChildFragmentManager();
        if (getContext()==null){
            Log.e(DEBUG_TAG, "Asked for arrivals with null context");
            return;
        }
        ArrivalsFetcher[] fetchers = utils.getDefaultArrivalsFetchers(getContext()).toArray(new ArrivalsFetcher[0]);
        if (ID == null || ID.length() <= 0) {
            // we're still in UI thread, no need to mess with Progress
            showToastMessage(R.string.insert_bus_stop_number_error, true);
            toggleSpinner(false);
        } else  if (framan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
            ArrivalsFragment fragment = (ArrivalsFragment) framan.findFragmentById(R.id.resultFrame);
            if (fragment != null && fragment.getStopID() != null && fragment.getStopID().equals(ID)){
                // Run with previous fetchers
                //fragment.getCurrentFetchers().toArray()
                new AsyncArrivalsSearcher(fragmentHelper,fragment.getCurrentFetchersAsArray(), getContext()).execute(ID);
            } else{
                new AsyncArrivalsSearcher(fragmentHelper, fetchers, getContext()).execute(ID);
            }
        }
        else {
            Log.d(DEBUG_TAG, "This is probably the first arrivals search, preparing GUI");
            prepareGUIForBusLines();
            new AsyncArrivalsSearcher(fragmentHelper,fetchers, getContext()).execute(ID);
            Log.d(DEBUG_TAG, "Started search for arrivals of stop " + ID);
        }
    }

    private boolean checkLocationPermission(){
        final Context context = getContext();
        if(context==null) return false;

        final boolean isOldVersion = Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
        final boolean noPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        return isOldVersion || !noPermission;

    }
    private void requestLocationPermission(){
        requestPermissionLauncher.launch(LOCATION_PERMISSIONS);
    }

    private void showNearbyFragmentIfPossible() {
        if (isNearbyFragmentShown()) {
            //nothing to do
            Log.w(DEBUG_TAG, "Asked to show nearby fragment but we already are showing it");
            return;
        }
        if (getContext() == null) {
            Log.e(DEBUG_TAG, "Wanting to show nearby fragment but context is null");
            return;
        }

        if (fragmentHelper.getLastSuccessfullySearchedBusStop() == null
                && !childFragMan.isDestroyed()) {
            //Go ahead with the request

            actuallyShowNearbyStopsFragment();
            pendingNearbyStopsFragmentRequest = false;
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
    /*

     * Run location requests separately and asynchronously

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

     */

}