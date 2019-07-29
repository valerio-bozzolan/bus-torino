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
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ProgressBar;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.middleware.AppDataProvider;
import it.reyboz.bustorino.middleware.NextGenDB.Contract.*;
import it.reyboz.bustorino.adapters.SquareStopAdapter;
import it.reyboz.bustorino.util.StopSorterByDistance;

import java.util.*;

public class NearbyStopsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private FragmentListener mListener;
    private LocationManager locManager;
    private FragmentLocationListener locationListener;
    private final String[] PROJECTION = {StopsTable.COL_ID,StopsTable.COL_LAT,StopsTable.COL_LONG,
            StopsTable.COL_NAME,StopsTable.COL_TYPE,StopsTable.COL_LINES_STOPPING};
    private final static String DEBUG_TAG = "NearbyStopsFragment";

    //data Bundle
    private final String BUNDLE_LOCATION =  "location";
    private final int LOADER_ID = 0;
    private RecyclerView gridRecyclerView;

    private SquareStopAdapter dataAdapter;
    private AutoFitGridLayoutManager gridLayoutManager;
    boolean canStartDBQuery = true;
    private Location lastReceivedLocation = null;
    private ProgressBar loadingProgressBar;
    private int distance;
    private SharedPreferences globalSharedPref;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private TextView messageTextView;
    private CommonScrollListener scrollListener;
    private boolean firstLoc = true;
    public static final int COLUMN_WIDTH_DP = 250;

    private Integer MAX_DISTANCE = -3;
    private int MIN_NUM_STOPS = -1;

    public NearbyStopsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment NearbyStopsFragment.
     */
    public static NearbyStopsFragment newInstance() {
        NearbyStopsFragment fragment = new NearbyStopsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            /*
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
            */
        }
        locManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        locationListener = new FragmentLocationListener(this);
        globalSharedPref = getContext().getSharedPreferences(getString(R.string.mainSharedPreferences),Context.MODE_PRIVATE);


        globalSharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_nearby_stops, container, false);
        gridRecyclerView = (RecyclerView) root.findViewById(R.id.stopGridRecyclerView);
        gridLayoutManager = new AutoFitGridLayoutManager(getContext().getApplicationContext(), utils.convertDipToPixels(getContext(),COLUMN_WIDTH_DP));
        gridRecyclerView.setLayoutManager(gridLayoutManager);
        gridRecyclerView.setHasFixedSize(false);
        loadingProgressBar  = (ProgressBar) root.findViewById(R.id.loadingBar);
        messageTextView = (TextView) root.findViewById(R.id.messageTextView);
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
        return root;
    }



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof FragmentListener) {
            mListener = (FragmentListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        canStartDBQuery = false;
        locManager.removeUpdates(locationListener);
        gridRecyclerView.setAdapter(null);
        Log.d(DEBUG_TAG,"On paused called");
    }

    @Override
    public void onResume() {
        super.onResume();
        canStartDBQuery = !globalSharedPref.getBoolean(getString(R.string.databaseUpdatingPref),false);
        try{
            if(canStartDBQuery) locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,5,locationListener);
        } catch (SecurityException ex){
            //ignored
            //try another location provider
        }
        if(dataAdapter!=null){
            gridRecyclerView.setAdapter(dataAdapter);
            loadingProgressBar.setVisibility(View.GONE);
        }
        Log.d(DEBUG_TAG,"OnResume called");

        //Re-read preferences
        SharedPreferences shpr = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        //For some reason, they are all saved as strings
        MAX_DISTANCE = shpr.getInt(getString(R.string.pref_key_radius_recents),1000);
        MIN_NUM_STOPS = Integer.parseInt(shpr.getString(getString(R.string.pref_key_num_recents),"12"));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        gridRecyclerView.setVisibility(View.INVISIBLE);
        gridRecyclerView.addOnScrollListener(scrollListener);

    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

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
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        ArrayList<Stop> stopList = new ArrayList<>();
        data.moveToFirst();
        if (0 > MAX_DISTANCE) throw new AssertionError();
        if(!globalSharedPref.getBoolean(getString(R.string.databaseUpdatingPref),false) && (data.getCount()<MIN_NUM_STOPS || distance<=MAX_DISTANCE)){
            distance = distance*2;
            Bundle d = new Bundle();
            d.putParcelable(BUNDLE_LOCATION,lastReceivedLocation);
            getLoaderManager().restartLoader(LOADER_ID,d,this);
            return;
        }
        Log.d("LoadFromCursor","Number of nearby stops: "+data.getCount());
        final int col_id = data.getColumnIndex(StopsTable.COL_ID);
        final int latInd = data.getColumnIndex(StopsTable.COL_LAT);
        final int lonInd = data.getColumnIndex(StopsTable.COL_LONG);
        final int nameindex = data.getColumnIndex(StopsTable.COL_NAME);
        final int typeIndex = data.getColumnIndex(StopsTable.COL_TYPE);
        final int linesIndex = data.getColumnIndex(StopsTable.COL_LINES_STOPPING);
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
        if(data.getCount()>0) {
            //quick trial to hopefully always get the stops in the correct order
            Collections.sort(stopList,new StopSorterByDistance(lastReceivedLocation));

            if(firstLoc) {
                dataAdapter = new SquareStopAdapter(stopList, mListener, lastReceivedLocation);
                gridRecyclerView.setAdapter(dataAdapter);
                firstLoc = false;
            }else {
                dataAdapter.setStops(stopList);
                dataAdapter.setUserPosition(lastReceivedLocation);
            }
            dataAdapter.notifyDataSetChanged();
            if (gridRecyclerView.getVisibility() != View.VISIBLE) {
                loadingProgressBar.setVisibility(View.GONE);
                gridRecyclerView.setVisibility(View.VISIBLE);
            }
            messageTextView.setVisibility(View.GONE);
        } else {
            messageTextView.setVisibility(View.VISIBLE);
            messageTextView.setText(R.string.no_stops_nearby);
            loadingProgressBar.setVisibility(View.GONE);
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    /**
     * Local locationListener, to use for the GPS
     */
    class FragmentLocationListener implements LocationListener{

        LoaderManager.LoaderCallbacks<Cursor> callbacks;
        private int oldLocStatus = -2;

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
            Log.d("LocationListener","can start loader "+ canStartDBQuery);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if(oldLocStatus!=status){

                if(status == LocationProvider.OUT_OF_SERVICE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                    messageTextView.setText(R.string.enableGpsText);
                    messageTextView.setVisibility(View.VISIBLE);
                }else if(status == LocationProvider.AVAILABLE){
                    messageTextView.setVisibility(View.GONE);
                }
                oldLocStatus = status;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

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
