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
package it.reyboz.bustorino.fragments

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.gtfs.MatoPattern
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.BusInfoWindow
import it.reyboz.bustorino.map.CustomInfoWindow
import it.reyboz.bustorino.map.CustomInfoWindow.TouchResponder
import it.reyboz.bustorino.map.LocationOverlay
import it.reyboz.bustorino.map.LocationOverlay.OverlayCallbacks
import it.reyboz.bustorino.map.MarkerUtils
import it.reyboz.bustorino.middleware.GeneralActivity
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel
import it.reyboz.bustorino.viewmodels.StopsMapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

open class MapFragmentKt : ScreenBaseFragment() {
    protected var listenerMain: FragmentListenerMain? = null
    private var shownStops: HashSet<String>? = null
    private lateinit var map: MapView
    var ctx: Context? = null
    private lateinit var mLocationOverlay: LocationOverlay
    private lateinit  var stopsFolderOverlay: FolderOverlay
    private var savedMapState: Bundle? = null
    protected lateinit var btCenterMap: ImageButton
    protected lateinit var  btFollowMe: ImageButton
    protected var coordLayout: CoordinatorLayout? = null
    private var hasMapStartFinished = false
    private var followingLocation = false

    //the ViewModel from which we get the stop to display in the map
    private val stopsViewModel: StopsMapViewModel by viewModels()

    //private GtfsPositionsViewModel gtfsPosViewModel; //= new ViewModelProvider(this).get(MapViewModel.class);
    private val livePositionsViewModel: LivePositionsViewModel by viewModels()
    private var useMQTTViewModel = true
    private val busPositionMarkersByTrip = HashMap<String, Marker>()
    private var busPositionsOverlay: FolderOverlay? = null
    private val tripMarkersAnimators = HashMap<String, ObjectAnimator>()
    protected val responder = TouchResponder { stopID, stopName ->
        if (listenerMain != null) {
            Log.d(DEBUG_TAG, "Asked to show arrivals for stop ID: $stopID")
            listenerMain!!.requestArrivalsForStopID(stopID)
        }
    }
    protected val locationCallbacks: OverlayCallbacks = object : OverlayCallbacks {
        override fun onDisableFollowMyLocation() {
            updateGUIForLocationFollowing(false)
            followingLocation = false
        }

        override fun onEnableFollowMyLocation() {
            updateGUIForLocationFollowing(true)
            followingLocation = true
        }
    }
    private val positionRequestLauncher =
        registerForActivityResult<Array<String>, Map<String, Boolean>>(
            ActivityResultContracts.RequestMultiplePermissions(),
            ActivityResultCallback { result ->
                if (result == null) {
                    Log.w(DEBUG_TAG, "Got asked permission but request is null, doing nothing?")
                } else if (java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_COARSE_LOCATION]
                    && java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_FINE_LOCATION]) {
                    // We can use the position, restart location overlay
                    map.overlays.remove(mLocationOverlay)
                    startLocationOverlay(true, map)
                    if (context == null || requireContext().getSystemService(Context.LOCATION_SERVICE) == null)
                        return@ActivityResultCallback ///@registerForActivityResult
                    val locationManager =
                        requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    @SuppressLint("MissingPermission") val userLocation =
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (userLocation != null) {
                        map!!.controller.setZoom(POSITION_FOUND_ZOOM)
                        val startPoint = GeoPoint(userLocation)
                        setLocationFollowing(true)
                        map!!.controller.setCenter(startPoint)
                    }
                } else Log.w(DEBUG_TAG, "No location permission")
            })

    //public static MapFragment getInstance(@NonNull Stop stop){
    //   return getInstance(stop.getLatitude(), stop.getLongitude(), stop.getStopDisplayName(), stop.ID);
    //}
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //use the same layout as the activity
        val root = inflater.inflate(R.layout.fragment_map, container, false)
        val context = requireContext()
        ctx = context.applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(context))
        map = root.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        //map.setTilesScaledToDpi(true);
        map.setFlingEnabled(true)

        // add ability to zoom with 2 fingers
        map.setMultiTouchControls(true)
        btCenterMap = root.findViewById(R.id.centerMapImageButton)
        btFollowMe = root.findViewById(R.id.followUserImageButton)
        coordLayout = root.findViewById(R.id.coord_layout)

        //setup FolderOverlay
        stopsFolderOverlay = FolderOverlay()
        //setup Bus Markers Overlay
        busPositionsOverlay = FolderOverlay()
        //reset shown bus updates
        busPositionMarkersByTrip.clear()
        tripMarkersAnimators.clear()
        //set map not done
        hasMapStartFinished = false
        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTViewModel = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE)
            .contentEquals(SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE)


        //Start map from bundle
        if (savedInstanceState != null) startMap(arguments, savedInstanceState) else startMap(
            arguments, savedMapState
        )
        //set listeners
        map.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(paramScrollEvent: ScrollEvent): Boolean {
                requestStopsToShow()
                //Log.d(DEBUG_TAG, "Scrolling");
                //if (moveTriggeredByCode) moveTriggeredByCode =false;
                //else setLocationFollowing(false);
                return true
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                requestStopsToShow()
                return true
            }
        }))
        btCenterMap.setOnClickListener(View.OnClickListener { v: View? ->
            //Log.i(TAG, "centerMap clicked ");
            if (Permissions.bothLocationPermissionsGranted(context)) {
                val myPosition = mLocationOverlay!!.myLocation
                map.getController().animateTo(myPosition)
            } else Toast.makeText(context, R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                .show()
        })
        btFollowMe.setOnClickListener(View.OnClickListener { v: View? ->
            //Log.i(TAG, "btFollowMe clicked ");
            if (Permissions.bothLocationPermissionsGranted(context)) setLocationFollowing(!followingLocation) else Toast.makeText(
                context, R.string.enable_position_message_map, Toast.LENGTH_SHORT
            )
                .show()
        })
        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listenerMain = if (context is FragmentListenerMain) {
            context
        } else {
            throw RuntimeException(
                context.toString()
                        + " must implement FragmentListenerMain"
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        listenerMain = null

        Log.w(DEBUG_TAG, "Fragment detached")
    }

    override fun onPause() {
        super.onPause()
        Log.w(DEBUG_TAG, "On pause called mapfrag")
        saveMapState()
        for (animator in tripMarkersAnimators.values) {
            if (animator != null && animator.isRunning) {
                animator.cancel()
            }
        }
        tripMarkersAnimators.clear()
        if (useMQTTViewModel) livePositionsViewModel.stopMatoUpdates()
    }

    /**
     * Save the map state inside the fragment
     * (calls saveMapState(bundle))
     */
    private fun saveMapState() {
        savedMapState = Bundle()
        saveMapState(savedMapState!!)
    }

    /**
     * Save the state of the map to restore it to a later time
     * @param bundle the bundle in which to save the data
     */
    private fun saveMapState(bundle: Bundle) {
        Log.d(DEBUG_TAG, "Saving state, location following: $followingLocation")
        bundle.putBoolean(FOLLOWING_LOCAT_KEY, followingLocation)
        if (map == null) {
            //The map is null, it  can happen?
            Log.e(DEBUG_TAG, "Cannot save map center, map is null")
            return
        }
        val loc = map!!.mapCenter
        bundle.putDouble(MAP_CENTER_LAT_KEY, loc.latitude)
        bundle.putDouble(MAP_CENTER_LON_KEY, loc.longitude)
        bundle.putDouble(MAP_CURRENT_ZOOM_KEY, map!!.zoomLevelDouble)
    }

    override fun onResume() {
        super.onResume()
        //TODO: cleanup duplicate code (maybe merging the positions classes?)
        if (listenerMain != null) listenerMain!!.readyGUIfor(FragmentKind.MAP)
        /// choose which to use
        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTViewModel = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE)
            .contentEquals(
                SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE
            )
        //gtfsPosViewModel.requestUpdates();
        if (useMQTTViewModel) livePositionsViewModel.requestMatoPosUpdates(MQTTMatoClient.LINES_ALL)
        else livePositionsViewModel.requestGTFSUpdates()
        //mapViewModel.testCascade();
        livePositionsViewModel.isLastWorkResultGood.observe(this) { d: Boolean ->
            Log.d(
                DEBUG_TAG, "Last trip download result is $d"
            )
        }
        livePositionsViewModel.tripsGtfsIDsToQuery.observe(this) { dat: List<String> ->
            Log.i(DEBUG_TAG, "Have these trips IDs missing from the DB, to be queried: $dat")
            livePositionsViewModel.downloadTripsFromMato(dat)
        }

        //rerequest stop
        stopsViewModel!!.requestStopsInBoundingBox(map!!.boundingBox)
    }

    private fun startRequestsPositions() {
        if (livePositionsViewModel != null) {
            //should always be the case
            livePositionsViewModel!!.updatesWithTripAndPatterns.observe(viewLifecycleOwner) { data: HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>> ->
                Log.d(
                    DEBUG_TAG,
                    "Have " + data.size + " trip updates, has Map start finished: " + hasMapStartFinished
                )
                if (hasMapStartFinished) updateBusPositionsInMap(data)
                if (!isDetached && !useMQTTViewModel) livePositionsViewModel!!.requestDelayedGTFSUpdates(
                    3000
                )
            }
        } else {
            Log.e(DEBUG_TAG, "PositionsViewModel is null")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveMapState(outState)
        super.onSaveInstanceState(outState)
    }
    //own methods
    /**
     * Switch following the location on and off
     * @param value true if we want to follow location
     */
    fun setLocationFollowing(value: Boolean) {
        followingLocation = value
        if (mLocationOverlay == null || context == null || map == null) //nothing else to do
            return
        if (value) {
            mLocationOverlay!!.enableFollowLocation()
        } else {
            mLocationOverlay!!.disableFollowLocation()
        }
    }

    /**
     * Do all the stuff you need to do on the gui, when parameter is changed to value
     * @param following value
     */
    protected fun updateGUIForLocationFollowing(following: Boolean) {
        if (following) btFollowMe!!.setImageResource(R.drawable.ic_follow_me_on) else btFollowMe!!.setImageResource(
            R.drawable.ic_follow_me
        )
    }

    /**
     * Build the location overlay. Enable only when
     * a) we know we have the permission
     * b) the location map is set
     */
    private fun startLocationOverlay(enableLocation: Boolean, map: MapView?) {
        checkNotNull(activity) { "Cannot enable LocationOverlay now" }
        // Location Overlay
        // from OpenBikeSharing (THANK GOD)
        Log.d(DEBUG_TAG, "Starting position overlay")
        val imlp = GpsMyLocationProvider(requireActivity().baseContext)
        imlp.locationUpdateMinDistance = 5f
        imlp.locationUpdateMinTime = 2000
        val overlay = LocationOverlay(imlp, map, locationCallbacks)
        if (enableLocation) overlay.enableMyLocation()
        overlay.isOptionsMenuEnabled = true

        //map.getOverlays().add(this.mLocationOverlay);
        mLocationOverlay = overlay
        map!!.overlays.add(mLocationOverlay)
    }

    fun startMap(incoming: Bundle?, savedInstanceState: Bundle?) {
        //Check that we're attached
        val activity = if (activity is GeneralActivity) activity as GeneralActivity? else null
        if (context == null || activity == null) {
            //we are not attached
            Log.e(DEBUG_TAG, "Calling startMap when not attached")
            return
        } else {
            Log.d(DEBUG_TAG, "Starting map from scratch")
        }
        //clear previous overlays
        map!!.overlays.clear()


        //parse incoming bundle
        var marker: GeoPoint? = null
        var name: String? = null
        var ID: String? = null
        var routesStopping: String? = ""
        if (incoming != null) {
            val lat = incoming.getDouble(BUNDLE_LATIT)
            val lon = incoming.getDouble(BUNDLE_LONGIT)
            marker = GeoPoint(lat, lon)
            name = incoming.getString(BUNDLE_NAME)
            ID = incoming.getString(BUNDLE_ID)
            routesStopping = incoming.getString(BUNDLE_ROUTES_STOPPING, "")
        }


        //ask for location permission
        if (!Permissions.bothLocationPermissionsGranted(activity)) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                //TODO: show dialog for permission rationale
                Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                    .show()
            }
            positionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS)
        }
        shownStops = HashSet()
        // move the map on the marker position or on a default view point: Turin, Piazza Castello
        // and set the start zoom
        val mapController = map!!.controller
        var startPoint: GeoPoint? = null
        startLocationOverlay(
            Permissions.bothLocationPermissionsGranted(activity),
            map
        )
        // set the center point
        if (marker != null) {
            //startPoint = marker;
            mapController.setZoom(POSITION_FOUND_ZOOM)
            setLocationFollowing(false)
            // put the center a little bit off (animate later)
            startPoint = GeoPoint(marker)
            startPoint.latitude = marker.latitude + utils.angleRawDifferenceFromMeters(20.0)
            startPoint.longitude = marker.longitude - utils.angleRawDifferenceFromMeters(20.0)
            //don't need to do all the rest since we want to show a point
        } else if (savedInstanceState != null && savedInstanceState.containsKey(MAP_CURRENT_ZOOM_KEY)) {
            mapController.setZoom(savedInstanceState.getDouble(MAP_CURRENT_ZOOM_KEY))
            mapController.setCenter(
                GeoPoint(
                    savedInstanceState.getDouble(MAP_CENTER_LAT_KEY),
                    savedInstanceState.getDouble(MAP_CENTER_LON_KEY)
                )
            )
            Log.d(
                DEBUG_TAG,
                "Location following from savedInstanceState: " + savedInstanceState.getBoolean(
                    FOLLOWING_LOCAT_KEY
                )
            )
            setLocationFollowing(savedInstanceState.getBoolean(FOLLOWING_LOCAT_KEY))
        } else {
            Log.d(DEBUG_TAG, "No position found from intent or saved state")
            var found = false
            val locationManager =
                requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            //check for permission
            if (Permissions.bothLocationPermissionsGranted(activity)) {
                @SuppressLint("MissingPermission") val userLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (userLocation != null) {
                    val distan = utils.measuredistanceBetween(
                        userLocation.latitude, userLocation.longitude,
                        DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON
                    )
                    if (distan < 100000.0) {
                        mapController.setZoom(POSITION_FOUND_ZOOM)
                        startPoint = GeoPoint(userLocation)
                        found = true
                        setLocationFollowing(true)
                    }
                }
            }
            if (!found) {
                startPoint = GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
                mapController.setZoom(NO_POSITION_ZOOM)
                setLocationFollowing(false)
            }
        }

        // set the minimum zoom level
        map!!.minZoomLevel = 15.0
        //add contingency check (shouldn't happen..., but)
        if (startPoint != null) {
            mapController.setCenter(startPoint)
        }


        //add stops overlay
        //map.getOverlays().add(mLocationOverlay);
        map!!.overlays.add(stopsFolderOverlay)
        Log.d(DEBUG_TAG, "Requesting stops load")
        // This is not necessary, by setting the center we already move
        // the map and we trigger a stop request
        //requestStopsToShow();
        if (marker != null) {
            // make a marker with the info window open for the searched marker
            //TODO:  make Stop Bundle-able
            val stopMarker = makeMarker(marker, ID, name, routesStopping, true)
            map!!.controller.animateTo(marker)
        }
        //add the overlays with the bus stops
        if (busPositionsOverlay == null) {
            //Log.i(DEBUG_TAG, "Null bus positions overlay,redo");
            busPositionsOverlay = FolderOverlay()
        }
        startRequestsPositions()
        if (stopsViewModel != null) {
            stopsViewModel!!.stopsInBoundingBox.observe(viewLifecycleOwner) { stops: List<Stop>? ->
                showStopsMarkers(
                    stops
                )
            }
        } else Log.d(DEBUG_TAG, "Cannot observe new stops in map, stopsViewModel is null")
        map!!.overlays.add(busPositionsOverlay)
        //set map as started
        hasMapStartFinished = true
    }

    /**
     * Start a request to load the stops that are in the current view
     * from the database
     */
    private fun requestStopsToShow() {
        // get the top, bottom, left and right screen's coordinate
        val bb = map!!.boundingBox
        Log.d(
            DEBUG_TAG,
            "Requesting stops in bounding box, stopViewModel is null " + (false)
        )
        stopsViewModel.requestStopsInBoundingBox(bb)

    }

    private fun updateBusMarker(
        marker: Marker?,
        posUpdate: LivePositionUpdate,
        justCreated: Boolean
    ) {
        val position: GeoPoint
        val updateID = posUpdate.tripID
        if (!justCreated) {
            position = marker!!.position
            if (posUpdate.latitude != position.latitude || posUpdate.longitude != position.longitude) {
                val newpos = GeoPoint(posUpdate.latitude, posUpdate.longitude)
                val valueAnimator = MarkerUtils.makeMarkerAnimator(
                    map, marker, newpos, MarkerUtils.LINEAR_ANIMATION, 1200
                )
                valueAnimator.setAutoCancel(true)
                tripMarkersAnimators[updateID] = valueAnimator
                valueAnimator.start()
            }
            //marker.setPosition(new GeoPoint(posUpdate.getLatitude(), posUpdate.getLongitude()));
        } else {
            position = GeoPoint(posUpdate.latitude, posUpdate.longitude)
            marker!!.position = position
        }
        marker.rotation = posUpdate.bearing?.let { it*-1f } ?: 0.0f
    }

    private fun updateBusPositionsInMap(tripsPatterns: HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>>) {
        Log.d(DEBUG_TAG, "Updating positions of the buses")
        //if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
        val noPatternsTrips = ArrayList<String>()
        for (tripID in tripsPatterns.keys) {
            val (update, tripWithPatternStops) = tripsPatterns[tripID] ?: continue


            //check if Marker is already created
            if (busPositionMarkersByTrip.containsKey(tripID)) {
                //need to change the position of the marker
                val marker = busPositionMarkersByTrip[tripID]!!
                updateBusMarker(marker, update, false)
                if (marker.infoWindow != null && marker.infoWindow is BusInfoWindow) {
                    val window = marker.infoWindow as BusInfoWindow
                    if (tripWithPatternStops != null) {
                        //Log.d(DEBUG_TAG, "Update pattern for trip: "+tripID);
                        window.setPatternAndDraw(tripWithPatternStops.pattern)
                    }
                }
            } else {
                //marker is not there, need to make it
                val marker = Marker(map)

                /*final Drawable mDrawable = DrawableUtils.Companion.getScaledDrawableResources(
                        getResources(),
                        R.drawable.point_heading_icon,
                R.dimen.map_icons_size, R.dimen.map_icons_size);

                 */
                //String route = GtfsUtils.getLineNameFromGtfsID(update.getRouteID());
                val mdraw =
                    ResourcesCompat.getDrawable(resources, R.drawable.map_bus_position_icon, null)!!
                //mdraw.setBounds(0,0,28,28);
                marker.icon = mdraw
                if (tripWithPatternStops == null) {
                    noPatternsTrips.add(tripID)
                }
                var markerPattern: MatoPattern? = null
                if (tripWithPatternStops != null && tripWithPatternStops.pattern != null) markerPattern =
                    tripWithPatternStops.pattern
                marker.infoWindow =
                    BusInfoWindow(map!!, update, markerPattern, false) { pattern: MatoPattern? -> }
                marker.setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                updateBusMarker(marker, update, true)
                // the overlay is null when it's not attached yet?5
                // cannot recreate it because it becomes null very soon
                // if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
                //save the marker
                if (busPositionsOverlay != null) {
                    busPositionsOverlay!!.add(marker)
                    busPositionMarkersByTrip[tripID] = marker
                }
            }
        }
        if (noPatternsTrips.size > 0) {
            Log.i(DEBUG_TAG, "These trips have no matching pattern: $noPatternsTrips")
        }
    }

    /**
     * Add stops as Markers on the map
     * @param stops the list of stops that must be included
     */
    protected fun showStopsMarkers(stops: List<Stop>?) {
        if (context == null || stops == null) {
            //we are not attached
            return
        }
        var good = true
        for (stop in stops) {
            if (shownStops!!.contains(stop.ID)) {
                continue
            }
            if (stop.longitude == null || stop.latitude == null) continue
            shownStops!!.add(stop.ID)
            if (!map!!.isShown) {
                if (good) Log.d(
                    DEBUG_TAG,
                    "Need to show stop but map is not shown, probably detached already"
                )
                good = false
                continue
            } else if (map!!.repository == null) {
                Log.e(DEBUG_TAG, "Map view repository is null")
            }
            val marker = GeoPoint(stop.latitude!!, stop.longitude!!)
            val stopMarker = makeMarker(marker, stop, false)
            stopsFolderOverlay!!.add(stopMarker)
            if (!map!!.overlays.contains(stopsFolderOverlay)) {
                Log.w(DEBUG_TAG, "Map doesn't have folder overlay")
            }
            good = true
        }
        //Log.d(DEBUG_TAG,"We have " +stopsFolderOverlay.getItems().size()+" stops in the folderOverlay");
        //force redraw of markers
        map!!.invalidate()
    }

    fun makeMarker(geoPoint: GeoPoint?, stop: Stop, isStartMarker: Boolean): Marker {
        return makeMarker(
            geoPoint, stop.ID,
            stop.stopDefaultName,
            stop.routesThatStopHereToString(), isStartMarker
        )
    }

    fun makeMarker(
        geoPoint: GeoPoint?, stopID: String?, stopName: String?,
        routesStopping: String?, isStartMarker: Boolean
    ): Marker {

        // add a marker
        val marker = Marker(map)

        // set custom info window as info window
        val popup = CustomInfoWindow(
            map, stopID, stopName, routesStopping,
            responder, R.layout.linedetail_stop_infowindow, R.color.red_darker
        )
        marker.infoWindow = popup

        // make the marker clickable
        marker.setOnMarkerClickListener { thisMarker: Marker, mapView: MapView? ->
            if (thisMarker.isInfoWindowOpen) {
                // on second click
                Log.w(DEBUG_TAG, "Pressed on the click marker")
            } else {
                // on first click

                // hide all opened info window
                InfoWindow.closeAllInfoWindowsOn(map)
                // show this particular info window
                thisMarker.showInfoWindow()
                // move the map to its position
                map!!.controller.animateTo(thisMarker.position)
            }
            true
        }

        // set its position
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        // add to it an icon
        //marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));
        marker.icon = ResourcesCompat.getDrawable(resources, R.drawable.bus_stop, ctx!!.theme)
        // add to it a title
        marker.title = stopName
        // set the description as the ID
        marker.snippet = stopID

        // show popup info window of the searched marker
        if (isStartMarker) {
            marker.showInfoWindow()
            //map.getController().animateTo(marker.getPosition());
        }
        return marker
    }

    override fun getBaseViewForSnackBar(): View? {
        return coordLayout
    }

    companion object {
        //private static final String TAG = "Busto-MapActivity";
        private const val MAP_CURRENT_ZOOM_KEY = "map-current-zoom"
        private const val MAP_CENTER_LAT_KEY = "map-center-lat"
        private const val MAP_CENTER_LON_KEY = "map-center-lon"
        private const val FOLLOWING_LOCAT_KEY = "following"
        const val BUNDLE_LATIT = "lat"
        const val BUNDLE_LONGIT = "lon"
        const val BUNDLE_NAME = "name"
        const val BUNDLE_ID = "ID"
        const val BUNDLE_ROUTES_STOPPING = "routesStopping"
        const val FRAGMENT_TAG = "BusTOMapFragment"
        private const val DEFAULT_CENTER_LAT = 45.0708
        private const val DEFAULT_CENTER_LON = 7.6858
        private const val POSITION_FOUND_ZOOM = 18.3
        const val NO_POSITION_ZOOM = 17.1
        private const val DEBUG_TAG = FRAGMENT_TAG

        @JvmStatic
        fun getInstance(): MapFragmentKt {
            return MapFragmentKt()
        }
        @JvmStatic
        fun getInstance(stop: Stop): MapFragmentKt {
            val fragment = MapFragmentKt()
            val args = Bundle()
            args.putDouble(MapFragment.BUNDLE_LATIT, stop.latitude!!)
            args.putDouble(MapFragment.BUNDLE_LONGIT, stop.longitude!!)
            args.putString(MapFragment.BUNDLE_NAME, stop.stopDisplayName)
            args.putString(MapFragment.BUNDLE_ID, stop.ID)
            args.putString(MapFragment.BUNDLE_ROUTES_STOPPING, stop.routesThatStopHereToString())
            fragment.arguments = args

            return fragment
        }
    }
}
