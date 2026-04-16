package it.reyboz.bustorino.fragments


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toast
import it.reyboz.bustorino.backend.FiveTNormalizer
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
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
import it.reyboz.bustorino.map.MapLibreStyles
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.viewmodels.StopsMapViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val STOP_TO_SHOW = "stoptoshow"

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
    private var mapInitCompleted =false
    private var stopsRedrawnTimes = 0

    //bottom Sheet behavior in GeneralMapLibreFragment
    //private var stopActiveSymbol: Symbol? = null

    // Location stuff
    private lateinit var locationManager: LocationManager
    private lateinit var showUserPositionButton: ImageButton
    private lateinit var centerUserButton: ImageButton
    private lateinit var followUserButton: ImageButton

    private var followingUserLocation = false
    private var pendingLocationActivation = false
    private var ignoreCameraMovementForFollowing = true
    private var enablingPositionFromClick = false
    private var restoredMapCamera = AtomicBoolean()
    private var permissionsGranted = false

    //TODO: Rewrite this mess using LocationEngineProvider in MapLibre
    private val positionRequestLauncher = registerForActivityResult<Array<String>, Map<String, Boolean>>(
            ActivityResultContracts.RequestMultiplePermissions(), ActivityResultCallback { result ->
                if (result == null) {
                    Log.w(DEBUG_TAG, "Got asked permission but request is null, doing nothing?")
                }else if(!pendingLocationActivation){
                    /// SHOULD DO NOTHING HERE
                    Log.d(DEBUG_TAG, "Requested location but now there is no pendingLocationActivation")
                } else if (java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_COARSE_LOCATION]
                    && java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_FINE_LOCATION]) {
                    // We can use the position, restart location overlay
                    permissionsGranted = true
                    if (context == null || requireContext().getSystemService(Context.LOCATION_SERVICE) == null)
                        return@ActivityResultCallback
                    val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    var lastLoc = stopsViewModel.lastUserLocation
                    @SuppressLint("MissingPermission")
                    if(lastLoc==null)  lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    else Log.d(DEBUG_TAG, "Got last location from cache")

                    //FIRST CASE: I have no GPS
                    if( !locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) ){
                        setMapLocationEnabled(false, false,false)

                    }
                    else if (lastLoc != null) {

                        if(LatLng(lastLoc.latitude, lastLoc.longitude).distanceTo(DEFAULT_LATLNG) <= MAX_DIST_KM*1000){
                            Log.d(DEBUG_TAG, "Showing the user position")
                            setMapLocationEnabled(true, true, false)
                        } else{
                            setMapLocationEnabled(false, false,false)
                            context?.let{Toast.makeText(it,R.string.too_far_not_showing_location, Toast.LENGTH_SHORT).show()}
                        }
                    } else requestInitialUserLocation()

                } else{
                    Toast.makeText(requireContext(),R.string.location_disabled, Toast.LENGTH_SHORT).show()
                    Log.w(DEBUG_TAG, "No location permission")
                }
            })
    private val showUserPositionRequestLauncher =
        registerForActivityResult<Array<String>, Map<String, Boolean>>(
            ActivityResultContracts.RequestMultiplePermissions(),
            ActivityResultCallback { result ->
                if (result == null) {
                    Log.w(DEBUG_TAG, "Got asked permission but request is null, doing nothing?")
                } else if (java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_COARSE_LOCATION]
                    && java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_FINE_LOCATION]) {
                    // We can use the position, restart location overlay
                    if (context == null || requireContext().getSystemService(Context.LOCATION_SERVICE) == null)
                        return@ActivityResultCallback ///@registerForActivityResult
                    setMapLocationEnabled(true, true, enablingPositionFromClick)
                } else Log.w(DEBUG_TAG, "No location permission")
            })

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

        showUserPositionButton = rootView.findViewById(R.id.locationEnableIcon)
        showUserPositionButton.setOnClickListener(this::switchUserLocationStatus)
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
            if(context!=null && locationComponent.isLocationComponentEnabled){
                if(followingUserLocation)
                    locationComponent.cameraMode =  CameraMode.NONE
                else locationComponent.cameraMode  = CameraMode.TRACKING
                // CameraMode.TRACKING makes the camera move and jump to the location

               setFollowingUser(!followingUserLocation)
            }
        }
        locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
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

            initMapUserLocation(style, mapReady, requireContext())
            //init stop layer with this
            val stopsInCache = stopsViewModel.getAllStopsLoaded()
            if(stopsInCache.isEmpty())
                initStopsLayer(style, null)
            else
                displayStops(stopsInCache)
            if(showBusLayer) setupBusLayer(style, withLabels = true, busIconsScale = 1.2f)

            // Start observing data now that everything is set up
            observeStops()
        }


        mapReady.addOnCameraIdleListener {
            isUserMovingCamera = false
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
                isUserMovingCamera = true
            }
        }

        mapReady.addOnMapClickListener { point ->
           onMapClickReact(point)
        }

        mapInitCompleted = true
        // we start requesting the bus positions now
        observeBusPositionUpdates()

        //Restoring data

        if (initialStopToShow!=null){
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
                mapReady.cameraPosition = CameraPosition.Builder().target(
                    LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
                ).zoom(DEFAULT_ZOOM).build()
            }
            restoredMapCamera.set(boundsRestored)
        }

        pendingLocationActivation = true
        positionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS)
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
        map?.locationComponent?.lastKnownLocation?.let{
            stopsViewModel.lastUserLocation = it
        }


    }

    override fun onMapDestroy() {
        mapStyle.removeLayer(STOPS_LAYER_ID)
        mapStyle.removeSource(STOPS_SOURCE_ID)

        mapStyle.removeLayer(BUSES_LAYER_ID)
        mapStyle.removeSource(BUSES_SOURCE_ID)


        //map?.locationComponent?.isLocationComponentEnabled = false
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
                "Have " + data.size + " trip updates, has Map start finished: " + mapInitCompleted
            )
            if (mapInitCompleted) updateBusPositionsInMap(data, hasVehicleTracking = true) { veh ->
                showVehicleTripInBottomSheet(veh)
            }
            if (!isDetached && !livePositionsViewModel.useMQTTPositionsLiveData.value!!) livePositionsViewModel.requestDelayedGTFSUpdates(
                3000
            )
        }
    }


    // ------ LOCATION STUFF -----
    @SuppressLint("MissingPermission")
    private fun requestInitialUserLocation() {
        val provider : String = LocationManager.GPS_PROVIDER//getBestLocationProvider()

        //provider.let {
        setLocationIconEnabled(true)
        Toast.makeText(requireContext(), R.string.position_searching_message, Toast.LENGTH_SHORT).show()
        locationManager.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                val distanceToTarget = userLatLng.distanceTo(DEFAULT_LATLNG)

                if (distanceToTarget <= MAX_DIST_KM*1000.0) {
                    map?.let{
                        // if we are still waiting for the position to enable
                        if(pendingLocationActivation)
                            setMapLocationEnabled(true, true, false)
                    }
                } else {
                    Toast.makeText(context, R.string.too_far_not_showing_location, Toast.LENGTH_SHORT).show()
                    setMapLocationEnabled(false,false, false)
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }, null)

    }


    /**
     * Handles logic of enabling the user location on the map
     */
    @SuppressLint("MissingPermission")
    private fun setMapLocationEnabled(enabled: Boolean, assumePermissions: Boolean, fromClick: Boolean) {
        if (enabled) {
            val permissionOk = assumePermissions || Permissions.bothLocationPermissionsGranted(requireContext())

            if (permissionOk) {
                Log.d(DEBUG_TAG, "Permission OK, starting location component, assumed: $assumePermissions, fromClick: $fromClick")
                locationComponent.isLocationComponentEnabled = true
                if (!restoredMapCamera.get()) {
                    locationComponent.cameraMode = CameraMode.TRACKING //CameraMode.TRACKING
                    setFollowingUser(true)
                }
                setLocationIconEnabled(true)
                if (fromClick) Toast.makeText(context, R.string.location_enabled, Toast.LENGTH_SHORT).show()
                pendingLocationActivation =false
                //locationComponent.locationEngine.requestLocationUpdates()
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    //TODO: show dialog for permission rationale
                    Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT).show()
                }
                Log.d(DEBUG_TAG, "Requesting permission to show user location")
                showUserPositionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS)
            }
        } else{
            locationComponent.isLocationComponentEnabled = false
            setFollowingUser(false)
            setLocationIconEnabled(false)
            if (fromClick) {
                Toast.makeText(requireContext(), R.string.location_disabled, Toast.LENGTH_SHORT).show()
                if(pendingLocationActivation) pendingLocationActivation=false //Cancel the request for the enablement of the position
            }
        }

    }



    private fun setLocationIconEnabled(enabled: Boolean){
        if (enabled)
            showUserPositionButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_red))
        else
            showUserPositionButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_grey))

    }

    private fun updateFollowingIcon(enabled: Boolean){
        if(enabled)
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.walk_circle_active))
        else
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.walk_circle_inactive))

    }
    private fun setFollowingUser(following: Boolean){
        updateFollowingIcon(following)
        followingUserLocation = following
        if(following)
            ignoreCameraMovementForFollowing = true
    }


    /**
     * Method used for enabling / disabling the location
     */
    private fun switchUserLocationStatus(view: View?){
        if(pendingLocationActivation || locationComponent.isLocationComponentEnabled)
            setMapLocationEnabled(false, false, true)
        else{
            if(locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                pendingLocationActivation = true
                Log.d(DEBUG_TAG, "Request enable location")
                setMapLocationEnabled(true, false, true)
            } else{
                Log.w(DEBUG_TAG, "Cannot find location, no GPS")
            }

        }
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
        private const val MAX_DIST_KM = 90.0

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