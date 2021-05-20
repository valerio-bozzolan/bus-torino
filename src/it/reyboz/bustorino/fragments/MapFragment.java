package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;

import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.data.NextGenDB;
import it.reyboz.bustorino.map.CustomInfoWindow;
import it.reyboz.bustorino.map.LocationOverlay;
import it.reyboz.bustorino.middleware.GeneralActivity;

import static it.reyboz.bustorino.util.Permissions.PERMISSION_REQUEST_POSITION;

public class MapFragment extends BaseFragment {

    private static final String TAG = "Busto-MapActivity";
    private static final String MAP_CURRENT_ZOOM_KEY = "map-current-zoom";
    private static final String MAP_CENTER_LAT_KEY = "map-center-lat";
    private static final String MAP_CENTER_LON_KEY = "map-center-lon";
    private static final String FOLLOWING_LOCAT_KEY ="following";

    public static final String BUNDLE_LATIT = "lat";
    public static final String BUNDLE_LONGIT = "lon";
    public static final String BUNDLE_NAME = "name";
    public static final String BUNDLE_ID = "ID";

    public static final String FRAGMENT_TAG="BusTOMapFragment";


    private static final double DEFAULT_CENTER_LAT = 45.0708;
    private static final double DEFAULT_CENTER_LON = 7.6858;
    private static final double POSITION_FOUND_ZOOM = 18.3;

    private static final String DEBUG_TAG=FRAGMENT_TAG;

    protected FragmentListenerMain listenerMain;

    private HashSet<String> shownStops = null;


    private MapView map = null;
    public Context ctx;
    private LocationOverlay mLocationOverlay = null;
    private FolderOverlay stopsFolderOverlay = null;
    private Bundle savedMapState = null;
    protected ImageButton btCenterMap;
    protected ImageButton btFollowMe;
    private boolean followingLocation = false;

    protected final CustomInfoWindow.TouchResponder responder = new CustomInfoWindow.TouchResponder() {
        @Override
        public void onActionUp(@NonNull String stopID, @Nullable String stopName) {
            if (listenerMain!= null){
                listenerMain.requestArrivalsForStopID(stopID);
            }
        }
    };
    protected final LocationOverlay.OverlayCallbacks locationCallbacks = new LocationOverlay.OverlayCallbacks() {
        @Override
        public void onDisableFollowMyLocation() {
            updateGUIForLocationFollowing(false);
            followingLocation=false;
        }

        @Override
        public void onEnableFollowMyLocation() {
            updateGUIForLocationFollowing(true);
            followingLocation=true;
        }
    };

    public MapFragment() {
    }
    public static MapFragment getInstance(){
        return new MapFragment();
    }
    public static MapFragment getInstance(double stopLatit, double stopLong, String stopName, String stopID){
        MapFragment fragment= new MapFragment();
        Bundle args = new Bundle();
        args.putDouble(BUNDLE_LATIT, stopLatit);
        args.putDouble(BUNDLE_LONGIT, stopLong);
        args.putString(BUNDLE_NAME, stopName);
        args.putString(BUNDLE_ID, stopID);
        fragment.setArguments(args);

        return fragment;
    }
    public static MapFragment getInstance(Stop stop){
        return getInstance(stop.getLatitude(), stop.getLongitude(), stop.getStopDisplayName(), stop.ID);
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //use the same layout as the activity
        View root = inflater.inflate(R.layout.activity_map, container, false);
        if (getContext() == null){
            throw new IllegalStateException();
        }
        ctx = getContext().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        map = root.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        //map.setTilesScaledToDpi(true);
        map.setFlingEnabled(true);

        // add ability to zoom with 2 fingers
        map.setMultiTouchControls(true);

        btCenterMap = root.findViewById(R.id.ic_center_map);
        btFollowMe = root.findViewById(R.id.ic_follow_me);

        //setup FolderOverlay
        stopsFolderOverlay = new FolderOverlay();


        //Start map from bundle
        if (savedInstanceState !=null)
            startMap(getArguments(), savedInstanceState);
        else startMap(getArguments(), savedMapState);
        //set listeners
        map.addMapListener(new DelayedMapListener(new MapListener() {

            @Override
            public boolean onScroll(ScrollEvent paramScrollEvent) {
                requestStopsToShow();
                //Log.d(DEBUG_TAG, "Scrolling");
                //if (moveTriggeredByCode) moveTriggeredByCode =false;
                //else setLocationFollowing(false);
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                requestStopsToShow();
                return true;
            }

        }));


        btCenterMap.setOnClickListener(v -> {
            //Log.i(TAG, "centerMap clicked ");
            final GeoPoint myPosition = mLocationOverlay.getMyLocation();
            map.getController().animateTo(myPosition);
        });

        btFollowMe.setOnClickListener(v -> {
            //Log.i(TAG, "btFollowMe clicked ");
            switchLocationFollowing(!followingLocation);
        });

        return root;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof FragmentListenerMain) {
            listenerMain = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement FragmentListenerMain");
        }
    }
    @Override
    public void onDetach() {
        super.onDetach();
        listenerMain = null;
        //    setupOnAttached = true;
        Log.w(DEBUG_TAG, "Fragment detached");
    }

    @Override
    public void onPause() {
        super.onPause();
        saveMapState();
    }

    /**
     * Save the map state inside the fragment
     * (calls saveMapState(bundle))
     */
    private void saveMapState(){
        savedMapState = new Bundle();
        saveMapState(savedMapState);

    }

    /**
     * Save the state of the map to restore it to a later time
     * @param bundle the bundle in which to save the data
     */
    private void saveMapState(Bundle bundle){
        final IGeoPoint loc = map.getMapCenter();
        bundle.putDouble(MAP_CENTER_LAT_KEY, loc.getLatitude());
        bundle.putDouble(MAP_CENTER_LON_KEY, loc.getLongitude());
        bundle.putDouble(MAP_CURRENT_ZOOM_KEY, map.getZoomLevelDouble());
        Log.d(DEBUG_TAG, "Saving state, location following: "+followingLocation);
        bundle.putBoolean(FOLLOWING_LOCAT_KEY, followingLocation);

    }

    @Override
    public void onResume() {
        super.onResume();
        if(listenerMain!=null) listenerMain.readyGUIfor(FragmentKind.MAP);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        saveMapState(outState);

        super.onSaveInstanceState(outState);
    }

    //own methods

    /**
     * Switch following the location on and off
     * @param value true if we want to follow location
     */
    public void switchLocationFollowing(Boolean value){
        followingLocation = value;
        if (value){
            mLocationOverlay.enableFollowLocation();
        } else {
            mLocationOverlay.disableFollowLocation();
        }
    }

    /**
     * Do all the stuff you need to do on the gui, when parameter is changed to value
     * @param following value
     */
    protected void updateGUIForLocationFollowing(boolean following){
        if (following)
            btFollowMe.setImageResource(R.drawable.ic_follow_me_on);
        else
            btFollowMe.setImageResource(R.drawable.ic_follow_me);

    }

    public void startMap(Bundle incoming, Bundle savedInstanceState) {
        //Check that we're attached
        GeneralActivity activity = getActivity() instanceof GeneralActivity ? (GeneralActivity) getActivity() : null;
        if(getContext()==null|| activity==null){
            //we are not attached
            Log.e(DEBUG_TAG, "Calling startMap when not attached");
            return;
        }else{
            Log.d(DEBUG_TAG, "Starting map from scratch");
        }


        //parse incoming bundle
        GeoPoint marker = null;
        String name = null;
        String ID = null;
        if (incoming != null) {
            double lat = incoming.getDouble(BUNDLE_LATIT);
            double lon = incoming.getDouble(BUNDLE_LONGIT);
            marker = new GeoPoint(lat, lon);
            name = incoming.getString(BUNDLE_NAME);
            ID = incoming.getString(BUNDLE_ID);
        }

        shownStops = new HashSet<>();
        // move the map on the marker position or on a default view point: Turin, Piazza Castello
        // and set the start zoom
        IMapController mapController = map.getController();
        GeoPoint startPoint = null;

        boolean havePositionPermission = true;

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activity.askForPermissionIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_REQUEST_POSITION);
            havePositionPermission = false;
        }
        // Location Overlay
        // from OpenBikeSharing (THANK GOD)
        GpsMyLocationProvider imlp = new GpsMyLocationProvider(activity.getBaseContext());
        imlp.setLocationUpdateMinDistance(5);
        imlp.setLocationUpdateMinTime(2000);
        this.mLocationOverlay = new LocationOverlay(imlp,map, locationCallbacks);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.setOptionsMenuEnabled(true);

        if (marker != null) {
            startPoint = marker;
            mapController.setZoom(POSITION_FOUND_ZOOM);
            switchLocationFollowing(false);
        } else if (savedInstanceState != null) {
            mapController.setZoom(savedInstanceState.getDouble(MAP_CURRENT_ZOOM_KEY));
            mapController.setCenter(new GeoPoint(savedInstanceState.getDouble(MAP_CENTER_LAT_KEY),
                    savedInstanceState.getDouble(MAP_CENTER_LON_KEY)));
            Log.d(DEBUG_TAG, "Location following from savedInstanceState: "+savedInstanceState.getBoolean(FOLLOWING_LOCAT_KEY));
            switchLocationFollowing(savedInstanceState.getBoolean(FOLLOWING_LOCAT_KEY));
        } else {
            Log.d(DEBUG_TAG, "No position found from intent or saved state");
            boolean found = false;
            LocationManager locationManager =
                    (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {

                Location userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (userLocation != null) {
                    mapController.setZoom(POSITION_FOUND_ZOOM);
                    startPoint = new GeoPoint(userLocation);
                    found = true;
                    switchLocationFollowing(true);
                }
            }
            if(!found){
                startPoint = new GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON);
                mapController.setZoom(16.0);
                switchLocationFollowing(false);
            }
        }

        // set the minimum zoom level
        map.setMinZoomLevel(15.0);
        //add contingency check (shouldn't happen..., but)
        if (startPoint != null) {
            mapController.setCenter(startPoint);
        }



        map.getOverlays().add(this.mLocationOverlay);

        //add stops overlay
        map.getOverlays().add(this.stopsFolderOverlay);

        Log.d(DEBUG_TAG, "Requesting stops load");
        // This is not necessary, by setting the center we already move
        // the map and we trigger a stop request
        //requestStopsToShow();
        if (marker != null) {
            // make a marker with the info window open for the searched marker
            makeMarker(startPoint, name , ID, true);
        }

    }

    /**
     * Start a request to load the stops that are in the current view
     * from the database
     */
    private void requestStopsToShow(){
        // get the top, bottom, left and right screen's coordinate
        BoundingBox bb = map.getBoundingBox();
        double latFrom = bb.getLatSouth();
        double latTo = bb.getLatNorth();
        double lngFrom = bb.getLonWest();
        double lngTo = bb.getLonEast();

        new AsyncStopFetcher(this).execute(
                new AsyncStopFetcher.BoundingBoxLimit(lngFrom,lngTo,latFrom, latTo));
    }

    /**
     * Add stops as Markers on the map
     * @param stops the list of stops that must be included
     */
    protected void showStopsMarkers(List<Stop> stops){

        for (Stop stop : stops) {
            if (shownStops.contains(stop.ID)){
                continue;
            }
            if(stop.getLongitude()==null || stop.getLatitude()==null)
                continue;

            shownStops.add(stop.ID);
            GeoPoint marker = new GeoPoint(stop.getLatitude(), stop.getLongitude());
            Marker stopMarker = makeMarker(marker, stop.getStopDefaultName(), stop.ID, false);
            stopsFolderOverlay.add(stopMarker);
            if (!map.getOverlays().contains(stopsFolderOverlay)) {
                Log.w(DEBUG_TAG, "Map doesn't have folder overlay");
            }
        }
        //Log.d(DEBUG_TAG,"We have " +stopsFolderOverlay.getItems().size()+" stops in the folderOverlay");
        //force redraw of markers
        map.invalidate();
    }

    public Marker makeMarker(GeoPoint geoPoint, String stopName, String ID, boolean isStartMarker) {

        // add a marker
        Marker marker = new Marker(map);

        // set custom info window as info window
        CustomInfoWindow popup = new CustomInfoWindow(map, ID, stopName, responder);
        marker.setInfoWindow(popup);

        // make the marker clickable
        marker.setOnMarkerClickListener((thisMarker, mapView) -> {
            if (thisMarker.isInfoWindowOpen()) {
                // on second click
                //TODO: show the arrivals for the stop
                Log.w(DEBUG_TAG, "Pressed on the click marker");
            } else {
                // on first click

                // hide all opened info window
                InfoWindow.closeAllInfoWindowsOn(map);
                // show this particular info window
                thisMarker.showInfoWindow();
                // move the map to its position
                map.getController().animateTo(thisMarker.getPosition());
            }

            return true;
        });

        // set its position
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // add to it an icon
        //marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));

        marker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.bus_marker, ctx.getTheme()));
        // add to it a title
        marker.setTitle(stopName);
        // set the description as the ID
        marker.setSnippet(ID);

        // show popup info window of the searched marker
        if (isStartMarker) {
            marker.showInfoWindow();
        }

        return marker;
    }

    /**
     * Simple asyncTask class to load the stops in the background
     * Holds a weak reference to the fragment to do callbacks
     */
    static class AsyncStopFetcher extends AsyncTask<AsyncStopFetcher.BoundingBoxLimit,Void, List<Stop>>{

        final WeakReference<MapFragment> fragmentWeakReference;

        public AsyncStopFetcher(MapFragment fragment) {
            this.fragmentWeakReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Stop> doInBackground(BoundingBoxLimit... limits) {
            if(fragmentWeakReference.get()==null || fragmentWeakReference.get().getContext() == null){
                Log.w(DEBUG_TAG, "AsyncLoad fragmentWeakreference null");

                return null;

            }
            final BoundingBoxLimit limit = limits[0];
            //Log.d(DEBUG_TAG, "Async Stop Fetcher started working");

            NextGenDB dbHelper = new NextGenDB(fragmentWeakReference.get().getContext());
            Stop[] stops = dbHelper.queryAllInsideMapView(limit.latitFrom, limit.latitTo,
                    limit.longFrom, limit.latitTo);
            dbHelper.close();
            return Arrays.asList(stops);
        }

        @Override
        protected void onPostExecute(List<Stop> stops) {
            super.onPostExecute(stops);
            //Log.d(DEBUG_TAG, "Async Stop Fetcher has finished working");
            if(fragmentWeakReference.get()==null) {
                Log.w(DEBUG_TAG, "AsyncLoad fragmentWeakreference null");
                return;
            }
            Log.d(DEBUG_TAG, "AsyncLoad number of stops: "+stops.size());
            fragmentWeakReference.get().showStopsMarkers(stops);
        }

        private static class BoundingBoxLimit{
            final double longFrom, longTo, latitFrom, latitTo;

            public BoundingBoxLimit(double longFrom, double longTo, double latitFrom, double latitTo) {
                this.longFrom = longFrom;
                this.longTo = longTo;
                this.latitFrom = latitFrom;
                this.latitTo = latitTo;
            }
        }

    }
}
