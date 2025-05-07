package it.reyboz.bustorino.fragments


import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.fragments.SettingsFragment.LIVE_POSITIONS_PREF_MQTT_VALUE
import it.reyboz.bustorino.map.MapLibreUtils
import it.reyboz.bustorino.map.MapLibreStyles
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.util.ViewUtils
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel
import it.reyboz.bustorino.viewmodels.StopsMapViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property.*
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val STOP_TO_SHOW = "stoptoshow"

/**
 * A simple [Fragment] subclass.
 * Use the [MapLibreFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapLibreFragment : GeneralMapLibreFragment() {


    protected var fragmentListener: CommonFragmentListener? = null
    private lateinit var locationComponent: LocationComponent
    private var lastLocation: Location? = null
    private val stopsViewModel: StopsMapViewModel by viewModels()
    private var stopsShowing = ArrayList<Stop>(0)
    private var isBottomSheetShowing = false
    //private lateinit var symbolManager: SymbolManager


    // Sources for stops and buses are in GeneralMapLibreFragment
    private var isUserMovingCamera = false
    private var stopsLayerStarted = false
    private var lastStopsSizeShown = 0
    private var lastBBox = LatLngBounds.from(2.0, 2.0, 1.0,1.0)
    private var mapInitCompleted =false
    private var stopsRedrawnTimes = 0

    //bottom Sheet behavior
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<RelativeLayout>
    private var bottomLayout: RelativeLayout? = null
    private lateinit var stopTitleTextView: TextView
    private lateinit var stopNumberTextView: TextView
    private lateinit var linesPassingTextView: TextView
    private lateinit var arrivalsCard: CardView
    private lateinit var directionsCard: CardView

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
                    Log.d(DEBUG_TAG, "HAVE THE PERMISSIONS")
                    if (context == null || requireContext().getSystemService(Context.LOCATION_SERVICE) == null)
                        return@ActivityResultCallback ///@registerForActivityResult
                    val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    var lastLoc = stopsViewModel.lastUserLocation
                    @SuppressLint("MissingPermission")
                    if(lastLoc==null)  lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    else Log.d(DEBUG_TAG, "Got last location from cache")

                    if (lastLoc != null) {
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
    private var useMQTTViewModel = true
    private val livePositionsViewModel : LivePositionsViewModel by viewModels()

    private val positionsByVehDict = HashMap<String, LivePositionUpdate>(5)
    private val animatorsByVeh = HashMap<String, ValueAnimator>()
    private var lastUpdateTime : Long = -1
    //private var busLabelSymbolsByVeh = HashMap<String,Symbol>()
    private val symbolsToUpdate = ArrayList<Symbol>()

    private var initialStopToShow : Stop? = null
    private var initialStopShown = false

    //shown stuff
    //private var savedStateOnStop : Bundle? = null

    private val showBusLayer = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialStopToShow = Stop.fromBundle(arguments)
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

        val restoreBundle = stopsViewModel.savedState
        if(restoreBundle!=null){
            mapView.onCreate(restoreBundle)
        } else mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this) //{ //map ->
            //map.setStyle("https://demotiles.maplibre.org/style.json") }


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
        if (Permissions.bothLocationPermissionsGranted(requireContext())) {
            requestInitialUserLocation()
        } else{
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                //TODO: show dialog for permission rationale
                Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT)
                    .show()
            }
        }


        // Setup close button
        rootView.findViewById<View>(R.id.btnClose).setOnClickListener {
            hideStopBottomSheet()
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
        //ViewUtils.loadJsonFromAsset(requireContext(),"map_style_good.json")


        val builder = Style.Builder().fromJson(mjson!!)

        mapReady.setStyle(builder) { style ->

            mapStyle = style
            //setupLayers(style)

            // Start observing data
            observeStops()
            initMapLocation(style, mapReady, requireContext())
            //init stop layer with this
            val stopsInCache = stopsViewModel.getAllStopsLoaded()
            if(stopsInCache.isEmpty())
                initStopsLayer(style, FeatureCollection.fromFeatures(ArrayList<Feature>()))
            else
                displayStops(stopsInCache)
            if(showBusLayer) setupBusLayer(style)


            /*symbolManager = SymbolManager(mapView,mapReady,style, null, "symbol-transit-airfield")
            symbolManager.iconAllowOverlap = true
            symbolManager.textAllowOverlap = false
            symbolManager.textIgnorePlacement =true


             */
            /*symbolManager.addClickListener{ _ ->
                if (stopActiveSymbol!=null){
                    hideStopBottomSheet()

                    return@addClickListener true
                } else
                    return@addClickListener false
            }

             */

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
            map?.let { setFollowingUser(it.locationComponent.cameraMode == CameraMode.TRACKING) }
        //setFollowingUser()

        }

        mapReady.addOnMapClickListener { point ->
           onMapClickReact(point)
        }

        mapInitCompleted = true
        // we start requesting the bus positions now
        startRequestingPositions()

        //Restoring data
        var boundsRestored = false
        pendingLocationActivation = true
        stopsViewModel.savedState?.let{
            boundsRestored = restoreMapStateFromBundle(it)
            //why are we disabling it?
            pendingLocationActivation = it.getBoolean(KEY_LOCATION_ENABLED,true)
            Log.d(DEBUG_TAG, "Restored map state from the saved bundle: ")
        }
        if(pendingLocationActivation)
            positionRequestLauncher.launch(Permissions.LOCATION_PERMISSIONS)

        //reset saved State at the end
        if((!boundsRestored)) {
            //set initial position
            //center position
            val latlngTarget = initialStopToShow?.let {
                LatLng(it.latitude!!, it.longitude!!)
            } ?: LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
            mapReady.cameraPosition = CameraPosition.Builder().target(latlngTarget).zoom(DEFAULT_ZOOM).build()
        }
        //reset saved state
        stopsViewModel.savedState = null
    }

    private fun onMapClickReact(point: LatLng): Boolean{
        map?.let { mapReady ->
            val screenPoint = mapReady.projection.toScreenLocation(point)
            val features = mapReady.queryRenderedFeatures(screenPoint, STOPS_LAYER_ID)
            val busNearby = mapReady.queryRenderedFeatures(screenPoint, BUSES_LAYER_ID)
            if (features.isNotEmpty()) {
                val feature = features[0]
                val id = feature.getStringProperty("id")
                val name = feature.getStringProperty("name")
                //Toast.makeText(requireContext(), "Clicked on $name ($id)", Toast.LENGTH_SHORT).show()
                val stop = stopsViewModel.getStopByID(id)
                stop?.let { newstop ->
                    val sameStopClicked = shownStopInBottomSheet?.let { newstop.ID==it.ID } ?: false
                    if (isBottomSheetShowing) {
                        hideStopBottomSheet()
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
                val route = feature.getStringProperty("line")

                Toast.makeText(context, "Veh $vehid on route $route", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return false
    }


    private fun initStopsLayer(style: Style, features:FeatureCollection){

        stopsSource = GeoJsonSource(STOPS_SOURCE_ID,features)
        style.addSource(stopsSource)

        // add icon
        style.addImage(STOP_IMAGE_ID,
            ResourcesCompat.getDrawable(resources,R.drawable.bus_stop_new, activity?.theme)!!)

        style.addImage(STOP_ACTIVE_IMG, ResourcesCompat.getDrawable(resources, R.drawable.bus_stop_new_highlight, activity?.theme)!!)
        style.addImage("ball",ResourcesCompat.getDrawable(resources, R.drawable.ball, activity?.theme)!!)
        // Stops layer
        val stopsLayer = SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID)
        stopsLayer.withProperties(
            PropertyFactory.iconImage(STOP_IMAGE_ID),
            PropertyFactory.iconAnchor(ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
            )

        style.addLayerBelow(stopsLayer, "symbol-transit-airfield") //"label_country_1") this with OSM Bright


        selectedStopSource = GeoJsonSource(SEL_STOP_SOURCE, FeatureCollection.fromFeatures(ArrayList<Feature>()))
        style.addSource(selectedStopSource)

        val selStopLayer = SymbolLayer(SEL_STOP_LAYER, SEL_STOP_SOURCE)
        selStopLayer.withProperties(
            PropertyFactory.iconImage(STOP_ACTIVE_IMG),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(ICON_ANCHOR_CENTER),

            )
        style.addLayerAbove(selStopLayer, STOPS_LAYER_ID)

        stopsLayerStarted = true
    }

    /**
     * Setup the Map Layers
     */
    private fun setupBusLayer(style: Style) {
        // Buses source
        busesSource = GeoJsonSource(BUSES_SOURCE_ID)
        style.addSource(busesSource)
        style.addImage("bus_symbol",ResourcesCompat.getDrawable(resources, R.drawable.map_bus_position_icon, activity?.theme)!!)

        // Buses layer
        val busesLayer = SymbolLayer(BUSES_LAYER_ID, BUSES_SOURCE_ID).apply {
            withProperties(
                PropertyFactory.iconImage("bus_symbol"),
                PropertyFactory.iconSize(1.2f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP),

                PropertyFactory.textAnchor(TEXT_ANCHOR_CENTER),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textField(Expression.get("line")),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textRotationAlignment(TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textSize(12f),
                PropertyFactory.textFont(arrayOf("noto_sans_regular"))
            )
        }
        style.addLayerAbove(busesLayer, STOPS_LAYER_ID)

        //Line names layer
        /*vehiclesLabelsSource = GeoJsonSource(LABELS_SOURCE)
        style.addSource(vehiclesLabelsSource)
        val textLayer = SymbolLayer(LABELS_LAYER_ID, LABELS_SOURCE).apply {
            withProperties(
                PropertyFactory.textField("label"),
                PropertyFactory.textSize(30f),
                //PropertyFactory.textHaloColor(Color.BLACK),
                //PropertyFactory.textHaloWidth(1f),

                PropertyFactory.textAnchor(TEXT_ANCHOR_CENTER),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textField(Expression.get("line")),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textRotationAlignment(TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textSize(12f)


        )
        }
        style.addLayerAbove(textLayer, BUSES_LAYER_ID)

         */

    }

    /**
     * Update the bottom sheet with the stop information
     */
    override fun openStopInBottomSheet(stop: Stop){
        bottomLayout?.let {

            //lay.findViewById<TextView>(R.id.stopTitleTextView).text ="${stop.ID} - ${stop.stopDefaultName}"
            val stopName = stop.stopUserName ?: stop.stopDefaultName
            stopTitleTextView.text = stopName//stop.stopDefaultName
            stopNumberTextView.text = stop.ID
            val string_show = if (stop.numRoutesStopping==0) ""
                else if (stop.numRoutesStopping <= 1)
                requireContext().getString(R.string.line_fill, stop.routesThatStopHereToString())
            else requireContext().getString(R.string.lines_fill, stop.routesThatStopHereToString())
            linesPassingTextView.text = string_show

            //SET ON CLICK LISTENER
            arrivalsCard.setOnClickListener{
                fragmentListener?.requestArrivalsForStopID(stop.ID)
            }

            directionsCard.setOnClickListener {
                ViewUtils.openStopInOutsideApp(stop, context)
            }


        }
        //add stop marker
        if (stop.latitude!=null && stop.longitude!=null) {
            /*stopActiveSymbol = symbolManager.create(
                SymbolOptions()
                    .withLatLng(LatLng(stop.latitude!!, stop.longitude!!))
                    .withIconImage(STOP_ACTIVE_IMG)
                    .withIconAnchor(ICON_ANCHOR_CENTER)
                    //.withTextFont(arrayOf("noto_sans_regular")))
             */
            Log.d(DEBUG_TAG, "Showing stop: ${stop.ID}")
            val list = ArrayList<Feature>()
            list.add(stopToGeoJsonFeature(stop))
            selectedStopSource.setGeoJson(
                FeatureCollection.fromFeatures(list)
            )
        }
        shownStopInBottomSheet = stop
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        isBottomSheetShowing = true
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
        mapView.onStart()

        //restore state from viewModel
        stopsViewModel.savedState?.let {
            restoreMapStateFromBundle(it)
            //reset state
            stopsViewModel.savedState = null
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        val keySourcePositions = getString(R.string.pref_positions_source)
        if(showBusLayer) {
            useMQTTViewModel = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(keySourcePositions, LIVE_POSITIONS_PREF_MQTT_VALUE)
                .contentEquals(LIVE_POSITIONS_PREF_MQTT_VALUE)

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
        }

        fragmentListener?.readyGUIfor(FragmentKind.MAP)
        //restore saved state
        savedMapStateOnPause?.let { restoreMapStateFromBundle(it) }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        Log.d(DEBUG_TAG, "Fragment paused")

        savedMapStateOnPause = saveMapStateInBundle()
        if (useMQTTViewModel) livePositionsViewModel.stopMatoUpdates()

    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        Log.d(DEBUG_TAG, "Fragment stopped!")
        stopsViewModel.savedState = Bundle().let {
            mapView.onSaveInstanceState(it)
            it
        }
        //save last location
        map?.locationComponent?.lastKnownLocation?.let{
            stopsViewModel.lastUserLocation = it
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        Log.d(DEBUG_TAG, "Destroyed map Fragment!!")
    }

    override fun onMapDestroy() {
        mapStyle.removeLayer(STOPS_LAYER_ID)
        mapStyle.removeSource(STOPS_SOURCE_ID)

        mapStyle.removeLayer(BUSES_LAYER_ID)
        mapStyle.removeSource(BUSES_SOURCE_ID)


        map?.locationComponent?.isLocationComponentEnabled = false
    }
    override fun getBaseViewForSnackBar(): View? {
        return mapView
    }

    private fun observeStops() {
        // Observe stops
        stopsViewModel.stopsToShow.observe(viewLifecycleOwner) { stops ->
            stopsShowing = ArrayList(stops)
            displayStops(stopsShowing)
            initialStopToShow?.let{ s->
                //show the stop in the bottom sheet
                if(!initialStopShown) {
                    openStopInBottomSheet(s)
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
    // Hide the bottom sheet and remove extra symbol
    private fun hideStopBottomSheet(){
        /*if (stopActiveSymbol!=null){
            symbolManager.delete(stopActiveSymbol)
            stopActiveSymbol = null
        }
         */
        //empty the source
        selectedStopSource.setGeoJson(FeatureCollection.fromFeatures(ArrayList<Feature>()))

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        //remove initial stop
        if(initialStopToShow!=null){
            initialStopToShow = null
        }
        //set showing
        isBottomSheetShowing = false
        shownStopInBottomSheet = null
    }
    // --------------- BUS LOCATIONS STUFF --------------------------
    /**
     * Start requesting position updates
     */
    private fun startRequestingPositions() {
        livePositionsViewModel.updatesWithTripAndPatterns.observe(viewLifecycleOwner) { data: HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>> ->
            Log.d(
                DEBUG_TAG,
                "Have " + data.size + " trip updates, has Map start finished: " + mapInitCompleted
            )
            if (mapInitCompleted) updateBusPositionsInMap(data)
            if (!isDetached && !useMQTTViewModel) livePositionsViewModel.requestDelayedGTFSUpdates(
                3000
            )
        }
    }
    private fun isInsideVisibleRegion(latitude: Double, longitude: Double, nullValue: Boolean): Boolean{
        var isInside = nullValue
        val visibleRegion = map?.projection?.visibleRegion
        visibleRegion?.let {
            val bounds = it.latLngBounds
            isInside = bounds.contains(LatLng(latitude, longitude))
        }
        return isInside
    }

    /*private fun createLabelForVehicle(positionUpdate: LivePositionUpdate){
        val symOpt = SymbolOptions()
            .withLatLng(LatLng(positionUpdate.latitude, positionUpdate.longitude))
            .withTextColor("#ffffff")
            .withTextField(positionUpdate.routeID.substringBeforeLast('U'))
            .withTextSize(13f)
            .withTextAnchor(TEXT_ANCHOR_CENTER)
            .withTextFont(arrayOf( "noto_sans_regular"))//"noto_sans_regular", "sans-serif")) //"noto_sans_regular"))

        val newSymbol = symbolManager.create(symOpt
        )
        Log.d(DEBUG_TAG, "Symbol for veh ${positionUpdate.vehicle}: $newSymbol")
        busLabelSymbolsByVeh[positionUpdate.vehicle] = newSymbol
    }
    private fun removeVehicleLabel(vehicle: String){
        busLabelSymbolsByVeh[vehicle]?.let {
            symbolManager.delete(it)
            busLabelSymbolsByVeh.remove(vehicle)
        }
    }

     */

    /**
     * Update function for the bus positions
     * Takes the processed updates and saves them accordingly
     */
    private fun updateBusPositionsInMap(incomingData: HashMap<String, Pair<LivePositionUpdate,TripAndPatternWithStops?>>){
        val vehsNew = HashSet(incomingData.values.map { up -> up.first.vehicle })
        val vehsOld = HashSet(positionsByVehDict.keys)

        val symbolsToUpdate = ArrayList<Symbol>()
        for (upsWithTrp in incomingData.values){
            val pos = upsWithTrp.first
            val vehID = pos.vehicle
            var animate = false
            if (vehsOld.contains(vehID)){
                //update position only if the starting or the stopping position of the animation are in the view
                val oldPos = positionsByVehDict[vehID]
                var avoidShowingUpdateBecauseIsImpossible = false
                oldPos?.let{
                    if(oldPos.routeID!=pos.routeID) {
                        val dist = LatLng(it.latitude, it.longitude).distanceTo(LatLng(pos.latitude, pos.longitude))
                        val speed = dist*3.6 / (pos.timestamp - it.timestamp) //this should be in km/h
                        Log.w(DEBUG_TAG, "Vehicle $vehID changed route from ${oldPos.routeID} to ${pos.routeID}, distance: $dist, speed: $speed")
                        if (speed > 120 || speed < 0){
                            avoidShowingUpdateBecauseIsImpossible = true
                        }
                    }
                }
                if (avoidShowingUpdateBecauseIsImpossible){
                    // DO NOT SHOW THIS SHIT
                    Log.w(DEBUG_TAG, "Update for vehicle $vehID skipped")
                    continue
                }

                val samePosition = oldPos?.let { (oldPos.latitude==pos.latitude)&&(oldPos.longitude == pos.longitude) }?:false
                if(!samePosition) {
                    val isPositionInBounds = isInsideVisibleRegion(
                        pos.latitude, pos.longitude, false
                    ) || (oldPos?.let { isInsideVisibleRegion(it.latitude,it.longitude, false) } ?: false)
                    if (isPositionInBounds) {
                        //animate = true
                        //this moves both the icon and the label
                        moveVehicleToNewPosition(pos)
                    } else {
                        positionsByVehDict[vehID] = pos
                        /*busLabelSymbolsByVeh[vehID]?.let {
                            it.latLng = LatLng(pos.latitude, pos.longitude)
                            symbolsToUpdate.add(it)
                        }

                         */
                    }
                }
            }
            else if(pos.latitude>0 && pos.longitude>0) {
                    //we should not have to check for this
                    // update it simply
                    positionsByVehDict[vehID] = pos
                    //createLabelForVehicle(pos)
                }else{
                    Log.w(DEBUG_TAG, "Update ignored for veh $vehID on line ${pos.routeID}, lat: ${pos.latitude}, lon ${pos.longitude}")
                }

        }
       // symbolManager.update(symbolsToUpdate)
        //remove old positions
        vehsOld.removeAll(vehsNew)
        //now vehsOld contains the vehicles id for those that have NOT been updated
        val currentTimeStamp = System.currentTimeMillis() /1000
        for(vehID in vehsOld){
            //remove after 2 minutes of inactivity
            if (positionsByVehDict[vehID]!!.timestamp - currentTimeStamp > 2*60){
                positionsByVehDict.remove(vehID)
                //removeVehicleLabel(vehID)
            }
        }
        //update UI
        updatePositionsIcons()
    }

    /**
     * This is the tricky part, animating the transitions
     * Basically, we need to set the new positions with the data and redraw them all
     */
    private fun moveVehicleToNewPosition(positionUpdate: LivePositionUpdate){
        if (positionUpdate.vehicle !in positionsByVehDict.keys)
            return
        val vehID = positionUpdate.vehicle
        val currentUpdate = positionsByVehDict[positionUpdate.vehicle]
        currentUpdate?.let { it ->
            //cancel current animation on vehicle
            animatorsByVeh[vehID]?.cancel()

            val currentPos = LatLng(it.latitude, it.longitude)
            val newPos = LatLng(positionUpdate.latitude, positionUpdate.longitude)
            val valueAnimator = ValueAnimator.ofObject(MapLibreUtils.LatLngEvaluator(), currentPos, newPos)
            valueAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                private var latLng: LatLng? = null
                override fun onAnimationUpdate(animation: ValueAnimator) {
                    latLng = animation.animatedValue as LatLng
                    //update position on animation
                    val update = positionsByVehDict[positionUpdate.vehicle]!!
                    latLng?.let { ll->
                        update.latitude = ll.latitude
                        update.longitude = ll.longitude
                        updatePositionsIcons()
                    }
                }
            })
            valueAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    //val update = positionsByVehDict[positionUpdate.vehicle]!!
                    //remove the label at the start of the animation
                    /*val annot = busLabelSymbolsByVeh[vehID]
                    annot?.let { sym ->
                        sym.textOpacity = 0.0f
                        symbolsToUpdate.add(sym)
                    }

                     */

                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    //recreate the label at the end of the animation
                    //createLabelForVehicle(positionUpdate)
                    /*val annot = busLabelSymbolsByVeh[vehID]
                    annot?.let { sym ->
                        sym.textOpacity = 1.0f
                        sym.latLng = newPos //LatLng(newPos)
                        symbolsToUpdate.add(sym)
                    }

                     */
                }
            })

            //set the new position as the current one but with the old lat and lng
            positionUpdate.latitude = currentUpdate.latitude
            positionUpdate.longitude = currentUpdate.longitude
            positionsByVehDict[vehID] = positionUpdate
            valueAnimator.duration = 300
            valueAnimator.interpolator = LinearInterpolator()
            valueAnimator.start()

            animatorsByVeh[vehID] = valueAnimator

        } ?: {
            Log.e(DEBUG_TAG, "Have to run animation for veh ${positionUpdate.vehicle} but not in the dict, adding")
            positionsByVehDict[positionUpdate.vehicle] = positionUpdate
        }
    }

    /**
     * Update the bus positions displayed on the map, from the existing data
     */
    private fun updatePositionsIcons(){
        //avoid frequent updates
        val currentTime = System.currentTimeMillis()
        //throttle updates when user is moving camera
        val interval = if(isUserMovingCamera) 150 else 60
        if(currentTime - lastUpdateTime < interval){
            //DO NOT UPDATE THE MAP
            return
        }
        val features = ArrayList<Feature>()//stops.mapNotNull { stop ->
        //stop.latitude?.let { lat ->
        //    stop.longitude?.let { lon ->
        for (pos in positionsByVehDict.values){
            //if (s.latitude!=null && s.longitude!=null)
            val point = Point.fromLngLat(pos.longitude, pos.latitude)
                features.add(
                    Feature.fromGeometry(
                        point,
                        JsonObject().apply {
                            addProperty("veh", pos.vehicle)
                            addProperty("trip", pos.tripID)
                            addProperty("bearing", pos.bearing ?:0.0f)
                            addProperty("line", pos.routeID.substringBeforeLast('U'))
                        }
                    )
                )
            /*busLabelSymbolsByVeh[pos.vehicle]?.let {
                it.latLng = LatLng(pos.latitude, pos.longitude)
                symbolsToUpdate.add(it)
            }

             */
        }
        //this updates the positions
        busesSource.setGeoJson(FeatureCollection.fromFeatures(features))
        //update labels, clear cache to be used
        //symbolManager.update(symbolsToUpdate)
        symbolsToUpdate.clear()
        lastUpdateTime = System.currentTimeMillis()
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
     * Initialize the map location, but do not enable the component
     */
    @SuppressLint("MissingPermission")
    private fun initMapLocation(style: Style, map: MapLibreMap, context: Context){
        locationComponent = map.locationComponent
        val locationComponentOptions =
            LocationComponentOptions.builder(context)
                .pulseEnabled(true)
                .build()
        val locationComponentActivationOptions =
            MapLibreUtils.buildLocationComponentActivationOptions(style, locationComponentOptions, context)
        locationComponent.activateLocationComponent(locationComponentActivationOptions)
        locationComponent.isLocationComponentEnabled = false

        lastLocation?.let {
            if (it.accuracy < 200)
                locationComponent.forceLocationUpdate(it)
        }
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
                if (initialStopToShow==null) {
                    locationComponent.cameraMode = CameraMode.TRACKING //CameraMode.TRACKING
                    setFollowingUser(true)
                }
                setLocationIconEnabled(true)
                if (fromClick) Toast.makeText(context, R.string.location_enabled, Toast.LENGTH_SHORT).show()
                pendingLocationActivation =false
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    //TODO: show dialog for permission rationale
                    Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_SHORT).show()
                }
                Log.d(DEBUG_TAG, "Requesting permission to show user location")
                enablingPositionFromClick = fromClick
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

    /**
     * Helper method for GUI
     */
    private fun updateFollowingIcon(enabled: Boolean){
        if(enabled)
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_follow_me_on))
        else
            followUserButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_follow_me))

    }
    private fun setFollowingUser(following: Boolean){
        updateFollowingIcon(following)
        followingUserLocation = following
        if(following)
            ignoreCameraMovementForFollowing = true
    }



    private fun switchUserLocationStatus(view: View?){
        if(pendingLocationActivation || locationComponent.isLocationComponentEnabled) setMapLocationEnabled(false, false, true)
        else{
            pendingLocationActivation = true
            Log.d(DEBUG_TAG, "Request enable location")
            setMapLocationEnabled(true, false, true)

        }
    }

    companion object {
        private const val STOPS_SOURCE_ID = "stops-source"
        private const val STOPS_LAYER_ID = "stops-layer"
        private const val STOPS_LAYER_SEL_ID ="stops-layer-selected"

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