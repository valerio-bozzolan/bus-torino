/*
	BusTO  - Fragments components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.text.InputType;
import android.widget.TextView;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import it.reyboz.bustorino.BuildConfig;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.StopNearbyAdapter;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.util.Permissions;
import it.reyboz.bustorino.viewmodels.IntroViewModel;
import it.reyboz.bustorino.viewmodels.SearchStopsDBViewModel;
import org.jetbrains.annotations.NotNull;

import static it.reyboz.bustorino.util.Permissions.LOCATION_PERMISSIONS;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainScreenFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainScreenFragment extends BarcodeFragment implements  FragmentListenerMain, ParentFragmentManagerFromChild{


    private static final String SAVED_FRAGMENT="saved_fragment";

    private static final String DEBUG_TAG = "BusTO - MainFragment";

    public static final String ARG_INITIAL_CONTENT = "initial_content";
    public static final String ARG_STOP_ID         = "pending_stop_id";
    public static final String ARG_SEARCH_QUERY    = "pending_search_query";

    public final static String FRAGMENT_TAG = "MainScreenFragment";

    private enum SearchMode {SEARCH_ID,SEARCH_NAME,INITIAL}
    public enum InitialScreen {
        HOME_BUTTONS(0),
        NEARBY_STOPS(1),
        ARRIVALS(2),
        STOP_SEARCH(3),
        NEARBY_ARRIVALS(4);

        public final int code;
        InitialScreen(int code) { this.code = code; }

        @Nullable
        public static InitialScreen fromCode(int code) {
            for (InitialScreen c : values()) if (c.code == code) return c;
            return null;
        }
        @NonNull
        public static InitialScreen fromFragmentKind(@NonNull FragmentKind kind){
            switch (kind){
                case HOME_BUTTONS -> { return  InitialScreen.HOME_BUTTONS; }
                case NEARBY_STOPS -> { return  InitialScreen.NEARBY_STOPS; }
                case FragmentKind.ARRIVALS -> { return  InitialScreen.ARRIVALS; }
                case FragmentKind.STOPS -> { return  InitialScreen.STOP_SEARCH; }
                case FragmentKind.NEARBY_ARRIVALS -> { return  InitialScreen.NEARBY_ARRIVALS; }
                default -> {
                    throw new IllegalArgumentException("Unknown fragment kind");
                }
            }
        }
    }

    private FragmentHelper fragmentHelper;
    private SwipeRefreshLayout swipeRefreshLayout;
    //private EditText busStopSearchByIDEditText;
    //private EditText busStopSearchByNameEditText;

    private SearchView busStopSearchView;
    private ProgressBar progressBar;
    private FloatingActionButton floatingActionButton;

    private SearchStopsDBViewModel searchStopsViewModel;

    private boolean setupOnStart = true;
    private boolean suppressArrivalsReload = false;
    private boolean initialScreenShown = false;
    private SearchMode searchMode = SearchMode.INITIAL;
    private FragmentManager childFragMan;

    /// LOCATION STUFF ///
    boolean pendingIntroRun = false;
    boolean pendingNearbyStopsFragmentRequest = false;
    boolean pendingNearbyAddToBackStack = false;
    boolean locationPermissionGranted, locationPermissionAsked = false;

    //// ACTIVITY ATTACHED (LISTENER ///
    private CommonFragmentListener mListener;

    private String pendingStopID = null;
    private String pendingSearchQuery = null;
    private InitialScreen initialScreen = InitialScreen.HOME_BUTTONS;
    private CoordinatorLayout coordLayout;

    private RecyclerView suggestionsRecyclerView;
    private StopNearbyAdapter suggestionsAdapter = null;
    private TextView stopSearchMessageTextView;

    //this is really a hackish thing, but it works
    private final LinkedBlockingQueue<Runnable> thingsToDoOnStart = new LinkedBlockingQueue<>();

    private boolean isSearchModeById(){
        return  searchMode != SearchMode.SEARCH_NAME;
    }

    private void emptySearchQueryViewModel(){
        searchStopsViewModel.searchStops("",isSearchModeById());
    }

    private void refreshStop() {
        if(getContext() == null){
            Log.w(DEBUG_TAG,"Asked to refresh stop but context is null");
            return;
        }
        if (childFragMan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
            ArrivalsFragment fragment = (ArrivalsFragment) childFragMan.findFragmentById(R.id.resultFrame);
            if (fragment == null){
                //we create a new fragment, which is WRONG
                Log.e("BusTO-RefreshStop", "Asking for refresh when there is no fragment");
            } else{
                //String stopName = fragment.getStopID();
                fragment.requestArrivalsForTheFragment();
            }
        } else { //we create a new fragment, which is WRONG
            Log.w(DEBUG_TAG, "Asked to refresh stop when there is no fragment");
        }
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    if (result == null) return;

                    if (result.get(Manifest.permission.ACCESS_COARSE_LOCATION) == null ||
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION) == null)
                        return;

                    Log.d(DEBUG_TAG, "Permissions for location are: " + result);
                    if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                        locationPermissionGranted = true;
                        Log.w(DEBUG_TAG, "Starting position");
                        /*if (mListener != null && getContext() != null) {
                            if (locationManager == null)
                                locationManager = AppLocationManager.getInstance(getContext());
                            locationManager.addLocationRequestFor(requester);
                        }

                         */
                        // show nearby fragment
                        //showNearbyStopsFragment();
                        Log.d(DEBUG_TAG, "We have location permission");
                        if (pendingNearbyStopsFragmentRequest) {
                            showNearbyFragmentIfPossible(pendingNearbyAddToBackStack);
                            pendingNearbyStopsFragmentRequest = false;
                        }
                    }
                    if (pendingNearbyStopsFragmentRequest) pendingNearbyStopsFragmentRequest = false;
                }
            });

    public MainScreenFragment() {
        // Required empty public constructor
    }


    public static MainScreenFragment newInstance(@NonNull InitialScreen kind,
                                            @Nullable String stopId,
                                            @Nullable String query) {
        MainScreenFragment f = new MainScreenFragment();
        f.setArguments(makeArgs(kind, stopId, query));
        return f;
    }
    public static MainScreenFragment newInstance(@NonNull InitialScreen kind, @Nullable Bundle args){
        MainScreenFragment f = new MainScreenFragment();
        if (args != null) {
            f.setArguments(args);
        }
        return f;
    }

    /**
     * Create the bundle for the arguments of the fragment
     * @param kind the kind of initial screen
     * @param stopId
     * @param query
     * @return
     */
    public static Bundle makeArgs(@NonNull InitialScreen kind, @Nullable String stopId, @Nullable String query) {
        Bundle b = new Bundle();
        b.putInt(ARG_INITIAL_CONTENT, kind.code);
        if (stopId != null) b.putString(ARG_STOP_ID, stopId);
        if (query  != null) b.putString(ARG_SEARCH_QUERY, query);
        return b;
    }
    public static Bundle makeArgsArrivals(@NonNull String stopID){
        return makeArgs(InitialScreen.ARRIVALS, stopID, null);
    }
    public static Bundle makeArgsStops(@NonNull String query){
        return makeArgs(InitialScreen.STOP_SEARCH, query, null);
    }
    public static Bundle makeArgsNearby(){
        return makeArgs(InitialScreen.NEARBY_STOPS, null, null);
    }
    public static Bundle makeArgsButtonsScreen(){
        return makeArgs(InitialScreen.HOME_BUTTONS, null, null);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            Log.d(DEBUG_TAG, "ARGS ARE NOT NULL: "+ args);

            if (args.containsKey(ARG_INITIAL_CONTENT)) {
                int code = args.getInt(ARG_INITIAL_CONTENT, InitialScreen.HOME_BUTTONS.code);
                InitialScreen parsed = InitialScreen.fromCode(code);
                initialScreen = (parsed != null) ? parsed : InitialScreen.HOME_BUTTONS;
            }
            String stopId = args.getString(ARG_STOP_ID);
            if (stopId != null)
                pendingSearchQuery = stopId;
            else
                pendingSearchQuery = args.getString(ARG_SEARCH_QUERY);
        }

        fragmentHelper = new FragmentHelper(this, getChildFragmentManager(), getContext(), R.id.resultFrame);
    }

    @Override
    public boolean needToPopMainStackOnBack() {
        return fragmentHelper.needToPopMainStackOnBack();
    }

    @Override
    public void setMainFragmentManagerTransition(boolean yes) {
        fragmentHelper.setMainFragmentManagerTransition(yes);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_main_screen, container, false);
        /// UI ELEMENTS //
        //busStopSearchByIDEditText = root.findViewById(R.id.busStopSearchByIDEditText);
        //busStopSearchByNameEditText = root.findViewById(R.id.busStopSearchByNameEditText);
        busStopSearchView = root.findViewById(R.id.busStopSearchView);
        progressBar = root.findViewById(R.id.progressBar);

        swipeRefreshLayout = root.findViewById(R.id.listRefreshLayout);
        floatingActionButton = root.findViewById(R.id.floatingActionButton);
        stopSearchMessageTextView = root.findViewById(R.id.stopSearchMessageTextView);
        suggestionsRecyclerView = root.findViewById(R.id.suggestionsRecyclerView);
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(),LinearLayoutManager.VERTICAL, false));
        EditText searchEditText = busStopSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchEditText.setSelectAllOnFocus(true);
        searchEditText.setOnFocusChangeListener( (view,hasFocus) ->{
            if(hasFocus){
                showFloatingActionButton(true);
            }
        });

        busStopSearchView.setOnFocusChangeListener( (view, hasFocus) ->{
            if(hasFocus){
                showFloatingActionButton(true);
            }
        });
        //busStopSearchView.setSelectAllOnFocus(true);
        /*busStopSearchByIDEditText
                .setOnEditorActionListener((v, actionId, event) -> {
                    // IME_ACTION_SEARCH alphabetical option
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        onSearchClick(v);
                        return true;
                    }
                    return false;
                });

         */
        /*searchEditText
                .setOnEditorActionListener((v, actionId, event) -> {
                    // IME_ACTION_SEARCH alphabetical option
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        onSearchClick(v);
                        return true;
                    }
                    return false;
                });

         */

        busStopSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                onSearchClick(null);
                emptySearchQueryViewModel();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                //Log.d(DEBUG_TAG, "Changed query text");
                searchStopsViewModel.searchStops(q, isSearchModeById());
                return true;
            }
        });

        swipeRefreshLayout
                .setOnRefreshListener(this::refreshStop);
        swipeRefreshLayout.setColorSchemeResources(R.color.blue_500, R.color.orange_500);

        coordLayout = root.findViewById(R.id.coord_layout);
        floatingActionButton.setImageResource(R.drawable.magnifying_glass_larger);
        floatingActionButton.setOnClickListener((this::onToggleKeyboardLayout));

        /*busStopSearchByIDEditText.setOnFocusChangeListener((v, hasFocus) -> {
            //Log.d(DEBUG_TAG, "stop search by ID has focus: " + hasFocus);
            if(hasFocus)
                setSearchModeBusStopID();
        });

        busStopSearchByNameEditText.setOnFocusChangeListener((v, hasFocus) -> {
            //Log.d(DEBUG_TAG, "stop search by Name has focus: " + hasFocus);
            if(hasFocus)
                setSearchModeBusStopName();
        });

         */

        AppCompatImageButton qrButton = root.findViewById(R.id.QRButton);
        qrButton.setOnClickListener(this::onQRButtonClick);

        AppCompatImageButton searchButton = root.findViewById(R.id.searchButton);
        searchButton.setOnClickListener(this::onSearchClick);

        // Fragment stuff
        childFragMan = getChildFragmentManager();
        childFragMan.addOnBackStackChangedListener(() -> Log.d("BusTO Main Fragment", "BACK STACK CHANGED"));

        /*
        cr.setAccuracy(Criteria.ACCURACY_FINE);
        cr.setAltitudeRequired(false);
        cr.setBearingRequired(false);
        cr.setCostAllowed(true);
        cr.setPowerRequirement(Criteria.NO_REQUIREMENT);
        */
        //locationManager = AppLocationManager.getInstance(requireContext());
        IntroViewModel introViewModel = new ViewModelProvider(requireActivity()).get(IntroViewModel.class);
        introViewModel.getIntroIsRunning().observe(getViewLifecycleOwner(), isRunning -> {
            pendingIntroRun = isRunning;
        });

        // TODO: Figure out how to go back to home when pressing home in the nav side bar
        /*fragShowingViewModel.getKindShowingFragment().observe(getViewLifecycleOwner(), kind -> {
            Log.w(DEBUG_TAG, "showing fragment kind: " + kind);
            try {
                var screenType = InternalScreen.fromFragmentKind(kind);
                if(screenType != internalScreen) {
                    showDifferentSubFragments(screenType);
                    internalScreen = screenType;
                }
            } catch (IllegalArgumentException e) {
                //ignored
                Log.d(DEBUG_TAG, "no update from fragment kind");
            }

        });

         */

        Log.d(DEBUG_TAG, "OnCreateView, savedInstanceState null: "+(savedInstanceState==null));
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(DEBUG_TAG, "onViewCreated, have savedInstanceState: "+ (savedInstanceState!=null) +
                "; SwipeRefreshLayout visible: "+(swipeRefreshLayout.getVisibility()==View.VISIBLE));
        //Log.d(DEBUG_TAG, "Saved instance state is: "+savedInstanceState);
        searchStopsViewModel = new ViewModelProvider(requireActivity()).get(SearchStopsDBViewModel.class);

        searchStopsViewModel.getFilteredStopsSearch().observe(getViewLifecycleOwner(),
                stops -> setSuggestionsStopRecyclerView(stops, true));

        if (getChildFragmentManager().findFragmentById(R.id.resultFrame)!= null){
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            // The child FragmentManager has restored its content — don't dispatch again
            return;
        }

        if (savedInstanceState != null) return;

        showDifferentSubFragments(initialScreen);
    }

    /**
     * Installs the initial child fragment based on the arguments supplied as arguments
     */
    private void showDifferentSubFragments(@NonNull InitialScreen screen) {
        boolean firstTime = !initialScreenShown;
        switch (screen) {
            case NEARBY_STOPS:
            case NEARBY_ARRIVALS: // TODO differentiate later
                //add to back stack if it is not just created
                showNearbyStopsFragmentChecking(!firstTime);
                break;
            case ARRIVALS:
                // pendingStopID is consumed in onResume → requestArrivalsForStopID
                if(pendingSearchQuery != null && isResumed()) {
                    swipeRefreshLayout.setVisibility(View.VISIBLE);
                    Log.d(DEBUG_TAG, "Searching arrivals for initial stop: "+pendingSearchQuery);
                    requestsArrivalsInternal(pendingSearchQuery, false);
                    pendingSearchQuery = null;
                }

                break;
            case STOP_SEARCH:
                if (pendingSearchQuery != null && pendingSearchQuery.length() >= 2) {
                    fragmentHelper.requestStopSearch(pendingSearchQuery);
                } else {
                    showButtonsFragment(firstTime);
                }
                pendingSearchQuery = null;
                break;
            case HOME_BUTTONS:
            default:
                showButtonsFragment(firstTime);
        }
        if(!initialScreenShown){
            initialScreenShown = true;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(DEBUG_TAG, "Saving instance state");
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.resultFrame);
        if (fragment!=null)
            getChildFragmentManager().putFragment(outState, SAVED_FRAGMENT, fragment);
        //if (fragmentHelper!=null) fragmentHelper.setBlockAllActivities(true);

    }

    public void setSuggestionsStopRecyclerView(List<Stop> stops, boolean showMessage){

        var res = !stops.isEmpty();
        var query = searchStopsViewModel.getQueryStops();
        //Log.d(DEBUG_TAG, "received stops suggestions, are "+stops.size());
        if(res){
            if(suggestionsAdapter!=null){
                suggestionsAdapter.setStops(stops);
            } else{
                suggestionsAdapter = new StopNearbyAdapter(new ArrayList<>(stops), null, true, (stop)->{
                    requestArrivalsForStopID(stop.ID);
                    //clear query
                    emptySearchQueryViewModel();
                    //setSuggestionsStopRecyclerView(List.of(), false);
                });
                suggestionsRecyclerView.setAdapter(suggestionsAdapter);
            }
            suggestionsRecyclerView.setVisibility(View.VISIBLE);

        } else{
            if(suggestionsAdapter !=null) suggestionsAdapter = null;
            suggestionsRecyclerView.setVisibility(View.GONE);
            if(showMessage && query!=null && !query.trim().isEmpty()){
                stopSearchMessageTextView.setText( searchMode == SearchMode.SEARCH_NAME ?
                        R.string.no_stop_found_search_name : R.string.no_stop_found_search_number);
                stopSearchMessageTextView.setVisibility(View.VISIBLE);
            } else{
                stopSearchMessageTextView.setVisibility(View.GONE);
            }

        }
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
        fragmentHelper.stopLastRequestIfNeeded();
        toggleSpinner(false);
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Log.d(DEBUG_TAG, "OnAttach called, setupOnAttach: "+ setupOnStart);
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
        try {
            while (!thingsToDoOnStart.isEmpty()) {
                var task = thingsToDoOnStart.take();
                task.run();
            }
        } catch (InterruptedException e) {
            Log.w(DEBUG_TAG, "Interrupted while doing task for start");
            thingsToDoOnStart.clear();
        }
        if (setupOnStart) {
            if (pendingStopID==null){
                if(!pendingIntroRun){
                    //show the fragment
                    //showButtonsFragment();
                }
            }
            else{
                ///TODO: if there is a stop displayed, we need to hold the update
            }
            setupOnStart = false;
        }
        // this happens when the fragment is recreated and the query is set on the searchView
        if(!searchStopsViewModel.getShowingSearchSuggestions()){
            emptySearchQueryViewModel();
        }
    }

    private void showButtonsFragment(boolean addInsteadOfReplace){

        swipeRefreshLayout.setVisibility(View.VISIBLE);
        var ft = childFragMan.beginTransaction();
        var frag = ButtonsFragment.newInstance();
        if(addInsteadOfReplace)
            ft.add(R.id.resultFrame,frag, ButtonsFragment.FRAGMENT_TAG);
        else{
            ft.replace(R.id.resultFrame, frag, ButtonsFragment.FRAGMENT_TAG);
            ft.addToBackStack(null);
        }
        ft.commit();
    }

    public void showButtonsFragmentIfNotNearby(boolean addToBackStack){
        if(isAdded()) {
            var framan = getChildFragmentManager();
            var showingFrag = framan.findFragmentById(R.id.resultFrame);
            if (showingFrag == null || showingFrag instanceof NearbyStopsFragment) {
                var fragHome = ButtonsFragment.newInstance();
                var ft = framan.beginTransaction();
                if (showingFrag == null) {
                    ft.add(R.id.resultFrame, fragHome, ButtonsFragment.FRAGMENT_TAG);
                } else {
                    ft.replace(R.id.resultFrame, fragHome, ButtonsFragment.FRAGMENT_TAG);
                }
                if (addToBackStack) ft.addToBackStack(null);
                ft.commit();
            } else {
                Log.d(DEBUG_TAG, "attempting to show buttons home fragment but have other types (different than nearby)");
            }
        } else{
            Log.d(DEBUG_TAG, "Fragment is not added, putting in queue of things to do");
            try {
                thingsToDoOnStart.put(() -> {
                    showButtonsFragmentIfNotNearby(addToBackStack);
                });
            } catch (InterruptedException e) {
                Log.e(DEBUG_TAG,"Cannot add task");
            }
        }
    }

    private void showNearbyStopsFragmentChecking(boolean addToBackStack){
        if(!checkLocationPermission()){
            requestLocationPermission();
            pendingNearbyStopsFragmentRequest = true;
            pendingNearbyAddToBackStack = addToBackStack;
            Log.d(DEBUG_TAG, "requesting location permission for nearby fragment");
        }
        else {
            Log.d(DEBUG_TAG, "Showing nearby stops fragment");
            showNearbyFragmentIfPossible(addToBackStack);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final Context con = requireContext();
        Log.w(DEBUG_TAG, "OnResume called, setupOnStart: "+ setupOnStart);
        //recheck the introduction activity has been run
        if(Permissions.bothLocationPermissionsGranted(con)){
            Log.d(DEBUG_TAG, "Location permission OK");

        } //don't request permission
        // if we have a pending stopID request, do it
        Log.d(DEBUG_TAG, "Pending stop ID for arrivals: "+pendingStopID);
        //this is the second time we are attaching this fragment ->
        Log.d(DEBUG_TAG, "Waiting for new stop request: "+ suppressArrivalsReload);

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
        // check if the fragment start query is null
        if(pendingSearchQuery!=null) {
            requestsArrivalsInternal(pendingSearchQuery, false);
            pendingSearchQuery = null;
        }
        else if(pendingStopID!=null){

            Log.d(DEBUG_TAG, "Pending request for arrivals at stop ID: "+pendingStopID);
            requestArrivalsForStopID(pendingStopID);
            pendingStopID = null;
        }
        //mListener.readyGUIfor(FragmentKind.MAIN_SCREEN_FRAGMENT);

        //fragmentHelper.setBlockAllActivities(false);

    }

    @Override
    public void onPause() {
        //mainHandler = null;
        //locationManager.removeLocationRequestFor(requester);
        //fragmentHelper.setBlockAllActivities(true);
        fragmentHelper.stopLastRequestIfNeeded();
        searchStopsViewModel.saveOpenSearchSuggestions();
        super.onPause();
    }


    /*
    GUI METHODS
     */

    @Override
    public void onQrScanSuccess(@NotNull String busIDToSearch) {
        EditText searchEditText = busStopSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchEditText.setText(busIDToSearch);
        requestArrivalsForStopID(busIDToSearch);
    }

    /**
     * QR scan button clicked
     *
     * @param v View QRButton clicked
     */
    public void onQRButtonClick(View v) {
        launchBarcodeScan();
    }

    /**
     * OK this is pure shit
     *
     * @param v View clicked
     */
    public void onSearchClick(View v) {
        //final StopsFinderByName[] stopsFinderByNames = new StopsFinderByName[]{new GTTStopsFetcher(), new FiveTStopsFetcher()};
        if (searchMode == SearchMode.SEARCH_ID || searchMode == SearchMode.INITIAL) {
            String busStopID = busStopSearchView.getQuery().toString();
            fragmentHelper.stopLastRequestIfNeeded();
            requestArrivalsForStopID(busStopID);
        } else if (searchMode == SearchMode.SEARCH_NAME) {
            // searchMode == SEARCH_BY_NAME
            String query = busStopSearchView.getQuery().toString();
            query = query.trim();
            if(getContext()!=null) {
                if (query.length() < 1) {
                    Toast.makeText(getContext(), R.string.insert_bus_stop_name_error, Toast.LENGTH_SHORT).show();
                } else if(query.length()< 2){
                    Toast.makeText(getContext(), R.string.query_too_short, Toast.LENGTH_SHORT).show();
                }
                else {
                    fragmentHelper.requestStopSearch(query);
                }
            }
        }
    }

    public void onToggleKeyboardLayout(View v) {
        switch (searchMode){
            case SEARCH_ID:
                setSearchMode(SearchMode.SEARCH_NAME);
                if (busStopSearchView.requestFocus())
                    showKeyboard();
                //}
                break;
            case SEARCH_NAME:
            case INITIAL:
                setSearchMode(SearchMode.SEARCH_ID);
                if (busStopSearchView.requestFocus())
                   showKeyboard();
                //}
        }

    }
    @Override
    public void enableRefreshLayout(boolean yes) {
        Log.d(DEBUG_TAG, "Enabling refresh layout: " + yes);
        swipeRefreshLayout.setEnabled(yes);
    }

    ////////////////////////////////////// GUI HELPERS /////////////////////////////////////////////

    private EditText getSearchEditText() {
        return busStopSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
    }
    public void showKeyboard() {
        if(getActivity() == null) return;
        final EditText et = getSearchEditText();
        et.requestFocus();
        et.post(() -> {
            InputMethodManager imm = (InputMethodManager)
                    requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            imm.restartInput(et);                                  // applica il nuovo inputType
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        });
    }


    protected boolean isNearbyFragmentShown(){
        Fragment fragment = getChildFragmentManager().findFragmentByTag(NearbyStopsFragment.FRAGMENT_TAG);
        return (fragment!= null && fragment.isResumed());
    }

    private void setSearchMode(SearchMode mode) {
        searchMode = mode;
        busStopSearchView.setQuery("", false);
        boolean byId = mode != SearchMode.SEARCH_NAME;
        busStopSearchView.setInputType(byId ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        floatingActionButton.setImageResource(byId ? R.drawable.numeric : R.drawable.alphabetical);
        busStopSearchView.setQueryHint(getString(byId ? R.string.insert_bus_stop_number : R.string.insert_bus_stop_name));
    }

    /*
     * Having that cursor at the left of the edit text makes me cancer.
     *
     * @param busStopID bus stop ID
     */
    //
    /*
    private void setBusStopSearchByIDEditText(String busStopID) {
        busStopSearchByIDEditText.setText(busStopID);
        busStopSearchByIDEditText.setSelection(busStopID.length());
    }

     */

    @Nullable
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


    private void prepareGUIForArrivals() {
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        //this is to hide the eventual search results from the text
        //setSuggestionsStopRecyclerView(List.of(), false);
        //actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        //setSuggestionsStopRecyclerView(List.of(), false);

        //actionHelpMenuItem.setVisible(false);
    }

    @Override
    public void showFloatingActionButton(boolean yes) {
        //mListener.showFloatingActionButton(yes);
        if(yes)
            floatingActionButton.setVisibility(View.VISIBLE);
        else
            floatingActionButton.setVisibility(View.GONE);
    }

    /**
     * This provides a temporary fix to make the transition
     * to a single asynctask go smoother
     *
     * @param fragmentType the type of fragment created
     */
    @Override
    public void readyGUIfor(FragmentKind fragmentType) {
        if(BuildConfig.DEBUG) Log.d(DEBUG_TAG, "Readying main fragment for type "+fragmentType);
        //if we are getting results, already, stop waiting for nearbyStops
        if (fragmentType == FragmentKind.ARRIVALS || fragmentType == FragmentKind.STOPS) {
            hideKeyboard();

            if (pendingNearbyStopsFragmentRequest) {
                //locationManager.removeLocationRequestFor(requester);
                pendingNearbyStopsFragmentRequest = false;
            }
        }

        if (fragmentType == null) Log.e("ActivityMain", "Problem with fragmentType");
        else
            switch (fragmentType) {
                case ARRIVALS:
                    prepareGUIForArrivals();
                    break;
                case STOPS:
                    prepareGUIForBusStops();
                    break;
                default:
                    //Log.d(DEBUG_TAG, "Fragment type is unknown");
                    return;
            }
        // Shows hints


    }

    /*
    // KEEPING THIS METHODS AS EXAMPLES FOR SHOW
    boolean searchStopsResultsFragmentShown(){
        Fragment frag = childFragMan.findFragmentById(R.id.resultFrame);
        return frag instanceof StopSearchResultFragment;
    }
    void showSearchStopsResultsFragment(){
        final String TAG = "stopSearchResult";
        Fragment frag = childFragMan.findFragmentById(R.id.resultFrame);
        if (frag instanceof StopSearchResultFragment){
            return; //nothing to do
        }
        boolean exists = frag != null;
        var trans = childFragMan.beginTransaction();

        frag = childFragMan.findFragmentByTag(TAG);
        if (!(frag instanceof  StopSearchResultFragment)) {
            frag = StopSearchResultFragment.newInstance();
        }
        if(exists){
            //show the fragment
            trans.replace(R.id.resultFrame,frag, TAG);
        } else{
            //create new fragment
            trans.add(R.id.resultFrame,frag, TAG);
        }
        trans.addToBackStack(null);
        trans.commit();
    }
     */

    @Override
    public void openLineFromStop(String routeGtfsId, @Nullable String stopIDFrom) {
        //pass to activity
        if(mListener!=null) mListener.openLineFromStop(routeGtfsId, stopIDFrom);
    }

    @Override
    public void openLineFromVehicle(String routeGtfsId, @Nullable String optionalPatternId, @Nullable Bundle args) {
        if(mListener!=null) mListener.openLineFromVehicle(routeGtfsId, optionalPatternId, args);
    }

    @Override
    public void openNearbyStopsFragment() {
        if(isAdded())
            showNearbyStopsFragmentChecking(true);
        else
            try{
                thingsToDoOnStart.put(() -> showNearbyStopsFragmentChecking(true));
            } catch (InterruptedException e) {
                Log.e(DEBUG_TAG, "trying to put open nearby in task but was interrupted");
            }
    }

    @Override
    public void openLinesFragment() {
        if(mListener!=null) mListener.openLinesFragment();
    }

    @Override
    public void openFavoritesFragment() {
        if(mListener!=null) mListener.openFavoritesFragment();
    }

    @Override
    public void showMapCenteredOnStop(Stop stop) {
        if(mListener!=null) mListener.showMapCenteredOnStop(stop);
    }

    private void requestsArrivalsInternal(String stopID, boolean addToBackStack) {
        if (!isResumed()){
            //defer request to onResume - it will be added to the backstack
            pendingStopID = stopID;
            Log.d(DEBUG_TAG, "Deferring update for stop "+stopID+ " saved: "+pendingStopID);
            return;
        }
        final boolean delayedRequest = !(pendingStopID==null);
        final FragmentManager framan = getChildFragmentManager();
        if (getContext()==null){
            Log.e(DEBUG_TAG, "Asked for arrivals with null context");
            return;
        }
        if (stopID == null || stopID.isEmpty()) {
            // we're still in UI thread, no need to mess with Progress
            showToastMessage(R.string.insert_bus_stop_number_error, true);
            toggleSpinner(false);
        } else{
            // ensure that the new sub-fragment is gonna be visible
            swipeRefreshLayout.setVisibility(View.VISIBLE);

            var palinaTrial = new Palina(stopID);
            if (framan.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment fragment) {
                if (fragment.isFragmentForTheSameStop(palinaTrial)){
                    // Run with previous fetchers
                    //fragment.getCurrentFetchers().toArray()
                    fragment.requestArrivalsForTheFragment();
                } else{
                    // The rest of the case is handled by the fragment Helper
                    fragmentHelper.showArrivalsFragmentForStop(palinaTrial, addToBackStack);
                }
            }
            else {
                // this is not needed any more
                //prepareGUIForArrivals();
                fragmentHelper.showArrivalsFragmentForStop(palinaTrial, addToBackStack);

            }
        }
    }

    /**
     * Main method for stops requests
     * @param ID the Stop ID
     */
    @Override
    public void requestArrivalsForStopID(String ID) {
        requestsArrivalsInternal(ID, true);
    }

    private boolean checkLocationPermission(){
        final Context context = getContext();
        if(context==null) return false;

        final boolean noPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        return !noPermission;

    }
    private void requestLocationPermission(){
        if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
            makeToast(R.string.enable_position_message_nearby);
        }
        requestPermissionLauncher.launch(LOCATION_PERMISSIONS);
    }

    private void showNearbyFragmentIfPossible(boolean addToBackStack) {
        if (isNearbyFragmentShown()) {
            //nothing to do
            Log.d(DEBUG_TAG, "Asked to show nearby fragment but we already are showing it");
            return;
        }
        if (getContext() == null) {
            Log.e(DEBUG_TAG, "Wanting to show nearby fragment but context is null");
            return;
        }

        if (!childFragMan.isDestroyed()) {
            //Go ahead with the request
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            final Fragment existingFrag = childFragMan.findFragmentById(R.id.resultFrame);
            // fragment;
            if (!(existingFrag instanceof NearbyStopsFragment)){
                Log.d(DEBUG_TAG, "actually showing Nearby Stops Fragment");
                //there is no fragment showing
                var nearbyFrag = (NearbyStopsFragment) childFragMan.findFragmentByTag(NearbyStopsFragment.FRAGMENT_TAG);
                if(nearbyFrag==null){
                    nearbyFrag = NearbyStopsFragment.newInstance(NearbyStopsFragment.FragType.STOPS);
                }
                FragmentTransaction ft = childFragMan.beginTransaction();

                ft.replace(R.id.resultFrame, nearbyFrag, NearbyStopsFragment.FRAGMENT_TAG);
                if(addToBackStack) ft.addToBackStack(null);
                if (getActivity()!=null && !getActivity().isFinishing())
                    ft.commit();
                else Log.e(DEBUG_TAG, "Not showing nearby fragment because activity null or is finishing");
            }
            pendingNearbyStopsFragmentRequest = false;
        }
    }

}