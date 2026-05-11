/*
	BusTO  - Fragments components
    Copyright (C) 2025 Fabio Mazza

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


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.room.concurrent.AtomicBoolean
import com.google.android.material.bottomsheet.BottomSheetBehavior
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.MapLibreLocationEngine
import it.reyboz.bustorino.map.MapLibreStyles
import it.reyboz.bustorino.viewmodels.StopsMapViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * A simple [Fragment] subclass.
 * Use the [MapLibreFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapLibreFragment : GeneralMapLibreFragment() {


    private val stopsViewModel: StopsMapViewModel by viewModels()
    private var stopsShowing = ArrayList<Stop>(0)

    // Sources for stops and buses are in GeneralMapLibreFragment
    private var isUserMovingCamera = false
    private var lastStopsSizeShown = 0
    private var lastBBox = LatLngBounds.from(2.0, 2.0, 1.0,1.0)
    private var stopsRedrawnTimes = 0

    //bottom Sheet behavior in GeneralMapLibreFragment
    //private var stopActiveSymbol: Symbol? = null

    // Location stuff
    private lateinit var locationManager: LocationManager
    private lateinit var userLocationButton: ImageButton
    private lateinit var centerUserButton: ImageButton
    private lateinit var followUserButton: ImageButton

    private var followingUserLocation = false
    private var ignoreCameraMovementForFollowing = true
    private var restoredMapCamera = AtomicBoolean()
    //BUS POSITIONS
    private var usingMQTTPositions = true // THIS IS INSIDE VIEW MODEL NOW

    private val symbolsToUpdate = ArrayList<Symbol>()

    private var initialStopToShow : Stop? = null
    private var initialStopShown = false
    private var waitingDelayedBusUpdate = false

    //shown stuff
    //private var savedStateOnStop : Bundle? = null

    private val showBusLayer = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialStopToShow = Stop.fromBundle(arguments)
            if (initialStopToShow==null){

            } else if(!initialStopToShow!!.hasCoords()){
                //null the stop if it doesn't have coordinates, we cannot find it
                initialStopToShow = null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView =  inflater.inflate(R.layout.fragment_map_libre,
            container, false)
        //reset the counter
        lastStopsSizeShown = 0
        stopsRedrawnTimes = 0
        stopsLayerStarted = false
        symbolsToUpdate.clear()

        // Init layout view

        // Init the MapView
        mapView = rootView.findViewById(R.id.libreMapView)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        //init bottom sheet
        val bottomSheet = rootView.findViewById<RelativeLayout>(R.id.bottom_sheet)
        bottomLayout = bottomSheet
        stopTitleTextView = bottomSheet.findViewById(R.id.stopTitleTextView)
        stopNumberTextView = bottomSheet.findViewById(R.id.stopNumberTextView)
        linesPassingTextView = bottomSheet.findViewById(R.id.linesPassingTextView)
        arrivalsCard = bottomSheet.findViewById(R.id.arrivalsCardButton)
        directionsCard = bottomSheet.findViewById(R.id.directionsCardButton)

        userLocationButton = rootView.findViewById(R.id.locationEnableIcon)
        userLocationButton.setOnClickListener(this::switchUserLocationStatus)
        followUserButton = rootView.findViewById(R.id.followUserImageButton)
        centerUserButton = rootView.findViewById(R.id.centerMapImageButton)
        busPositionsIconButton = rootView.findViewById(R.id.busPositionsImageButton)
        busPositionsIconButton.setOnClickListener {
            LivePositionsDialogFragment().show(parentFragmentManager, "LivePositionsDialog")
        }
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        arrivalsCard.setOnClickListener {
            if(context!=null){
                Toast.makeText(context,"ARRIVALS", Toast.LENGTH_SHORT).show()
            }
        }
        centerUserButton.setOnClickListener {
            if(context!=null && locationComponent.isLocationComponentEnabled) {
                val location = locationComponent.lastKnownLocation

                location?.let {
                    mapView.getMapAsync { map ->
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(LatLng(location.latitude, location.longitude)).build()), 500)
                    }
                }
            }
        }
        followUserButton.setOnClickListener {
            // onClick user following button
            if(context!=null && locationInitialized && locationComponent.isLocationComponentEnabled){

                // CameraMode.TRACKING makes the camera move and jump to the location
               setFollowUserLocation(!followingUserLocation)
            }
        }
        //locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        /*
        if (Permissions.bothLocationPermissionsGranted(requireContext()) && deviceHasGpsProvider()) {

            requestInitialUserLocation()
        } else{
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                //TODO: show dialog for permission rationale
                Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                    .show()
            }
            // PERMISSIONS REQUESTED AFTER MAP SETUP
        }

         */


        // Setup close button
        rootView.findViewById<View>(R.id.btnClose).setOnClickListener {
            hideStopOrBusBottomSheet()
        }
        observeStatusLivePositions()
        //observe change in source of the live positions
        livePositionsViewModel.useMQTTPositionsLiveData.observe(viewLifecycleOwner){ useMQTT->
            //Log.d(DEBUG_TAG, "Changed MQTT positions, now have to use MQTT: $useMQTT")
            if (showBusLayer && isResumed) {
                //Log.d(DEBUG_TAG, "Deciding to switch, the current source is using MQTT: $usingMQTTPositions")
                if(useMQTT!=usingMQTTPositions){
                    // we have to switch
                    val clearPos = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("positions_clear_on_switch_pref", true)
                    livePositionsViewModel.clearOldPositionsUpdates()
                    if(useMQTT){
                        //switching to MQTT, the GTFS positions are disabled automatically
                        livePositionsViewModel.requestMatoPosUpdates(MQTTMatoClient.LINES_ALL)
                    } else{
                        //switching to GTFS RT: stop Mato, launch first request
                        livePositionsViewModel.stopMatoUpdates()
                        livePositionsViewModel.requestGTFSUpdates()
                    }
                    Log.d(DEBUG_TAG, "Should clear positions: $clearPos")
                    if (clearPos) {
                        livePositionsViewModel.clearAllPositions()
                        //force clear of the viewed data
                        if(vehShowing.isNotEmpty()) hideStopOrBusBottomSheet()
                        clearAllBusPositionsInMap()
                    }

                }
            }
            usingMQTTPositions = useMQTT

        }
        mapStateViewModel.locationUserActive.observe(viewLifecycleOwner){
            setLocationIconEnabled(it)}
        mapStateViewModel.followingUserPosition.observe(viewLifecycleOwner){ updateFollowingIcon(it)}

        Log.d(DEBUG_TAG, "Fragment View Created!")

        //TODO: Reshow last open stop when switching back to the map fragment
        return rootView
    }

    /**
     * This method sets up the map and the layers
     */
    override fun onMapReady(mapReady: MapLibreMap) {
        this.map = mapReady
        val context = requireContext()
        val mjson = MapLibreStyles.getJsonStyleFromAsset(context, PreferencesHolder.getMapLibreStyleFile(context))

        val builder = Style.Builder().fromJson(mjson!!)

        mapReady.setStyle(builder) { style ->

            mapStyle = style
            //setupLayers(style)
            addImagesStyle(style)

            //init stop layer with this
            val stopsInCache = stopsViewModel.getAllStopsLoaded()
            if(stopsInCache.isEmpty())
                initStopsLayer(style, null)
            else
                displayStops(stopsInCache)
            if(showBusLayer) setupBusLayer(style, withLabels = true, busIconsScale = 1.2f)

            // Start observing data now that everything is set up
            observeStops()

            checkInitMapLocation(mapReady,style, context)
        }


        mapReady.addOnCameraIdleListener {
            map?.let {
                val newBbox = it.projection.visibleRegion.latLngBounds
                if ((newBbox.center==lastBBox.center) && (newBbox.latitudeSpan==lastBBox.latitudeSpan) && (newBbox.longitudeSpan==lastBBox.latitudeSpan)){
                    //do nothing
                } else {
                    stopsViewModel.loadStopsInLatLngBounds(newBbox)
                    lastBBox = newBbox

                }

            }

        }
        mapReady.addOnCameraMoveStartedListener { v->
            if(v== MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE){
                //the user is moving the map
                //isUserMovingCamera = true
                updateFollowingIcon(false)
            }
        }

        mapReady.addOnMapClickListener { point ->
           onMapClickReact(point)
        }
        // we start requesting the bus positions now
        observeBusPositionUpdates()

        //Restoring data

        if (initialStopToShow!=null && initialStopToShow?.hasCoords() == true){
            val s = initialStopToShow!!
            if(s.hasCoords()){
                mapReady.cameraPosition = CameraPosition.Builder().target(
                    LatLng(s.latitude!!, s.longitude!!)
                ).zoom(DEFAULT_ZOOM).build()
            }
            restoredMapCamera.set(true)
        } else{
            var boundsRestored = false
            //restore the map state here
            map?.let{
                boundsRestored = mapStateViewModel.restoreMapState(it)
                 mapStateViewModel.lastOpenStopID.value?.let{ sID->
                     val s= stopsViewModel.getStopByID(sID)
                     if (s==null) {
                         if(sID.isNotEmpty())
                             Log.w(DEBUG_TAG,"Wanted to open stop $sID in map but it was not loaded!")
                     }
                     else{
                         openStopInBottomSheet(s) }
                 }

            }
            if(!boundsRestored){
                // we have not restored the bounds, open normally in target location
                // TODO: check that the map is reopened in the same location
                val lastLoc = mapStateViewModel.locationToShow
                val defaultLoc = LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
                val proposedLoc = lastLoc?.let{ LatLng(lastLoc.latitude, lastLoc.longitude)}
                val targetLoc = if(proposedLoc == null || proposedLoc.distanceTo(defaultLoc) > MAX_DIST_KM*1000)
                        defaultLoc
                    else proposedLoc


                mapReady.cameraPosition = CameraPosition.Builder().target(targetLoc).zoom(DEFAULT_ZOOM).build()
            }
            restoredMapCamera.set(boundsRestored)


        }
        mapInitialized = true

        //pendingLocationActivation = true
        //positionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS)

    }

    private fun onMapClickReact(point: LatLng): Boolean{
        map?.let { mapReady ->
            val screenPoint = mapReady.projection.toScreenLocation(point)
            val stopsFeatures = mapReady.queryRenderedFeatures(screenPoint, STOPS_LAYER_ID)
            val busNearby = mapReady.queryRenderedFeatures(screenPoint, BUSES_LAYER_ID)
            Log.d(DEBUG_TAG, "Clicked on stops: $stopsFeatures \n and buses: $busNearby")
            if (stopsFeatures.isNotEmpty()) {
                val feature = stopsFeatures[0]
                val id = feature.getStringProperty("id")
                val name = feature.getStringProperty("name")
                //Toast.makeText(requireContext(), "Clicked on $name ($id)", Toast.LENGTH_SHORT).show()
                val stop = stopsViewModel.getStopByID(id)
                Log.d(DEBUG_TAG, "Decided click is on stop with id $id : $stop")
                stop?.let { newstop ->
                    val sameStopClicked = shownStopInBottomSheet?.let { newstop.ID==it.ID } ?: false
                    Log.d(DEBUG_TAG, "Hiding clicked stop: $sameStopClicked")
                    if (isBottomSheetShowing()) {
                        hideStopOrBusBottomSheet()
                    }
                    if(!sameStopClicked){
                        openStopInBottomSheet(newstop)
                        //isBottomSheetShowing = true
                        //move camera
                        if (newstop.latitude != null && newstop.longitude != null)
                        //mapReady.cameraPosition = CameraPosition.Builder().target(LatLng(it.latitude!!, it.longitude!!)).build()
                            mapReady.animateCamera(
                                CameraUpdateFactory.newLatLng(LatLng(newstop.latitude!!, newstop.longitude!!)),
                                750
                            )
                    }

                }
                return true
            } else if (busNearby.isNotEmpty()) {
                val feature = busNearby[0]
                val vehid = feature.getStringProperty("veh")
                if (isBottomSheetShowing()) hideStopOrBusBottomSheet()
                showVehicleTripInBottomSheet(vehid)
                //move camera to center on vehicle
                updatesByVehDict[vehid]?.let { dat ->
                    mapReady.animateCamera(
                        CameraUpdateFactory.newLatLng(LatLng(dat.posUpdate.latitude, dat.posUpdate.longitude)), 750
                    )
                }
                return true
            }
        }
        return false
    }

    override fun showOpenStopWithSymbolLayer(): Boolean {
        return false
    }
    override fun hideStopOrBusBottomSheet(){
        if (shownStopInBottomSheet?.ID == initialStopToShow?.ID){
            initialStopToShow = null
        }
        super.hideStopOrBusBottomSheet()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentListener = if (context is CommonFragmentListener) {
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
        fragmentListener = null
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        //mapView.onResume() handled in GeneralMapLibreFragment

        if(showBusLayer) {
            //first, clean up all the old positions
            livePositionsViewModel.clearOldPositionsUpdates()

            if (livePositionsViewModel.useMQTTPositionsLiveData.value!!){
                livePositionsViewModel.requestMatoPosUpdates(MQTTMatoClient.LINES_ALL)
                usingMQTTPositions = true
            }
            else {
                livePositionsViewModel.requestGTFSUpdates()
                usingMQTTPositions = false
            }

            livePositionsViewModel.isLastWorkResultGood.observe(this) { d: Boolean ->
                Log.d(
                    DEBUG_TAG, "Last trip download result is $d"
                )
            }
            livePositionsViewModel.tripsGtfsIDsToQuery.observe(this) { dat: List<String> ->
                Log.i(DEBUG_TAG, "Have these trips IDs missing from the DB, to be queried: $dat")
                livePositionsViewModel.downloadTripsFromMato(dat)
            }
        }

        fragmentListener?.readyGUIfor(FragmentKind.MAP)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        Log.d(DEBUG_TAG, "Fragment paused")

        map?.let{
            //if map is initialized
            mapStateViewModel.saveMapState(it)
        }
        mapStateViewModel.lastOpenStopID.postValue(shownStopInBottomSheet?.ID)
        if (livePositionsViewModel.useMQTTPositionsLiveData.value!!) livePositionsViewModel.stopMatoUpdates()

    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        Log.d(DEBUG_TAG, "Fragment stopped!")
       /* stopsViewModel.savedState = Bundle().let {
            mapView.onSaveInstanceState(it)
            it
        }
        */
        //save last location
        if (locationInitialized)
            map?.locationComponent?.lastKnownLocation?.let{
                stopsViewModel.lastUserLocation = it
            }


    }

    override fun onMapDestroy() {
        mapStyle.removeLayer(STOPS_LAYER_ID)
        mapStyle.removeSource(STOPS_SOURCE_ID)

        mapStyle.removeLayer(BUSES_LAYER_ID)
        mapStyle.removeSource(BUSES_SOURCE_ID)


    }
    override fun getBaseViewForSnackBar(): View? {
        return mapView
    }

    private fun showVehicleTripInBottomSheet(veh: String) {
        val data = updatesByVehDict[veh] ?: return
        super.showVehicleTripInBottomSheet(veh) { patternCode, _ ->
            map?.let { mapStateViewModel.saveMapState(it) }
            fragmentListener?.openLineFromVehicle(
                data.posUpdate.getLineGTFSFormat(),
                patternCode,
                mapStateViewModel.savedCameraState?.toBundle()
            )
        }
    }
    private fun observeStops() {
        // Observe stops
        stopsViewModel.stopsToShow.observe(viewLifecycleOwner) { stops ->
            stopsShowing = ArrayList(stops)
            displayStops(stopsShowing)
            initialStopToShow?.let{ s->
                //show the stop in the bottom sheet
                if(!initialStopShown && (s.ID in stopsShowing.map { it.ID })) {
                    val stopToShow = stopsShowing.first { it.ID == s.ID }
                    openStopInBottomSheet(stopToShow)
                    initialStopShown = true
                }
            }
        }

    }

    /**
     * Add the stops to the layers
     */
    private fun displayStops(stops: List<Stop>?) {
        if (stops.isNullOrEmpty()) return

        if (stops.size==lastStopsSizeShown){
            Log.d(DEBUG_TAG, "Not updating, have same number of stops. After 3 times")
            return
        }
        /*if(stops.size> lastStopsSizeShown){
            stopsRedrawnTimes = 0
        } else{
            stopsRedrawnTimes++
        }

         */

        val features = ArrayList<Feature>()//stops.mapNotNull { stop ->
            //stop.latitude?.let { lat ->
            //    stop.longitude?.let { lon ->
        for (s in stops){
            if (s.latitude!=null && s.longitude!=null)
                features.add(stopToGeoJsonFeature(s))


        }
        Log.d(DEBUG_TAG,"Have put ${features.size} stops to display")

        // if the layer is already started, substitute the stops inside, otherwise start it
        if (stopsLayerStarted) {
            stopsSource.setGeoJson(FeatureCollection.fromFeatures(features))
            lastStopsSizeShown = features.size
        } else
            map?.let {
                Log.d(DEBUG_TAG, "Map stop layer is not started yet, init layer")
                initStopsLayer(mapStyle, FeatureCollection.fromFeatures(features))
                Log.d(DEBUG_TAG,"Started stops layer on map")
                lastStopsSizeShown = features.size
                stopsLayerStarted = true
            }
    }

    // --------------- BUS LOCATIONS STUFF --------------------------
    /**
     * Start requesting position updates
     */
    private fun observeBusPositionUpdates() {
        livePositionsViewModel.updatesWithTripAndPatterns.observe(viewLifecycleOwner) { data: HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>> ->
            Log.d(
                DEBUG_TAG,
                "Have " + data.size + " trip updates, has Map start finished: $mapInitialized"
            )
            if (mapInitialized) updateBusPositionsInMap(data, hasVehicleTracking = true) { veh ->
                showVehicleTripInBottomSheet(veh)
            }
            if (!isDetached && !livePositionsViewModel.useMQTTPositionsLiveData.value!!) livePositionsViewModel.requestDelayedGTFSUpdates(
                3000
            )
        }
    }


    // ------ LOCATION STUFF -----



    @SuppressLint("MissingPermission")
    override fun onMapLocationComponentInitialized() {
        //locationComponent.cameraMode = CameraMode.TRACKING

        locationComponent.renderMode = RenderMode.COMPASS
        locationComponent.locationEngine?.apply{
            // this is only called once
            getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(res: LocationEngineResult?) {
                    Log.d(DEBUG_TAG, "Got the last location, ${res?.lastLocation}")
                    res?.lastLocation?.let { loc ->
                        if(mapInitialized)
                            map?.cameraPosition = CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).build()
                        else
                           mapStateViewModel.locationToShow = loc
                    }
                }

                override fun onFailure(p0: java.lang.Exception) {
                    if( p0 is MapLibreLocationEngine.NoLocationException)
                            Log.d(DEBUG_TAG, "Cannot find location: ${p0.message}")
                    else
                        Log.w(DEBUG_TAG, "Failed to get the last location, error: ${p0.message}",)
                }

            })
        }
    }

    override fun onMapLocationEnabled(active: Boolean) {
        //Extra stuff to do
        setFollowUserLocation(active)
    }

    @SuppressLint("MissingPermission")
    override fun onFirstReceivedLocation(location: Location) {

        val it = location
        if(locationInitialized && !receivedFirstLocation) {
            //only zoom if the user position is close enough to the center
            val newPoint = LatLng(it.latitude, it.longitude)
            if(newPoint.distanceTo(LatLng(
                    MapLibreFragment.DEFAULT_CENTER_LAT,
                    MapLibreFragment.DEFAULT_CENTER_LON
                ))
                > MAX_DIST_KM * 1000){
                //show Toast
                if(!shownToastNoPosition) context?.let{ c->
                    Toast.makeText(c, R.string.too_far_not_showing_location, Toast.LENGTH_LONG).show()
                    shownToastNoPosition = true
                }
                setLocationComponentEnabled(false)
                //Update UI Status
                mapStateViewModel.locationUserActive.value = false
                mapStateViewModel.followingUserPosition.value = false
            } else {
                map?.apply {
                    animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(LatLng(location.latitude, location.longitude)).build()
                        ),
                        1000
                    )
                    setLocationComponentEnabled(true)
                    locationComponent.cameraMode = CameraMode.TRACKING
                    mapStateViewModel.locationUserActive.value = true
                }
                setFollowUserLocation(true)
            }
        }
        else{
            //check for this is when the map is used
            mapStateViewModel.locationToShow = location
        }
    }

    override fun setLocationIconEnabled(enabled: Boolean){
        if (enabled)
            userLocationButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_red))
        else
            userLocationButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_grey))

    }

    private fun updateFollowingIcon(enabled: Boolean){
        if(enabled)
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.walk_circle_active))
        else
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.walk_circle_inactive))

    }

    /**
     * This sets both the status on the component if it has been activated and the icon in the Fragment
     */
    private fun setFollowUserLocation(enabled: Boolean){
        if(locationInitialized) {
            if (enabled)
                locationComponent.cameraMode = CameraMode.TRACKING
            else locationComponent.cameraMode = CameraMode.NONE
        }
        //update the icon by updating the livedata
        mapStateViewModel.followingUserPosition.value = enabled
    }


    companion object {
        private const val STOPS_SOURCE_ID = "stops-source"
        private const val STOPS_LAYER_ID = "stops-layer"

        private const val LABELS_LAYER_ID = "bus-labels-layer"
        private const val LABELS_SOURCE = "labels-source"
        private const val STOP_IMAGE_ID ="bus-stop-icon"
        const val DEFAULT_CENTER_LAT = 45.0708
        const val DEFAULT_CENTER_LON = 7.6858
        private val DEFAULT_LATLNG = LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
        private val DEFAULT_ZOOM = 14.3
        private const val POSITION_FOUND_ZOOM = 16.5
        private const val NO_POSITION_ZOOM = 17.1

        private const val DEBUG_TAG = "BusTO-MapLibreFrag"
        private const val STOP_ACTIVE_IMG = "Stop-active"

        const val FRAGMENT_TAG = "BusTOMapFragment"

        private const val LOCATION_PERMISSION_REQUEST_CODE = 981202

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param stop Eventual stop to center the map into
         * @return A new instance of fragment MapLibreFragment.
         */
        @JvmStatic
        fun newInstance(stop: Stop?) =
            MapLibreFragment().apply {
                arguments = Bundle().let {
                    // Cannot use Parcelable as it requires higher version of Android
                    //stop?.let{putParcelable(STOP_TO_SHOW, it)}
                    stop?.toBundle(it)
                }
            }

    }
}