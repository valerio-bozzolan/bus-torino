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
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.volley.*;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.ArrivalsStopAdapter;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.backend.FiveTAPIFetcher.QueryType;
import it.reyboz.bustorino.middleware.AppLocationManager;
import it.reyboz.bustorino.data.AppDataProvider;
import it.reyboz.bustorino.data.NextGenDB.Contract.*;
import it.reyboz.bustorino.adapters.SquareStopAdapter;
import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.StopSorterByDistance;

import java.util.*;

public class NearbyStopsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private FragmentListenerMain mListener;
    private FragmentLocationListener fragmentLocationListener;
    private final String[] PROJECTION = {StopsTable.COL_ID,StopsTable.COL_LAT,StopsTable.COL_LONG,
            StopsTable.COL_NAME,StopsTable.COL_TYPE,StopsTable.COL_LINES_STOPPING};
    private final static String DEBUG_TAG = "NearbyStopsFragment";
    private final static String FRAGMENT_TYPE_KEY = "FragmentType";
    public final static int TYPE_STOPS = 19, TYPE_ARRIVALS = 20;
    private int fragment_type;

    public final static String FRAGMENT_TAG="NearbyStopsFrag";

    //data Bundle
    private final String BUNDLE_LOCATION =  "location";
    private final int LOADER_ID = 0;
    private RecyclerView gridRecyclerView;

    private SquareStopAdapter dataAdapter;
    private AutoFitGridLayoutManager gridLayoutManager;
    boolean canStartDBQuery = true;
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

    public NearbyStopsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment NearbyStopsFragment.
     */
    public static NearbyStopsFragment newInstance(int fragmentType) {
        if(fragmentType != TYPE_STOPS && fragmentType != TYPE_ARRIVALS )
            throw new IllegalArgumentException("WRONG KIND OF FRAGMENT USED");
        NearbyStopsFragment fragment = new NearbyStopsFragment();
        final Bundle args = new Bundle(1);
        args.putInt(FRAGMENT_TYPE_KEY,fragmentType);
        fragment.setArguments(args);
        return fragment;
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            setFragmentType(getArguments().getInt(FRAGMENT_TYPE_KEY));
        }
        locManager = AppLocationManager.getInstance(getContext());
        fragmentLocationListener = new FragmentLocationListener(this);
        globalSharedPref = getContext().getSharedPreferences(getString(R.string.mainSharedPreferences),Context.MODE_PRIVATE);


        globalSharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener);


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

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.d(DEBUG_TAG,"Key "+key+" was changed");
                if(key.equals(getString(R.string.databaseUpdatingPref))){
                    if(!sharedPreferences.getBoolean(getString(R.string.databaseUpdatingPref),true)){
                        canStartDBQuery = true;
                        Log.d(DEBUG_TAG,"The database has finished updating, can start update now");
                    }
                }
            }
        };
        scrollListener = new CommonScrollListener(mListener,false);
        switchButton.setOnClickListener(v -> {
           switchFragmentType();
        });
        Log.d(DEBUG_TAG, "onCreateView");
        return root;
    }

    protected ArrayList<Stop> createStopListFromCursor(Cursor data){
        ArrayList<Stop> stopList = new ArrayList<>();
        final int col_id = data.getColumnIndex(StopsTable.COL_ID);
        final int latInd = data.getColumnIndex(StopsTable.COL_LAT);
        final int lonInd = data.getColumnIndex(StopsTable.COL_LONG);
        final int nameindex = data.getColumnIndex(StopsTable.COL_NAME);
        final int typeIndex = data.getColumnIndex(StopsTable.COL_TYPE);
        final int linesIndex = data.getColumnIndex(StopsTable.COL_LINES_STOPPING);

        data.moveToFirst();
        for(int i=0; i<data.getCount();i++){
            String[] routes = data.getString(linesIndex).split(",");

            stopList.add(new Stop(data.getString(col_id),data.getString(nameindex),null,null,
                            Route.Type.fromCode(data.getInt(typeIndex)),
                            Arrays.asList(routes), //the routes should be compact, not normalized yet
                            data.getDouble(latInd),data.getDouble(lonInd)
                    )
            );
            //Log.d("NearbyStopsFragment","Got stop with id "+data.getString(col_id)+
            //" and name "+data.getString(nameindex));
            data.moveToNext();
        }
        return stopList;
    }

    /**
     * Use this method to set the fragment type
     * @param type the type, TYPE_ARRIVALS or TYPE_STOPS
     */
    private void setFragmentType(int type){
        if(type!=TYPE_ARRIVALS && type !=TYPE_STOPS)
            throw new IllegalArgumentException("type not recognized");
        this.fragment_type = type;
        switch(type){
            case TYPE_ARRIVALS:

                TIME_INTERVAL_REQUESTS = 5*1000;
                break;
            case TYPE_STOPS:
                TIME_INTERVAL_REQUESTS = 1000;

        }
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        /// TODO: RISOLVERE PROBLEMA: il context qui e' l'Activity non il Fragment
        if (context instanceof FragmentListenerMain) {
            mListener = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
        Log.d(DEBUG_TAG, "OnAttach called");
    }

    @Override
    public void onPause() {
        super.onPause();
        canStartDBQuery = false;

        gridRecyclerView.setAdapter(null);
        locManager.removeLocationRequestFor(fragmentLocationListener);
        Log.d(DEBUG_TAG,"On paused called");
    }

    @Override
    public void onResume() {
        super.onResume();
        canStartDBQuery = !globalSharedPref.getBoolean(getString(R.string.databaseUpdatingPref),false);
        try{
            if(canStartDBQuery) locManager.addLocationRequestFor(fragmentLocationListener);
        } catch (SecurityException ex){
            //ignored
            //try another location provider
        }
        switch(fragment_type){
            case TYPE_STOPS:
                if(dataAdapter!=null){
                    gridRecyclerView.setAdapter(dataAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
                }
                break;
            case TYPE_ARRIVALS:
                if(arrivalsStopAdapter!=null){
                    gridRecyclerView.setAdapter(arrivalsStopAdapter);
                    circlingProgressBar.setVisibility(View.GONE);
                }
        }

        mListener.enableRefreshLayout(false);
        Log.d(DEBUG_TAG,"OnResume called");

        //Re-read preferences
        SharedPreferences shpr = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        //For some reason, they are all saved as strings
        MAX_DISTANCE = shpr.getInt(getString(R.string.pref_key_radius_recents),600);
        MIN_NUM_STOPS = Integer.parseInt(shpr.getString(getString(R.string.pref_key_num_recents),"10"));
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
        lastReceivedLocation = args.getParcelable(BUNDLE_LOCATION);
        Uri.Builder builder =  new Uri.Builder();
        builder.scheme("content").authority(AppDataProvider.AUTHORITY)
                .appendPath("stops").appendPath("location")
                .appendPath(String.valueOf(lastReceivedLocation.getLatitude()))
                .appendPath(String.valueOf(lastReceivedLocation.getLongitude()))
                .appendPath(String.valueOf(distance)); //distance
        CursorLoader cl = new CursorLoader(getContext(),builder.build(),PROJECTION,null,null,null);
        cl.setUpdateThrottle(2000);
        return cl;
    }


    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        if (0 > MAX_DISTANCE) throw new AssertionError();
        //Cursor might be null
        if(data==null){
            Log.e(DEBUG_TAG,"Null cursor, something really wrong happened");
            return;
        }
        if(!isDBUpdating() && (data.getCount()<MIN_NUM_STOPS || distance<=MAX_DISTANCE)){
            distance = distance*2;
            Bundle d = new Bundle();
            d.putParcelable(BUNDLE_LOCATION,lastReceivedLocation);
            getLoaderManager().restartLoader(LOADER_ID,d,this);
            return;
        }
        Log.d("LoadFromCursor","Number of nearby stops: "+data.getCount());
        ////////
        ArrayList<Stop> stopList = createStopListFromCursor(data);
        if(data.getCount()>0) {
            //quick trial to hopefully always get the stops in the correct order
            Collections.sort(stopList,new StopSorterByDistance(lastReceivedLocation));
            switch (fragment_type){
                case TYPE_STOPS:
                    showStopsInRecycler(stopList);
                    break;
                case TYPE_ARRIVALS:
                    arrivalsManager = new ArrivalsManager(stopList);
                    flatProgressBar.setVisibility(View.VISIBLE);
                    flatProgressBar.setProgress(0);
                    flatProgressBar.setIndeterminate(false);
                    //for the moment, be satisfied with only one location
                    //AppLocationManager.getInstance(getContext()).removeLocationRequestFor(fragmentLocationListener);
                    break;
                default:
            }

        } else {
            setNoStopsLayout();
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
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
        if(fragment_type==TYPE_ARRIVALS){
            setFragmentType(TYPE_STOPS);
            switchButton.setText(getString(R.string.show_arrivals));
            titleTextView.setText(getString(R.string.nearby_stops_message));
            if(arrivalsManager!=null)
                arrivalsManager.cancelAllRequests();
            if(dataAdapter!=null)
                gridRecyclerView.setAdapter(dataAdapter);

        } else if (fragment_type==TYPE_STOPS){
            setFragmentType(TYPE_ARRIVALS);
            titleTextView.setText(getString(R.string.nearby_arrivals_message));
            switchButton.setText(getString(R.string.show_stops));
            if(arrivalsStopAdapter!=null)
                gridRecyclerView.setAdapter(arrivalsStopAdapter);
        }
        fragmentLocationListener.lastUpdateTime = -1;
        locManager.removeLocationRequestFor(fragmentLocationListener);
        locManager.addLocationRequestFor(fragmentLocationListener);
    }

    //useful methods
    protected boolean isDBUpdating(){
        return globalSharedPref.getBoolean(getString(R.string.databaseUpdatingPref),false);
    }


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
                routesPairList.add(new Pair<>(p,r));
            }
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

    class ArrivalsManager implements FiveTAPIVolleyRequest.ResponseListener, Response.ErrorListener{
        final HashMap<String,Palina> mStops;
        final Map<String,List<Route>> routesToAdd = new HashMap<>();
        final static String REQUEST_TAG = "NearbyArrivals";
        private final QueryType[] types = {QueryType.ARRIVALS,QueryType.DETAILS};
        final NetworkVolleyManager volleyManager;
        private final int MAX_ARRIVAL_STOPS =35;
        int activeRequestCount = 0,reqErrorCount = 0, reqSuccessCount=0;

        ArrivalsManager(List<Stop> stops){
            mStops = new HashMap<>();
            volleyManager = NetworkVolleyManager.getInstance(getContext());

            for(Stop s: stops.subList(0,Math.min(stops.size(), MAX_ARRIVAL_STOPS))){
                mStops.put(s.ID,new Palina(s));
                for(QueryType t: types) {
                    final FiveTAPIVolleyRequest req = FiveTAPIVolleyRequest.getNewRequest(t, s.ID, this, this);
                    if (req != null) {
                        req.setTag(REQUEST_TAG);
                        volleyManager.addToRequestQueue(req);
                        activeRequestCount++;
                    }
                }
            }
            flatProgressBar.setMax(activeRequestCount);
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
        public void onResponse(Palina result, QueryType type) {
            //counter for requests
            activeRequestCount--;
            reqSuccessCount++;


            final Palina palinaInMap = mStops.get(result.ID);
            //palina cannot be null here
            //sorry for the brutal crash when it happens
            if(palinaInMap == null) throw new IllegalStateException("Cannot get the palina from the map");
            //necessary to split the Arrivals and Details cases
            switch (type){
                case ARRIVALS:
                    palinaInMap.addInfoFromRoutes(result.queryAllRoutes());
                    final List<Route> possibleRoutes = routesToAdd.get(result.ID);
                    if(possibleRoutes!=null) {
                        palinaInMap.addInfoFromRoutes(possibleRoutes);
                        routesToAdd.remove(result.ID);
                    }
                break;
                case DETAILS:
                    if(palinaInMap.queryAllRoutes().size()>0){
                        //merge the branches
                        palinaInMap.addInfoFromRoutes(result.queryAllRoutes());
                    } else {
                        routesToAdd.put(result.ID,result.queryAllRoutes());
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Wrong QueryType in onResponse");
            }

            final ArrayList<Palina> outList = new ArrayList<>();
            for(Palina p: mStops.values()){
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
            if(accuracy<60 && canStartDBQuery) {
                distance = 20;
                final Bundle msgBundle = new Bundle();
                msgBundle.putParcelable(BUNDLE_LOCATION,location);
                getLoaderManager().restartLoader(LOADER_ID,msgBundle,callbacks);
            }
            lastUpdateTime = System.currentTimeMillis();
            Log.d("BusTO:NearPositListen","can start loader "+ canStartDBQuery);
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

    /**
     * Simple trick to get an automatic number of columns (from https://www.journaldev.com/13792/android-gridlayoutmanager-example)
     *
     */
     class AutoFitGridLayoutManager extends GridLayoutManager {

        private int columnWidth;
        private boolean columnWidthChanged = true;

        public AutoFitGridLayoutManager(Context context, int columnWidth) {
            super(context, 1);

            setColumnWidth(columnWidth);
        }

        public void setColumnWidth(int newColumnWidth) {
            if (newColumnWidth > 0 && newColumnWidth != columnWidth) {
                columnWidth = newColumnWidth;
                columnWidthChanged = true;
            }
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            if (columnWidthChanged && columnWidth > 0) {
                int totalSpace;
                if (getOrientation() == VERTICAL) {
                    totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
                } else {
                    totalSpace = getHeight() - getPaddingTop() - getPaddingBottom();
                }
                int spanCount = Math.max(1, totalSpace / columnWidth);
                setSpanCount(spanCount);
                columnWidthChanged = false;
            }
            super.onLayoutChildren(recycler, state);
        }
    }
}
