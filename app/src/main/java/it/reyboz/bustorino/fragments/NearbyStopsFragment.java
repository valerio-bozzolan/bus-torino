/*
	BusTO  - Fragments components
    Copyright (C) 2018 Fabio Mazza

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

import android.annotation.SuppressLint;
import android.content.Context;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ProgressBar;
import android.widget.TextView;
import it.reyboz.bustorino.BuildConfig;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.ArrivalsStopAdapter;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.middleware.AppLocationManager;
import it.reyboz.bustorino.adapters.SquareStopAdapter;
import it.reyboz.bustorino.middleware.AutoFitGridLayoutManager;
import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.Permissions;
import it.reyboz.bustorino.util.StopSorterByDistance;
import it.reyboz.bustorino.viewmodels.NearbyStopsViewModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class NearbyStopsFragment extends Fragment {

    public enum FragType{
        STOPS(1), ARRIVALS(2);
        private final int num;
        FragType(int num){
            this.num = num;
        }
        public static FragType fromNum(int i){
            switch (i){
                case 1: return STOPS;
                case 2: return ARRIVALS;
                default:
                    throw new IllegalArgumentException("type not recognized");
            }
        }
    }
    private enum LocationShowingStatus {SEARCHING, FIRST_FIX, DISABLED, NO_PERMISSION}

    private FragmentListenerMain mListener;
    private FragmentLocationListener fragmentLocationListener;

    private final static String DEBUG_TAG = "NearbyStopsFragment";
    private final static String FRAGMENT_TYPE_KEY = "FragmentType";
    //public final static int TYPE_STOPS = 19, TYPE_ARRIVALS = 20;
    private FragType fragment_type = FragType.STOPS;

    public final static String FRAGMENT_TAG="NearbyStopsFrag";

    private RecyclerView gridRecyclerView;

    private SquareStopAdapter dataAdapter;
    private AutoFitGridLayoutManager gridLayoutManager;
    private GPSPoint lastPosition = null;
    private ProgressBar circlingProgressBar,flatProgressBar;
    private int distance = 10;
    protected SharedPreferences globalSharedPref;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private TextView messageTextView,titleTextView, loadingTextView;
    private CommonScrollListener scrollListener;
    private AppCompatButton switchButton;
    private boolean firstLocForStops = true,firstLocForArrivals = true;
    public static final int COLUMN_WIDTH_DP = 250;


    private Integer MAX_DISTANCE = -3;
    private int MIN_NUM_STOPS = -1;
    private int TIME_INTERVAL_REQUESTS = -1;
    private LocationManager locManager;

    //These are useful for the case of nearby arrivals
    private NearbyArrivalsDownloader arrivalsManager = null;
    private ArrivalsStopAdapter arrivalsStopAdapter = null;

    private boolean dbUpdateRunning = false;

    private ArrayList<Stop> currentNearbyStops = new ArrayList<>();
    private NearbyArrivalsDownloader nearbyArrivalsDownloader;

    private LocationShowingStatus showingStatus = LocationShowingStatus.NO_PERMISSION;

    private final NearbyArrivalsDownloader.ArrivalsListener arrivalsListener = new NearbyArrivalsDownloader.ArrivalsListener() {
        @Override
        public void setProgress(int completedRequests, int pendingRequests) {
            if(flatProgressBar!=null) {
                if (pendingRequests == 0) {
                    flatProgressBar.setIndeterminate(true);
                    flatProgressBar.setVisibility(View.GONE);
                } else {
                    flatProgressBar.setIndeterminate(false);
                    flatProgressBar.setProgress(completedRequests);
                }
            }
        }

        @Override
        public void onAllRequestsCancelled() {
            if(flatProgressBar!=null) flatProgressBar.setVisibility(View.GONE);
        }

        @Override
        public void showCompletedArrivals(ArrayList<Palina> completedPalinas) {
            showArrivalsInRecycler(completedPalinas);
        }
    };

    //ViewModel
    private NearbyStopsViewModel viewModel;

    public NearbyStopsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment NearbyStopsFragment.
     */
    public static NearbyStopsFragment newInstance(FragType type) {
        //if(fragmentType != TYPE_STOPS && fragmentType != TYPE_ARRIVALS )
        //    throw new IllegalArgumentException("WRONG KIND OF FRAGMENT USED");
        NearbyStopsFragment fragment = new NearbyStopsFragment();
        final Bundle args = new Bundle(1);
        args.putInt(FRAGMENT_TYPE_KEY,type.num);
        fragment.setArguments(args);
        return fragment;
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            setFragmentType(FragType.fromNum(getArguments().getInt(FRAGMENT_TYPE_KEY)));
        }
        locManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        fragmentLocationListener = new FragmentLocationListener();
        if (getContext()!=null) {
            globalSharedPref = getContext().getSharedPreferences(getString(R.string.mainSharedPreferences), Context.MODE_PRIVATE);
            globalSharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        }

        nearbyArrivalsDownloader = new NearbyArrivalsDownloader(getContext().getApplicationContext(), arrivalsListener);


    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        if (getContext() == null) throw new RuntimeException();
        View root = inflater.inflate(R.layout.fragment_nearby_stops, container, false);
        gridRecyclerView = root.findViewById(R.id.stopGridRecyclerView);
        gridLayoutManager = new AutoFitGridLayoutManager(getContext().getApplicationContext(), Float.valueOf(utils.convertDipToPixels(getContext(),COLUMN_WIDTH_DP)).intValue());
        gridRecyclerView.setLayoutManager(gridLayoutManager);
        gridRecyclerView.setHasFixedSize(false);
        circlingProgressBar = root.findViewById(R.id.circularProgressBar);
        flatProgressBar = root.findViewById(R.id.horizontalProgressBar);
        messageTextView = root.findViewById(R.id.messageTextView);
        titleTextView = root.findViewById(R.id.titleTextView);
        loadingTextView = root.findViewById(R.id.positionLoadingTextView);
        switchButton = root.findViewById(R.id.switchButton);

        scrollListener = new CommonScrollListener(mListener,false);
        switchButton.setOnClickListener(v -> switchFragmentType());
        Log.d(DEBUG_TAG, "onCreateView");

        final Context appContext =requireContext().getApplicationContext();
        DatabaseUpdate.watchUpdateWorkStatus(getContext(), this, new Observer<List<WorkInfo>>() {
            @SuppressLint("MissingPermission")
            @Override
            public void onChanged(List<WorkInfo> workInfos) {
                if(workInfos.isEmpty()) return;

                WorkInfo wi = workInfos.get(0);
                if (wi.getState() == WorkInfo.State.RUNNING && fragmentLocationListener.isRegistered) {
                    locManager.removeUpdates(fragmentLocationListener);
                    fragmentLocationListener.isRegistered = true;
                    dbUpdateRunning = true;
                } else{
                    //start the request
                    if(!fragmentLocationListener.isRegistered){
                        requestLocationUpdates();
                    }
                    dbUpdateRunning = false;
                }
            }
        });

        //observe the livedata
        viewModel.getStopsAtDistance().observe(getViewLifecycleOwner(), stops -> {
            if (!dbUpdateRunning && (stops.size() < MIN_NUM_STOPS && distance <= MAX_DISTANCE)) {
                distance = distance + 40;
                viewModel.requestStopsAtDistance(distance, true);
                //Log.d(DEBUG_TAG, "Doubling distance now!");
                return;
            }
            if(!stops.isEmpty()) {
                Log.d(DEBUG_TAG, "Showing "+stops.size()+" stops nearby");
                currentNearbyStops =stops;
                showStopsInViews(currentNearbyStops, lastPosition);
            }
        });
        if(Permissions.anyLocationPermissionsGranted(appContext)){
            setShowingStatus(LocationShowingStatus.SEARCHING);
        } else {
            setShowingStatus(LocationShowingStatus.NO_PERMISSION);

        }
        return root;
    }

    //because linter is stupid and cannot look inside *anyLocationPermissionGranted*
    @SuppressLint("MissingPermission")
    private boolean requestLocationUpdates(){
        if(Permissions.anyLocationPermissionsGranted(requireContext())) {
            locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    3000, 10.0f, fragmentLocationListener
            );
            fragmentLocationListener.isRegistered = true;
            return true;
        } else return false;
    }



    /**
     * Use this method to set the fragment type
     * @param type the type, TYPE_ARRIVALS or TYPE_STOPS
     */
    private void setFragmentType(FragType type){
        this.fragment_type = type;
        switch(type){
            case ARRIVALS:
                TIME_INTERVAL_REQUESTS = 5*1000;
                break;
            case STOPS:
                TIME_INTERVAL_REQUESTS = 1000;

        }
    }
    private void setShowingStatus(@NonNull LocationShowingStatus newStatus){
        if(newStatus == showingStatus){
            Log.d(DEBUG_TAG, "Asked to set new displaying status but it's the same");
            return;
        }
        switch (newStatus){
            case FIRST_FIX:
                circlingProgressBar.setVisibility(View.GONE);
                loadingTextView.setVisibility(View.GONE);
                gridRecyclerView.setVisibility(View.VISIBLE);
                messageTextView.setVisibility(View.GONE);
                break;
            case NO_PERMISSION:
                circlingProgressBar.setVisibility(View.GONE);
                loadingTextView.setVisibility(View.GONE);
                messageTextView.setText(R.string.enable_position_message_nearby);
                messageTextView.setVisibility(View.VISIBLE);
                break;
            case DISABLED:
                if (showingStatus== LocationShowingStatus.SEARCHING){
                    circlingProgressBar.setVisibility(View.GONE);
                    loadingTextView.setVisibility(View.GONE);
                }
                messageTextView.setText(R.string.enableGpsText);
                messageTextView.setVisibility(View.VISIBLE);
                break;
            case SEARCHING:
                circlingProgressBar.setVisibility(View.VISIBLE);
                loadingTextView.setVisibility(View.VISIBLE);
                gridRecyclerView.setVisibility(View.GONE);
                messageTextView.setVisibility(View.GONE);
        }
        showingStatus = newStatus;
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FragmentListenerMain) {
            mListener = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context
                    + " must implement OnFragmentInteractionListener");
        }
        Log.d(DEBUG_TAG, "OnAttach called");
        viewModel =  new ViewModelProvider(this).get(NearbyStopsViewModel.class);


    }

    @Override
    public void onPause() {
        super.onPause();

        gridRecyclerView.setAdapter(null);
        locManager.removeUpdates(fragmentLocationListener);
        fragmentLocationListener.isRegistered = false;
        Log.d(DEBUG_TAG,"On paused called");
    }

    @Override
    public void onResume() {
        super.onResume();
        try{
            if(!dbUpdateRunning && !fragmentLocationListener.isRegistered) {
                requestLocationUpdates();
            }
        } catch (SecurityException ex){
            //ignored
            //try another location provider
        }
        //fix view if we were showing the stops or the arrivals
        prepareForFragmentType();
        switch(fragment_type){
            case STOPS:
                if(dataAdapter!=null){
                    //gridRecyclerView.setAdapter(dataAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
                    loadingTextView.setVisibility(View.GONE);
                }
                break;
            case ARRIVALS:
                if(arrivalsStopAdapter!=null){
                    //gridRecyclerView.setAdapter(arrivalsStopAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
                    loadingTextView.setVisibility(View.GONE);
                }
        }

        mListener.enableRefreshLayout(false);
        Log.d(DEBUG_TAG,"OnResume called");
        if(getContext()==null){
            Log.e(DEBUG_TAG, "NULL CONTEXT, everything is going to crash now");
            MIN_NUM_STOPS = 5;
            MAX_DISTANCE = 600;
            return;
        }
        //Re-read preferences
        SharedPreferences shpr = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        //For some reason, they are all saved as strings
        MAX_DISTANCE = shpr.getInt(getString(R.string.pref_key_radius_recents),600);
        boolean isMinStopInt = true;
        try{
            MIN_NUM_STOPS = shpr.getInt(getString(R.string.pref_key_num_recents), 5);
        } catch (ClassCastException ex){
            isMinStopInt = false;
        }
        if(!isMinStopInt)
            try {
                MIN_NUM_STOPS = Integer.parseInt(shpr.getString(getString(R.string.pref_key_num_recents), "5"));
            } catch (NumberFormatException ex){
                MIN_NUM_STOPS = 5;
            }
        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "Max distance for stops: "+MAX_DISTANCE+ ", Min number of stops: "+MIN_NUM_STOPS);

    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gridRecyclerView.setVisibility(View.INVISIBLE);
        gridRecyclerView.addOnScrollListener(scrollListener);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        if(arrivalsManager!=null) arrivalsManager.cancelAllRequests();
    }

    /**
     * Display the stops, or run new set of requests for arrivals
     */
    private void showStopsInViews(ArrayList<Stop> stops, GPSPoint location){
        if (stops.isEmpty()) {
            setNoStopsLayout();
            return;
        }
        if (location == null){
            // we could do something better, but it's better to do this for now
            return;
        }

        double minDistance = Double.POSITIVE_INFINITY;
        for(Stop s: stops){
            minDistance = Math.min(minDistance, s.getDistanceFromLocation(location.getLatitude(), location.getLongitude()));
        }


        //quick trial to hopefully always get the stops in the correct order
        Collections.sort(stops,new StopSorterByDistance(location));
        switch (fragment_type){
            case STOPS:
                showStopsInRecycler(stops);
                break;
            case ARRIVALS:
                if(getContext()==null) break; //don't do anything if we're not attached
                if(arrivalsManager==null)
                    arrivalsManager = new NearbyArrivalsDownloader(getContext().getApplicationContext(), arrivalsListener);
                arrivalsManager.requestArrivalsForStops(stops);
                /*flatProgressBar.setVisibility(View.VISIBLE);
                flatProgressBar.setProgress(0);
                flatProgressBar.setIndeterminate(false);
                 */
                //for the moment, be satisfied with only one location
                //AppLocationManager.getInstance(getContext()).removeLocationRequestFor(fragmentLocationListener);
                break;
            default:
        }

    }

    /**
     * To enable targeting from the Button
     */
    public void switchFragmentType(View v){
        switchFragmentType();
    }

    /**
     * Call when you need to switch the type of fragment
     */
    private void switchFragmentType(){
        switch (fragment_type){
            case ARRIVALS:
                setFragmentType(FragType.STOPS);
                break;
            case STOPS:
                setFragmentType(FragType.ARRIVALS);
                break;
            default:
        }
        prepareForFragmentType();
        fragmentLocationListener.lastUpdateTime = -1;
        //locManager.removeLocationRequestFor(fragmentLocationListener);
        //locManager.addLocationRequestFor(fragmentLocationListener);
        showStopsInViews(currentNearbyStops, lastPosition);
    }

    /**
     * Prepare the views for the set fragment type
     */
    private void prepareForFragmentType(){
        if(fragment_type==FragType.STOPS){
            switchButton.setText(getString(R.string.show_arrivals));
            titleTextView.setText(getString(R.string.nearby_stops_message));
            if(arrivalsManager!=null)
                arrivalsManager.cancelAllRequests();
            if(dataAdapter!=null)
                gridRecyclerView.setAdapter(dataAdapter);

        } else if (fragment_type==FragType.ARRIVALS){
            titleTextView.setText(getString(R.string.nearby_arrivals_message));
            switchButton.setText(getString(R.string.show_stops));
            if(arrivalsStopAdapter!=null)
                gridRecyclerView.setAdapter(arrivalsStopAdapter);
        }
    }

    //useful methods

    /////// GUI METHODS ////////
    private void showStopsInRecycler(List<Stop> stops){

        if(firstLocForStops) {
            dataAdapter = new SquareStopAdapter(stops, mListener, lastPosition);
            gridRecyclerView.setAdapter(dataAdapter);
            firstLocForStops = false;
        }else {
            dataAdapter.setStops(stops);
            dataAdapter.setUserPosition(lastPosition);
        }
        dataAdapter.notifyDataSetChanged();

        //showRecyclerHidingLoadMessage();
        if (gridRecyclerView.getVisibility() != View.VISIBLE) {
            circlingProgressBar.setVisibility(View.GONE);
            loadingTextView.setVisibility(View.GONE);
            gridRecyclerView.setVisibility(View.VISIBLE);
        }
        messageTextView.setVisibility(View.GONE);

        if(mListener!=null) mListener.readyGUIfor(FragmentKind.NEARBY_STOPS);
    }

    private void showArrivalsInRecycler(List<Palina> palinas){
        Collections.sort(palinas,new StopSorterByDistance(lastPosition));

        final ArrayList<Pair<Stop,Route>> routesPairList = new ArrayList<>(10);
        //int maxNum = Math.min(MAX_STOPS, stopList.size());
        for(Palina p: palinas){
            //if there are no routes available, skip stop
            if(p.queryAllRoutes().isEmpty()) continue;
            for(Route r: p.queryAllRoutes()){
                //if there are no routes, should not do anything
                if (r.passaggi != null && !r.passaggi.isEmpty())
                    routesPairList.add(new Pair<>(p,r));
            }
        }
        if (getContext()==null){
            Log.e(DEBUG_TAG, "Trying to show arrivals in Recycler but we're not attached");
            return;
        }
        if(firstLocForArrivals){
            arrivalsStopAdapter = new ArrivalsStopAdapter(routesPairList,mListener,getContext(),lastPosition);
            gridRecyclerView.setAdapter(arrivalsStopAdapter);
            firstLocForArrivals = false;
        } else {
            arrivalsStopAdapter.setRoutesPairListAndPosition(routesPairList,lastPosition);
        }

        //arrivalsStopAdapter.notifyDataSetChanged();

        showRecyclerHidingLoadMessage();
        if(mListener!=null) mListener.readyGUIfor(FragmentKind.NEARBY_ARRIVALS);

    }

    private void setNoStopsLayout(){
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(R.string.no_stops_nearby);
        circlingProgressBar.setVisibility(View.GONE);
        loadingTextView.setVisibility(View.GONE);
    }

    /**
     * Does exactly what is says on the tin
     */
    private void showRecyclerHidingLoadMessage(){
        if (gridRecyclerView.getVisibility() != View.VISIBLE) {
            circlingProgressBar.setVisibility(View.GONE);
            loadingTextView.setVisibility(View.GONE);
            gridRecyclerView.setVisibility(View.VISIBLE);
        }
        messageTextView.setVisibility(View.GONE);
    }

    /**
     * Local locationListener, to use for the GPS
     */
    class FragmentLocationListener implements LocationListenerCompat {

        private long lastUpdateTime = -1;
        public boolean isRegistered = false;

        @Override
        public void onLocationChanged(Location location) {
            //set adapter

            if(location==null){
                Log.e(DEBUG_TAG, "Location is null, cannot request stops");
                return;
            } else if(viewModel==null){
                return;
            }
            if(location.getAccuracy()<200 && !dbUpdateRunning) {
               if(viewModel.getDistanceMtLiveData().getValue()==null){
                    //never run request
                   distance = 40;
               }
               lastPosition = new GPSPoint(location.getLatitude(), location.getLongitude());
               viewModel.requestStopsAtDistance(location.getLatitude(), location.getLongitude(), distance, true);
            }
            lastUpdateTime = System.currentTimeMillis();
            Log.d("BusTO:NearPositListen","can start request for stops: "+ !dbUpdateRunning);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(DEBUG_TAG, "Location provider "+provider+" enabled");
            if(provider.equals(LocationManager.GPS_PROVIDER)){
                setShowingStatus(LocationShowingStatus.SEARCHING);
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(DEBUG_TAG, "Location provider "+provider+" disabled");
            if(provider.equals(LocationManager.GPS_PROVIDER)) {
               setShowingStatus(LocationShowingStatus.DISABLED);
            }
        }

        @Override
        public void onStatusChanged(@NonNull @NotNull String provider, int status, @Nullable @org.jetbrains.annotations.Nullable Bundle extras) {
            LocationListenerCompat.super.onStatusChanged(provider, status, extras);
        }
        /*
        @Override
        public void onLocationStatusChanged(int status) {
            switch(status){
                case AppLocationManager.LOCATION_GPS_AVAILABLE:
                    messageTextView.setVisibility(View.GONE);

                    break;
                case AppLocationManager.LOCATION_UNAVAILABLE:
                    messageTextView.setText(R.string.enableGpsText);
                    messageTextView.setVisibility(View.VISIBLE);
                    break;
                default:
                    Log.e(DEBUG_TAG,"Location status not recognized");
            }
        }

        @Override
        public @NotNull LocationCriteria getLocationCriteria() {

            return new LocationCriteria(200,TIME_INTERVAL_REQUESTS);
        }

        @Override
        public long getLastUpdateTimeMillis() {
            return lastUpdateTime;
        }
        void resetUpdateTime(){
            lastUpdateTime = -1;
        }

        @Override
        public void onLocationProviderAvailable() {

        }

        @Override
        public void onLocationDisabled() {

        }

         */
    }
}
