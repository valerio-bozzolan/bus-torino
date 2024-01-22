/*
	BusTO  - Fragments components
    Copyright (C) 2020 Andrea Ugo
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
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;

import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate;
import it.reyboz.bustorino.backend.mato.MQTTMatoClient;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.MatoTripsDownloadWorker;
import it.reyboz.bustorino.data.gtfs.MatoPattern;
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops;
import it.reyboz.bustorino.map.*;
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel;
import it.reyboz.bustorino.viewmodels.StopsMapViewModel;
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

import java.util.*;

import kotlin.Pair;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.GeneralActivity;
import it.reyboz.bustorino.util.Permissions;

import static it.reyboz.bustorino.fragments.SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE;

public class MapFragment extends ScreenBaseFragment {

    //private static final String TAG = "Busto-MapActivity";
    private static final String MAP_CURRENT_ZOOM_KEY = "map-current-zoom";
    private static final String MAP_CENTER_LAT_KEY = "map-center-lat";
    private static final String MAP_CENTER_LON_KEY = "map-center-lon";
    private static final String FOLLOWING_LOCAT_KEY ="following";

    public static final String BUNDLE_LATIT = "lat";
    public static final String BUNDLE_LONGIT = "lon";
    public static final String BUNDLE_NAME = "name";
    public static final String BUNDLE_ID = "ID";
    public static final String BUNDLE_ROUTES_STOPPING = "routesStopping";

    public static final String FRAGMENT_TAG="BusTOMapFragment";


    private static final double DEFAULT_CENTER_LAT = 45.0708;
    private static final double DEFAULT_CENTER_LON = 7.6858;
    private static final double POSITION_FOUND_ZOOM = 18.3;
    public static final double NO_POSITION_ZOOM = 17.1;

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

    protected CoordinatorLayout coordLayout;
    private boolean hasMapStartFinished = false;
    private boolean followingLocation = false;

    //the ViewModel from which we get the stop to display in the map
    private StopsMapViewModel stopsViewModel;

    //private GtfsPositionsViewModel gtfsPosViewModel; //= new ViewModelProvider(this).get(MapViewModel.class);
    private LivePositionsViewModel livePositionsViewModel;
    private Boolean useMQTTViewModel = true;

    private final HashMap<String,Marker> busPositionMarkersByTrip = new HashMap<>();
    private FolderOverlay busPositionsOverlay = null;

    private final HashMap<String, ObjectAnimator> tripMarkersAnimators = new HashMap<>();

    protected final CustomInfoWindow.TouchResponder responder = new CustomInfoWindow.TouchResponder() {
        @Override
        public void onActionUp(@NonNull String stopID, @Nullable String stopName) {
            if (listenerMain!= null){
                Log.d(DEBUG_TAG, "Asked to show arrivals for stop ID: "+stopID);
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

    private final ActivityResultLauncher<String[]> positionRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result == null){
                    Log.w(DEBUG_TAG, "Got asked permission but request is null, doing nothing?");
                }
                else if(Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION)) &&
                        Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))){

                    map.getOverlays().remove(mLocationOverlay);
                    startLocationOverlay(true, map);
                    if(getContext()==null || getContext().getSystemService(Context.LOCATION_SERVICE)==null)
                        return;
                    LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
                    @SuppressLint("MissingPermission")
                    Location userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (userLocation != null) {
                        map.getController().setZoom(POSITION_FOUND_ZOOM);
                        GeoPoint startPoint = new GeoPoint(userLocation);
                        setLocationFollowing(true);
                        map.getController().setCenter(startPoint);
                    }
                }
                else Log.w(DEBUG_TAG,"No location permission");
            });

    public MapFragment() {
    }
    public static MapFragment getInstance(){
        return new MapFragment();
    }
    public static MapFragment getInstance(@NonNull Stop stop){
        MapFragment fragment= new MapFragment();
        Bundle args = new Bundle();
        args.putDouble(BUNDLE_LATIT, stop.getLatitude());
        args.putDouble(BUNDLE_LONGIT, stop.getLongitude());
        args.putString(BUNDLE_NAME, stop.getStopDisplayName());
        args.putString(BUNDLE_ID, stop.ID);
        args.putString(BUNDLE_ROUTES_STOPPING, stop.routesThatStopHereToString());
        fragment.setArguments(args);

        return fragment;
    }
    //public static MapFragment getInstance(@NonNull Stop stop){
     //   return getInstance(stop.getLatitude(), stop.getLongitude(), stop.getStopDisplayName(), stop.ID);
    //}


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //use the same layout as the activity
        View root = inflater.inflate(R.layout.fragment_map, container, false);
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

        btCenterMap = root.findViewById(R.id.icon_center_map);
        btFollowMe = root.findViewById(R.id.icon_follow);
        coordLayout = root.findViewById(R.id.coord_layout);

        //setup FolderOverlay
        stopsFolderOverlay = new FolderOverlay();
        //setup Bus Markers Overlay
        busPositionsOverlay = new FolderOverlay();
        //reset shown bus updates
        busPositionMarkersByTrip.clear();
        tripMarkersAnimators.clear();
        //set map not done
        hasMapStartFinished = false;

        String keySourcePositions=getString(R.string.pref_positions_source);
        useMQTTViewModel = (
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getString(keySourcePositions,LIVE_POSITIONS_PREF_MQTT_VALUE).contentEquals(LIVE_POSITIONS_PREF_MQTT_VALUE));


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
            if(Permissions.bothLocationPermissionsGranted(getContext())) {
                final GeoPoint myPosition = mLocationOverlay.getMyLocation();
                map.getController().animateTo(myPosition);
            } else
                Toast.makeText(getContext(), R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                        .show();
        });

        btFollowMe.setOnClickListener(v -> {
            //Log.i(TAG, "btFollowMe clicked ");
            if(Permissions.bothLocationPermissionsGranted(getContext()))
                setLocationFollowing(!followingLocation);
            else
                Toast.makeText(getContext(), R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                    .show();
        });


        return root;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        ViewModelProvider provider = new ViewModelProvider(this);
        //gtfsPosViewModel = provider.get(GtfsPositionsViewModel.class);
        livePositionsViewModel = provider.get(LivePositionsViewModel.class);
        stopsViewModel = provider.get(StopsMapViewModel.class);


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
        //stop animations

        //    setupOnAttached = true;
        Log.w(DEBUG_TAG, "Fragment detached");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.w(DEBUG_TAG, "On pause called mapfrag");
        saveMapState();
        for (ObjectAnimator animator : tripMarkersAnimators.values()) {
            if(animator!=null && animator.isRunning()){
                animator.cancel();
            }
        }
        tripMarkersAnimators.clear();
        if(useMQTTViewModel)   livePositionsViewModel.stopMatoUpdates();

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
        Log.d(DEBUG_TAG, "Saving state, location following: "+followingLocation);
        bundle.putBoolean(FOLLOWING_LOCAT_KEY, followingLocation);
        if (map == null){
            //The map is null, it  can happen?
            Log.e(DEBUG_TAG, "Cannot save map center, map is null");
            return;
        }
        final IGeoPoint loc = map.getMapCenter();
        bundle.putDouble(MAP_CENTER_LAT_KEY, loc.getLatitude());
        bundle.putDouble(MAP_CENTER_LON_KEY, loc.getLongitude());
        bundle.putDouble(MAP_CURRENT_ZOOM_KEY, map.getZoomLevelDouble());
    }


    @Override
    public void onResume() {
        super.onResume();
        //TODO: cleanup duplicate code (maybe merging the positions classes?)
        if(listenerMain!=null) listenerMain.readyGUIfor(FragmentKind.MAP);
        /// choose which to use
        String keySourcePositions=getString(R.string.pref_positions_source);
        useMQTTViewModel = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(keySourcePositions,LIVE_POSITIONS_PREF_MQTT_VALUE).contentEquals(
                        LIVE_POSITIONS_PREF_MQTT_VALUE);
        if(livePositionsViewModel !=null) {
            //gtfsPosViewModel.requestUpdates();
            if(useMQTTViewModel)
                livePositionsViewModel.requestMatoPosUpdates(MQTTMatoClient.LINES_ALL);
            else
                livePositionsViewModel.requestGTFSUpdates();
            //mapViewModel.testCascade();
            livePositionsViewModel.getTripsGtfsIDsToQuery().observe(this, dat -> {
                Log.i(DEBUG_TAG, "Have these trips IDs missing from the DB, to be queried: "+dat);
                //gtfsPosViewModel.downloadTripsFromMato(dat);
                MatoTripsDownloadWorker.Companion.downloadTripsFromMato(dat,
                        requireContext().getApplicationContext(),
                        "BusTO-MatoTripDownload");
            });
        } /*else if(gtfsPosViewModel!=null){
            gtfsPosViewModel.requestUpdates();
            gtfsPosViewModel.getTripsGtfsIDsToQuery().observe(this, dat -> {
                Log.i(DEBUG_TAG, "Have these trips IDs missing from the DB, to be queried: "+dat);
                //gtfsPosViewModel.downloadTripsFromMato(dat);
                MatoTripsDownloadWorker.Companion.downloadTripsFromMato(dat,getContext().getApplicationContext(),
                        "BusTO-MatoTripDownload");
            });
        }
        */
        else Log.e(DEBUG_TAG, "livePositionsViewModel is null at onResume");

        //rerequest stop
        stopsViewModel.requestStopsInBoundingBox(map.getBoundingBox());
    }

    private void startRequestsPositions(){
        if (livePositionsViewModel != null) {
            //should always be the case
            livePositionsViewModel.getUpdatesWithTripAndPatterns().observe(getViewLifecycleOwner(), data -> {
                Log.d(DEBUG_TAG, "Have " + data.size() + " trip updates, has Map start finished: " + hasMapStartFinished);
                if (hasMapStartFinished) updateBusPositionsInMap(data);

                if(!isDetached() && !useMQTTViewModel)
                    livePositionsViewModel.requestDelayedGTFSUpdates(3000);

            });

        } else {
            Log.e(DEBUG_TAG, "PositionsViewModel is null");
        }
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
    public void setLocationFollowing(Boolean value){
        followingLocation = value;
        if(mLocationOverlay==null || getContext() == null || map ==null)
            //nothing else to do
            return;
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

    /**
     * Build the location overlay. Enable only when
     * a) we know we have the permission
     * b) the location map is set
     */
    private void startLocationOverlay(boolean enableLocation, MapView map){
        if(getActivity()== null) throw new IllegalStateException("Cannot enable LocationOverlay now");
        // Location Overlay
        // from OpenBikeSharing (THANK GOD)
        Log.d(DEBUG_TAG, "Starting position overlay");
        GpsMyLocationProvider imlp = new GpsMyLocationProvider(getActivity().getBaseContext());
        imlp.setLocationUpdateMinDistance(5);
        imlp.setLocationUpdateMinTime(2000);

        final LocationOverlay overlay = new LocationOverlay(imlp,map, locationCallbacks);
        if (enableLocation) overlay.enableMyLocation();
        overlay.setOptionsMenuEnabled(true);

        //map.getOverlays().add(this.mLocationOverlay);
        this.mLocationOverlay = overlay;
        map.getOverlays().add(mLocationOverlay);
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
        //clear previous overlays
        map.getOverlays().clear();


        //parse incoming bundle
        GeoPoint marker = null;
        String name = null;
        String ID = null;
        String routesStopping = "";
        if (incoming != null) {
            double lat = incoming.getDouble(BUNDLE_LATIT);
            double lon = incoming.getDouble(BUNDLE_LONGIT);
            marker = new GeoPoint(lat, lon);
            name = incoming.getString(BUNDLE_NAME);
            ID = incoming.getString(BUNDLE_ID);
            routesStopping = incoming.getString(BUNDLE_ROUTES_STOPPING, "");
        }


       //ask for location permission
        if(!Permissions.bothLocationPermissionsGranted(activity)){
            if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
                //TODO: show dialog for permission rationale
                Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT).show();
            }
            positionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS);

        }

        shownStops = new HashSet<>();
        // move the map on the marker position or on a default view point: Turin, Piazza Castello
        // and set the start zoom
        IMapController mapController = map.getController();
        GeoPoint startPoint = null;
        startLocationOverlay(Permissions.bothLocationPermissionsGranted(activity),
                map);
        // set the center point
        if (marker != null) {
            //startPoint = marker;
            mapController.setZoom(POSITION_FOUND_ZOOM);
            setLocationFollowing(false);
            // put the center a little bit off (animate later)
            startPoint = new GeoPoint(marker);
            startPoint.setLatitude(marker.getLatitude()+ utils.angleRawDifferenceFromMeters(20));
            startPoint.setLongitude(marker.getLongitude()-utils.angleRawDifferenceFromMeters(20));
            //don't need to do all the rest since we want to show a point
        } else if (savedInstanceState != null && savedInstanceState.containsKey(MAP_CURRENT_ZOOM_KEY)) {
            mapController.setZoom(savedInstanceState.getDouble(MAP_CURRENT_ZOOM_KEY));
            mapController.setCenter(new GeoPoint(savedInstanceState.getDouble(MAP_CENTER_LAT_KEY),
                    savedInstanceState.getDouble(MAP_CENTER_LON_KEY)));
            Log.d(DEBUG_TAG, "Location following from savedInstanceState: "+savedInstanceState.getBoolean(FOLLOWING_LOCAT_KEY));
            setLocationFollowing(savedInstanceState.getBoolean(FOLLOWING_LOCAT_KEY));
        } else {
            Log.d(DEBUG_TAG, "No position found from intent or saved state");
            boolean found = false;
            LocationManager locationManager =
                    (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
            //check for permission
            if (locationManager != null && Permissions.bothLocationPermissionsGranted(activity)) {

                @SuppressLint("MissingPermission")
                Location userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (userLocation != null) {
                    double distan = utils.measuredistanceBetween(userLocation.getLatitude(), userLocation.getLongitude(),
                            DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON);
                    if (distan < 100_000.0) {
                        mapController.setZoom(POSITION_FOUND_ZOOM);
                        startPoint = new GeoPoint(userLocation);
                        found = true;
                        setLocationFollowing(true);
                    }
                }
            }
            if(!found){
                startPoint = new GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON);
                mapController.setZoom(NO_POSITION_ZOOM);
                setLocationFollowing(false);
            }
        }

        // set the minimum zoom level
        map.setMinZoomLevel(15.0);
        //add contingency check (shouldn't happen..., but)

        if (startPoint != null) {
            mapController.setCenter(startPoint);
        }


        //add stops overlay
        //map.getOverlays().add(mLocationOverlay);
        map.getOverlays().add(this.stopsFolderOverlay);

        Log.d(DEBUG_TAG, "Requesting stops load");
        // This is not necessary, by setting the center we already move
        // the map and we trigger a stop request
        //requestStopsToShow();
        if (marker != null) {
            // make a marker with the info window open for the searched marker
            //TODO:  make Stop Bundle-able
            Marker stopMarker = makeMarker(marker, ID , name, routesStopping,true);
            map.getController().animateTo(marker);
        }
        //add the overlays with the bus stops
        if(busPositionsOverlay == null){
            //Log.i(DEBUG_TAG, "Null bus positions overlay,redo");
            busPositionsOverlay = new FolderOverlay();
        }
        startRequestsPositions();
        if(stopsViewModel !=null){

            stopsViewModel.getStopsInBoundingBox().observe(getViewLifecycleOwner(),
                    this::showStopsMarkers
                    );
        } else Log.d(DEBUG_TAG, "Cannot observe new stops in map, stopsViewModel is null");
        map.getOverlays().add(this.busPositionsOverlay);
        //set map as started
        hasMapStartFinished = true;
    }

    /**
     * Start a request to load the stops that are in the current view
     * from the database
     */
    private void requestStopsToShow(){
        // get the top, bottom, left and right screen's coordinate
        BoundingBox bb = map.getBoundingBox();
        Log.d(DEBUG_TAG, "Requesting stops in bounding box, stopViewModel is null "+(stopsViewModel==null));
        if(stopsViewModel!=null){
            stopsViewModel.requestStopsInBoundingBox(bb);
        }
        /*double latFrom = bb.getLatSouth();
        double latTo = bb.getLatNorth();
        double lngFrom = bb.getLonWest();
        double lngTo = bb.getLonEast();
        if (stopFetcher!= null && stopFetcher.getStatus()!= AsyncTask.Status.FINISHED)
            stopFetcher.cancel(true);
        stopFetcher = new AsyncStopFetcher(this);
        stopFetcher.execute(
                new AsyncStopFetcher.BoundingBoxLimit(lngFrom,lngTo,latFrom, latTo));

         */
    }

    private void updateBusMarker(final Marker marker, final LivePositionUpdate posUpdate, @Nullable boolean justCreated){
        GeoPoint position;
        final String updateID = posUpdate.getTripID();
        if(!justCreated){
            position = marker.getPosition();
            if(posUpdate.getLatitude()!=position.getLatitude() || posUpdate.getLongitude()!=position.getLongitude()){
                GeoPoint newpos = new GeoPoint(posUpdate.getLatitude(), posUpdate.getLongitude());
                ObjectAnimator valueAnimator = MarkerUtils.makeMarkerAnimator(
                        map, marker, newpos, MarkerUtils.LINEAR_ANIMATION, 1200);
                valueAnimator.setAutoCancel(true);
                tripMarkersAnimators.put(updateID,valueAnimator);
                valueAnimator.start();
            }
                //marker.setPosition(new GeoPoint(posUpdate.getLatitude(), posUpdate.getLongitude()));
        } else {

            position = new GeoPoint(posUpdate.getLatitude(), posUpdate.getLongitude());
            marker.setPosition(position);
        }

        if(posUpdate.getBearing()!=null)
            marker.setRotation(posUpdate.getBearing()*(-1.f));
    }

    private void updateBusPositionsInMap(HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops>> tripsPatterns){
        Log.d(DEBUG_TAG, "Updating positions of the buses");
        //if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
        final ArrayList<String> noPatternsTrips = new ArrayList<>();
        for(String tripID: tripsPatterns.keySet()) {
            final Pair<LivePositionUpdate, TripAndPatternWithStops> pair = tripsPatterns.get(tripID);
            if (pair == null) continue;
            final LivePositionUpdate update = pair.getFirst();
            final TripAndPatternWithStops tripWithPatternStops = pair.getSecond();


            //check if Marker is already created
            if (busPositionMarkersByTrip.containsKey(tripID)){
                //need to change the position of the marker
                final Marker marker = busPositionMarkersByTrip.get(tripID);
                assert marker!=null;
                updateBusMarker(marker, update, false);
                if(marker.getInfoWindow()!=null && marker.getInfoWindow() instanceof BusInfoWindow){
                    BusInfoWindow window = (BusInfoWindow) marker.getInfoWindow();
                    if(tripWithPatternStops != null) {
                        //Log.d(DEBUG_TAG, "Update pattern for trip: "+tripID);
                        window.setPatternAndDraw(tripWithPatternStops.getPattern());
                    }

                }
            } else{
                //marker is not there, need to make it
                if(map==null) Log.e(DEBUG_TAG, "Creating marker with null map, things will explode");
                final Marker marker = new Marker(map);

                /*final Drawable mDrawable = DrawableUtils.Companion.getScaledDrawableResources(
                        getResources(),
                        R.drawable.point_heading_icon,
                R.dimen.map_icons_size, R.dimen.map_icons_size);

                 */
                //String route = GtfsUtils.getLineNameFromGtfsID(update.getRouteID());
                final Drawable mdraw = ResourcesCompat.getDrawable(getResources(),R.drawable.map_bus_position_icon, null);
                /*final Drawable mdraw = DrawableUtils.Companion.writeOnDrawable(getResources(),
                        R.drawable.point_heading_icon,
                        R.color.white,
                        route,12);

                 */
                assert mdraw != null;
                //mdraw.setBounds(0,0,28,28);
                marker.setIcon(mdraw);
                if(tripWithPatternStops == null){
                    noPatternsTrips.add(tripID);
                }
                MatoPattern markerPattern = null;
                if(tripWithPatternStops != null && tripWithPatternStops.getPattern()!=null)
                    markerPattern = tripWithPatternStops.getPattern();
                marker.setInfoWindow(new BusInfoWindow(map, update, markerPattern , false, (pattern) -> {    }));
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

                updateBusMarker(marker, update, true);
                // the overlay is null when it's not attached yet?5
                // cannot recreate it because it becomes null very soon
                // if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
                //save the marker
                if(busPositionsOverlay!=null) {
                    busPositionsOverlay.add(marker);
                    busPositionMarkersByTrip.put(tripID, marker);
                }

            }
        }
        if(noPatternsTrips.size()>0){
            Log.i(DEBUG_TAG, "These trips have no matching pattern: "+noPatternsTrips);
        }
    }

    /**
     * Add stops as Markers on the map
     * @param stops the list of stops that must be included
     */
    protected void showStopsMarkers(List<Stop> stops){
        if (getContext() == null || stops == null){
            //we are not attached
            return;
        }
        boolean good = true;

        for (Stop stop : stops) {
            if (shownStops.contains(stop.ID)){
                continue;
            }
            if(stop.getLongitude()==null || stop.getLatitude()==null)
                continue;

            shownStops.add(stop.ID);
            if(!map.isShown()){
                if(good)
                    Log.d(DEBUG_TAG, "Need to show stop but map is not shown, probably detached already");
                good = false;
                continue;
            } else if(map.getRepository() == null){
                Log.e(DEBUG_TAG, "Map view repository is null");
            }
            GeoPoint marker = new GeoPoint(stop.getLatitude(), stop.getLongitude());

            Marker stopMarker = makeMarker(marker, stop, false);
            stopsFolderOverlay.add(stopMarker);
            if (!map.getOverlays().contains(stopsFolderOverlay)) {
                Log.w(DEBUG_TAG, "Map doesn't have folder overlay");
            }
            good=true;
        }
        //Log.d(DEBUG_TAG,"We have " +stopsFolderOverlay.getItems().size()+" stops in the folderOverlay");
        //force redraw of markers
        map.invalidate();
    }

    public Marker makeMarker(GeoPoint geoPoint, Stop stop, boolean isStartMarker){
        return  makeMarker(geoPoint,stop.ID,
                stop.getStopDefaultName(),
                stop.routesThatStopHereToString(), isStartMarker);
    }

    public Marker makeMarker(GeoPoint geoPoint, String stopID, String stopName,
                             String routesStopping, boolean isStartMarker) {

        // add a marker
        final Marker marker = new Marker(map);

        // set custom info window as info window
        CustomInfoWindow popup = new CustomInfoWindow(map, stopID, stopName, routesStopping,
                responder, R.layout.linedetail_stop_infowindow, R.color.red_darker);
        marker.setInfoWindow(popup);

        // make the marker clickable
        marker.setOnMarkerClickListener((thisMarker, mapView) -> {
            if (thisMarker.isInfoWindowOpen()) {
                // on second click
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
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        // add to it an icon
        //marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));

        marker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.bus_stop, ctx.getTheme()));
        // add to it a title
        marker.setTitle(stopName);
        // set the description as the ID
        marker.setSnippet(stopID);

        // show popup info window of the searched marker
        if (isStartMarker) {
            marker.showInfoWindow();
            //map.getController().animateTo(marker.getPosition());
        }

        return marker;
    }

    @Nullable
    @Override
    public View getBaseViewForSnackBar() {
        return coordLayout;
    }

}
