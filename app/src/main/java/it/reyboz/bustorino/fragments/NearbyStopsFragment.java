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

import android.content.Context;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.volley.*;
import it.reyboz.bustorino.BuildConfig;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.ArrivalsStopAdapter;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.backend.FiveTAPIFetcher.QueryType;
import it.reyboz.bustorino.backend.mato.MapiArrivalRequest;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.data.NextGenDB;
import it.reyboz.bustorino.middleware.AppLocationManager;
import it.reyboz.bustorino.data.AppDataProvider;
import it.reyboz.bustorino.data.NextGenDB.Contract.*;
import it.reyboz.bustorino.adapters.SquareStopAdapter;
import it.reyboz.bustorino.middleware.AutoFitGridLayoutManager;
import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.StopSorterByDistance;

import java.util.*;

public class NearbyStopsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

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

    private FragmentListenerMain mListener;
    private FragmentLocationListener fragmentLocationListener;

    private final static String DEBUG_TAG = "NearbyStopsFragment";
    private final static String FRAGMENT_TYPE_KEY = "FragmentType";
    //public final static int TYPE_STOPS = 19, TYPE_ARRIVALS = 20;
    private FragType fragment_type = FragType.STOPS;

    public final static String FRAGMENT_TAG="NearbyStopsFrag";

    //data Bundle
    private final String BUNDLE_LOCATION =  "location";
    private final int LOADER_ID = 0;
    private RecyclerView gridRecyclerView;

    private SquareStopAdapter dataAdapter;
    private AutoFitGridLayoutManager gridLayoutManager;
    private Location lastReceivedLocation = null;
    private ProgressBar circlingProgressBar,flatProgressBar;
    private int distance;
    protected SharedPreferences globalSharedPref;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private TextView messageTextView,titleTextView;
    private CommonScrollListener scrollListener;
    private AppCompatButton switchButton;
    private boolean firstLocForStops = true,firstLocForArrivals = true;
    public static final int COLUMN_WIDTH_DP = 250;


    private Integer MAX_DISTANCE = -3;
    private int MIN_NUM_STOPS = -1;
    private int TIME_INTERVAL_REQUESTS = -1;
    private AppLocationManager locManager;

    //These are useful for the case of nearby arrivals
    private ArrivalsManager arrivalsManager = null;
    private ArrivalsStopAdapter arrivalsStopAdapter = null;

    private boolean dbUpdateRunning = false;

    private ArrayList<Stop> currentNearbyStops = new ArrayList<>();

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
        locManager = AppLocationManager.getInstance(getContext());
        fragmentLocationListener = new FragmentLocationListener(this);
        if (getContext()!=null) {
            globalSharedPref = getContext().getSharedPreferences(getString(R.string.mainSharedPreferences), Context.MODE_PRIVATE);
            globalSharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        }


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
        circlingProgressBar = root.findViewById(R.id.loadingBar);
        flatProgressBar = root.findViewById(R.id.horizontalProgressBar);
        messageTextView = root.findViewById(R.id.messageTextView);
        titleTextView = root.findViewById(R.id.titleTextView);
        switchButton = root.findViewById(R.id.switchButton);

        scrollListener = new CommonScrollListener(mListener,false);
        switchButton.setOnClickListener(v -> switchFragmentType());
        Log.d(DEBUG_TAG, "onCreateView");

        DatabaseUpdate.watchUpdateWorkStatus(getContext(), this, new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(List<WorkInfo> workInfos) {
                if(workInfos.isEmpty()) return;

                WorkInfo wi = workInfos.get(0);
                if (wi.getState() == WorkInfo.State.RUNNING && locManager.isRequesterRegistered(fragmentLocationListener)) {
                    locManager.removeLocationRequestFor(fragmentLocationListener);
                    dbUpdateRunning = true;
                } else if(!locManager.isRequesterRegistered(fragmentLocationListener)){
                    locManager.addLocationRequestFor(fragmentLocationListener);
                    dbUpdateRunning = false;
                }
            }
        });
        return root;
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


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        /// TODO: RISOLVERE PROBLEMA: il context qui e' l'Activity non il Fragment
        if (context instanceof FragmentListenerMain) {
            mListener = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context
                    + " must implement OnFragmentInteractionListener");
        }
        Log.d(DEBUG_TAG, "OnAttach called");
    }

    @Override
    public void onPause() {
        super.onPause();

        gridRecyclerView.setAdapter(null);
        locManager.removeLocationRequestFor(fragmentLocationListener);
        Log.d(DEBUG_TAG,"On paused called");
    }

    @Override
    public void onResume() {
        super.onResume();
        try{
            if(!dbUpdateRunning && !locManager.isRequesterRegistered(fragmentLocationListener))
                    locManager.addLocationRequestFor(fragmentLocationListener);
        } catch (SecurityException ex){
            //ignored
            //try another location provider
        }
        switch(fragment_type){
            case STOPS:
                if(dataAdapter!=null){
                    gridRecyclerView.setAdapter(dataAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
                }
                break;
            case ARRIVALS:
                if(arrivalsStopAdapter!=null){
                    gridRecyclerView.setAdapter(arrivalsStopAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
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
            Log.d(DEBUG_TAG, "Max distance for stops: "+MAX_DISTANCE+
                    ", Min number of stops: "+MIN_NUM_STOPS);
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

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //BUILD URI
        if (args!=null)
            lastReceivedLocation = args.getParcelable(BUNDLE_LOCATION);
        Uri.Builder builder =  new Uri.Builder();
        builder.scheme("content").authority(AppDataProvider.AUTHORITY)
                .appendPath("stops").appendPath("location")
                .appendPath(String.valueOf(lastReceivedLocation.getLatitude()))
                .appendPath(String.valueOf(lastReceivedLocation.getLongitude()))
                .appendPath(String.valueOf(distance)); //distance
        CursorLoader cl = new CursorLoader(getContext(),builder.build(),NextGenDB.QUERY_COLUMN_stops_all,null,null,null);
        cl.setUpdateThrottle(2000);
        return cl;
    }


    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        if (0 > MAX_DISTANCE) throw new AssertionError();
        //Cursor might be null
        if (cursor == null) {
            Log.e(DEBUG_TAG, "Null cursor, something really wrong happened");
            return;
        }
        Log.d(DEBUG_TAG, "Num stops found: " + cursor.getCount() + ", Current distance: " + distance);

        if (!dbUpdateRunning && (cursor.getCount() < MIN_NUM_STOPS && distance <= MAX_DISTANCE)) {
            distance = distance * 2;
            Bundle d = new Bundle();
            d.putParcelable(BUNDLE_LOCATION, lastReceivedLocation);
            getLoaderManager().restartLoader(LOADER_ID, d, this);
            //Log.d(DEBUG_TAG, "Doubling distance now!");
            return;
        }
        Log.d("LoadFromCursor", "Number of nearby stops: " + cursor.getCount());
        ////////
        if(cursor.getCount()>0)
            currentNearbyStops = NextGenDB.getStopsFromCursorAllFields(cursor);

        showCurrentStops();
    }

    /**
     * Display the stops, or run new set of requests for arrivals
     */
    private void showCurrentStops(){
        if (currentNearbyStops.isEmpty()) {
            setNoStopsLayout();
            return;
        }

        double minDistance = Double.POSITIVE_INFINITY;
        for(Stop s: currentNearbyStops){
            minDistance = Math.min(minDistance, s.getDistanceFromLocation(lastReceivedLocation));
        }


        //quick trial to hopefully always get the stops in the correct order
        Collections.sort(currentNearbyStops,new StopSorterByDistance(lastReceivedLocation));
        switch (fragment_type){
            case STOPS:
                showStopsInRecycler(currentNearbyStops);
                break;
            case ARRIVALS:
                arrivalsManager = new ArrivalsManager(currentNearbyStops);
                flatProgressBar.setVisibility(View.VISIBLE);
                flatProgressBar.setProgress(0);
                flatProgressBar.setIndeterminate(false);
                //for the moment, be satisfied with only one location
                //AppLocationManager.getInstance(getContext()).removeLocationRequestFor(fragmentLocationListener);
                break;
            default:
        }

    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
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
        if(fragment_type==FragType.ARRIVALS){
            setFragmentType(FragType.STOPS);
            switchButton.setText(getString(R.string.show_arrivals));
            titleTextView.setText(getString(R.string.nearby_stops_message));
            if(arrivalsManager!=null)
                arrivalsManager.cancelAllRequests();
            if(dataAdapter!=null)
                gridRecyclerView.setAdapter(dataAdapter);

        } else if (fragment_type==FragType.STOPS){
            setFragmentType(FragType.ARRIVALS);
            titleTextView.setText(getString(R.string.nearby_arrivals_message));
            switchButton.setText(getString(R.string.show_stops));
            if(arrivalsStopAdapter!=null)
                gridRecyclerView.setAdapter(arrivalsStopAdapter);
        }
        fragmentLocationListener.lastUpdateTime = -1;
        //locManager.removeLocationRequestFor(fragmentLocationListener);
        //locManager.addLocationRequestFor(fragmentLocationListener);
        showCurrentStops();
    }

    //useful methods

    /////// GUI METHODS ////////
    private void showStopsInRecycler(List<Stop> stops){

        if(firstLocForStops) {
            dataAdapter = new SquareStopAdapter(stops, mListener, lastReceivedLocation);
            gridRecyclerView.setAdapter(dataAdapter);
            firstLocForStops = false;
        }else {
            dataAdapter.setStops(stops);
            dataAdapter.setUserPosition(lastReceivedLocation);
        }
        dataAdapter.notifyDataSetChanged();

        //showRecyclerHidingLoadMessage();
        if (gridRecyclerView.getVisibility() != View.VISIBLE) {
            circlingProgressBar.setVisibility(View.GONE);
            gridRecyclerView.setVisibility(View.VISIBLE);
        }
        messageTextView.setVisibility(View.GONE);

        if(mListener!=null) mListener.readyGUIfor(FragmentKind.NEARBY_STOPS);
    }

    private void showArrivalsInRecycler(List<Palina> palinas){
        Collections.sort(palinas,new StopSorterByDistance(lastReceivedLocation));

        final ArrayList<Pair<Stop,Route>> routesPairList = new ArrayList<>(10);
        //int maxNum = Math.min(MAX_STOPS, stopList.size());
        for(Palina p: palinas){
            //if there are no routes available, skip stop
            if(p.queryAllRoutes().size() == 0) continue;
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
            arrivalsStopAdapter = new ArrivalsStopAdapter(routesPairList,mListener,getContext(),lastReceivedLocation);
            gridRecyclerView.setAdapter(arrivalsStopAdapter);
            firstLocForArrivals = false;
        } else {
            arrivalsStopAdapter.setRoutesPairListAndPosition(routesPairList,lastReceivedLocation);
        }

        //arrivalsStopAdapter.notifyDataSetChanged();

        showRecyclerHidingLoadMessage();
        if(mListener!=null) mListener.readyGUIfor(FragmentKind.NEARBY_ARRIVALS);

    }

    private void setNoStopsLayout(){
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(R.string.no_stops_nearby);
        circlingProgressBar.setVisibility(View.GONE);
    }

    /**
     * Does exactly what is says on the tin
     */
    private void showRecyclerHidingLoadMessage(){
        if (gridRecyclerView.getVisibility() != View.VISIBLE) {
            circlingProgressBar.setVisibility(View.GONE);
            gridRecyclerView.setVisibility(View.VISIBLE);
        }
        messageTextView.setVisibility(View.GONE);
    }

    class ArrivalsManager implements Response.Listener<Palina>, Response.ErrorListener{
        final HashMap<String,Palina> palinasDone = new HashMap<>();
        //final Map<String,List<Route>> routesToAdd = new HashMap<>();
        final static String REQUEST_TAG = "NearbyArrivals";
        final NetworkVolleyManager volleyManager;
        int activeRequestCount = 0,reqErrorCount = 0, reqSuccessCount=0;

        ArrivalsManager(List<Stop> stops){
            volleyManager = NetworkVolleyManager.getInstance(getContext());

            int MAX_ARRIVAL_STOPS = 35;
            Date currentDate = new Date();
            int timeRange = 3600;
            int departures = 10;
            int numreq = 0;
            for(Stop s: stops.subList(0,Math.min(stops.size(), MAX_ARRIVAL_STOPS))){

                final MapiArrivalRequest req = new MapiArrivalRequest(s.ID, currentDate, timeRange, departures, this, this);
                req.setTag(REQUEST_TAG);
                volleyManager.addToRequestQueue(req);
                activeRequestCount++;
                numreq++;
            }
            flatProgressBar.setMax(numreq);
        }



        @Override
        public void onErrorResponse(VolleyError error) {
            if(error instanceof ParseError){
                //TODO
                Log.w(DEBUG_TAG,"Parsing error for stop request");
            } else if (error instanceof NetworkError){
                String s;
                if(error.networkResponse!=null)
                    s = new String(error.networkResponse.data);
                else s="";
                Log.w(DEBUG_TAG,"Network error: "+s);
            }else {
                Log.w(DEBUG_TAG,"Volley Error: "+error.getMessage());
            }
            if(error.networkResponse!=null){
                Log.w(DEBUG_TAG, "Error status code: "+error.networkResponse.statusCode);
            }
            //counters
            activeRequestCount--;
            reqErrorCount++;
            flatProgressBar.setProgress(reqErrorCount+reqSuccessCount);
        }

        @Override
        public void onResponse(Palina result) {
            //counter for requests
            activeRequestCount--;
            reqSuccessCount++;
            //final Palina palinaInMap = palinasDone.get(result.ID);
            //palina cannot be null here
            //sorry for the brutal crash when it happens
            //if(palinaInMap == null) throw new IllegalStateException("Cannot get the palina from the map");
            //add the palina to the successful one
            //TODO: Avoid redoing everything every time a new Result arrives
            palinasDone.put(result.ID, result);
            final ArrayList<Palina> outList = new ArrayList<>();
            for(Palina p: palinasDone.values()){
                final List<Route> routes = p.queryAllRoutes();
                if(routes!=null && routes.size()>0) outList.add(p);
            }
            showArrivalsInRecycler(outList);
            flatProgressBar.setProgress(reqErrorCount+reqSuccessCount);
            if(activeRequestCount==0) {
                flatProgressBar.setIndeterminate(true);
                flatProgressBar.setVisibility(View.GONE);
            }
        }
        void cancelAllRequests(){
            volleyManager.getRequestQueue().cancelAll(REQUEST_TAG);
            flatProgressBar.setVisibility(View.GONE);
        }
    }
    /**
     * Local locationListener, to use for the GPS
     */
    class FragmentLocationListener implements AppLocationManager.LocationRequester{

        LoaderManager.LoaderCallbacks<Cursor> callbacks;
        private int oldLocStatus = -2;
        private LocationCriteria cr;
        private long lastUpdateTime = -1;

        public FragmentLocationListener(LoaderManager.LoaderCallbacks<Cursor> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void onLocationChanged(Location location) {
            //set adapter
            float accuracy = location.getAccuracy();
            if(accuracy<100 && !dbUpdateRunning) {
                distance = 20;
                final Bundle msgBundle = new Bundle();
                msgBundle.putParcelable(BUNDLE_LOCATION,location);
                getLoaderManager().restartLoader(LOADER_ID,msgBundle,callbacks);
            }
            lastUpdateTime = System.currentTimeMillis();
            Log.d("BusTO:NearPositListen","can start loader "+ !dbUpdateRunning);
        }

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
        public LocationCriteria getLocationCriteria() {

            return new LocationCriteria(120,TIME_INTERVAL_REQUESTS);
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
    }
}
