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

import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ProgressBar;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopSorterByDistance;
import it.reyboz.bustorino.middleware.AppDataProvider;
import it.reyboz.bustorino.middleware.NextGenDB.Contract.*;
import it.reyboz.bustorino.middleware.SquareStopAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class NearbyStopsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private FragmentListener mListener;
    private LocationManager locManager;
    private FragmentLocationListener locationListener;
    private final String[] PROJECTION = {StopsTable.COL_ID,StopsTable.COL_LAT,StopsTable.COL_LONG,
            StopsTable.COL_NAME,StopsTable.COL_TYPE,StopsTable.COL_LINES_STOPPING};
    //needed for the @SimpleCursorAdapter
    private final String[] bindFrom = {StopsTable.COL_ID,StopsTable.COL_NAME,StopsTable.COL_LINES_STOPPING};
    private final int[] bindTo = {R.id.busStopIDView,R.id.stopNameView,R.id.routesStoppingTextView};
    //data Bundle
    private final String BUNDLE_LOCATION =  "location";
    private final int LOADER_ID = 0;
    private GridView gV;

    private SquareStopAdapter madapter;
    boolean canStartUpdate = true;
    private Location lastReceivedLocation = null;
    private ProgressBar loadingProgressBar;
    private int distance;

    public NearbyStopsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment NearbyStopsFragment.
     */
    // TODO: Rename and change types and number of parameters
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_nearby_stops, container, false);
        gV = (GridView) root.findViewById(R.id.stopGridNearby);
        loadingProgressBar  = (ProgressBar) root.findViewById(R.id.loadingBar);

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
        canStartUpdate = false;
        locManager.removeUpdates(locationListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        canStartUpdate = true;
        try{
            locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,10,locationListener);
        } catch (SecurityException ex){
            //ignored
            //try another location provider
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        gV.setVisibility(View.INVISIBLE);
        gV.setOnScrollListener(new CommonScrollListener(mListener,false));

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
        cl.setUpdateThrottle(1000);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        ArrayList<Stop> stopList = new ArrayList<>();
        data.moveToFirst();
        if(data.getCount()<4){
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
                            Route.Type.fromCode(data.getInt(typeIndex)), Arrays.asList(routes),
                            data.getDouble(latInd),data.getDouble(lonInd)
                    )
            );
            Log.d("NearbyStopsFragment","Got stop with id "+data.getString(col_id)+
            " and name "+data.getString(nameindex));
            data.moveToNext();
        }
        madapter = new SquareStopAdapter(stopList,getContext(),mListener,lastReceivedLocation);
        madapter.sort(new StopSorterByDistance(lastReceivedLocation));
        gV.setAdapter(madapter);
        if(gV.getVisibility()!=View.VISIBLE){
            loadingProgressBar.setVisibility(View.GONE);
            gV.setVisibility(View.VISIBLE);

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

        public FragmentLocationListener(LoaderManager.LoaderCallbacks<Cursor> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void onLocationChanged(Location location) {
            //set adapter
            float accuracy = location.getAccuracy();
            if(accuracy<60 && canStartUpdate) {
                distance = 100;
                final Bundle setting = new Bundle();
                setting.putParcelable(BUNDLE_LOCATION,location);
                getLoaderManager().restartLoader(LOADER_ID,setting,callbacks);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }
}
