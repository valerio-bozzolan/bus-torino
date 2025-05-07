/*
	BusTO  - Fragments components
    Copyright (C) 2023 Fabio Mazza

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
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.NameCapitalize
import it.reyboz.bustorino.adapters.StopAdapterListener
import it.reyboz.bustorino.adapters.StopRecyclerAdapter
import it.reyboz.bustorino.backend.FiveTNormalizer
import it.reyboz.bustorino.backend.LivePositionTripPattern
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.gtfs.PolylineParser
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.MatoTripsDownloadWorker
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.*
import it.reyboz.bustorino.middleware.LocationUtils
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.util.ViewUtils
import it.reyboz.bustorino.viewmodels.LinesViewModel
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel
import kotlinx.coroutines.Runnable
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_CENTER
import org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.concurrent.atomic.AtomicBoolean


class LinesDetailFragment() : GeneralMapLibreFragment() {

    private var lineID = ""
    private lateinit var patternsSpinner: Spinner
    private var patternsAdapter: ArrayAdapter<String>? = null

    //Bottom sheet behavior
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<RelativeLayout>
    private var bottomLayout: RelativeLayout? = null
    private lateinit var stopTitleTextView: TextView
    private lateinit var stopNumberTextView: TextView
    private lateinit var linesPassingTextView: TextView
    private lateinit var arrivalsCard: CardView
    private lateinit var directionsCard: CardView
    private lateinit var bottomrightImage: ImageView

    //private var isBottomSheetShowing = false
    private var shouldMapLocationBeReactivated = true

    private var toRunWhenMapReady : Runnable? = null
    private var mapInitialized = AtomicBoolean(false)

    //private var patternsSpinnerState: Parcelable? = null

    private lateinit var currentPatterns: List<MatoPatternWithStops>

    //private lateinit var map: MapView
    private var patternShown: MatoPatternWithStops? = null

    private val viewModel: LinesViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private var firstInit = true
    private var pausedFragment = false
    private lateinit var switchButton: ImageButton

    private var favoritesButton: ImageButton? = null
    private var locationIcon: ImageButton? = null
    private var isLineInFavorite = false
    private var appContext: Context? = null
    private var isLocationPermissionOK = false
    private val lineSharedPrefMonitor = SharedPreferences.OnSharedPreferenceChangeListener { pref, keychanged ->
        if(keychanged!=PreferencesHolder.PREF_FAVORITE_LINES || lineID.isEmpty()) return@OnSharedPreferenceChangeListener
        val newFavorites = pref.getStringSet(PreferencesHolder.PREF_FAVORITE_LINES, HashSet())
        newFavorites?.let {favorites->
            isLineInFavorite = favorites.contains(lineID)
            //if the button has been intialized, change the icon accordingly
            favoritesButton?.let { button->
                //avoid crashes if fragment not attached
                if(context==null) return@let
                if(isLineInFavorite) {
                    button.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_filled, null))
                    appContext?.let {  Toast.makeText(it,R.string.favorites_line_add,Toast.LENGTH_SHORT).show()}
                } else {
                    button.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_outline, null))
                    appContext?.let {Toast.makeText(it,R.string.favorites_line_remove,Toast.LENGTH_SHORT).show()}
                }


            }
        }
    }

    private lateinit var stopsRecyclerView: RecyclerView
    private lateinit var descripTextView: TextView
    private var stopIDFromToShow: String? = null
    //adapter for recyclerView
    private val stopAdapterListener= object : StopAdapterListener {
        override fun onTappedStop(stop: Stop?) {

            if(viewModel.shouldShowMessage) {
                Toast.makeText(context, R.string.long_press_stop_4_options, Toast.LENGTH_SHORT).show()
                viewModel.shouldShowMessage=false
            }
            stop?.let {
                fragmentListener.requestArrivalsForStopID(it.ID)
            }
            if(stop == null){
                Log.e(DEBUG_TAG,"Passed wrong stop")
            }
            if(fragmentListener == null){
                Log.e(DEBUG_TAG, "Fragment listener is null")
            }
        }

        override fun onLongPressOnStop(stop: Stop?): Boolean {
            TODO("Not yet implemented")
        }

    }
    private val patternsSorter = Comparator{ p1: MatoPatternWithStops, p2: MatoPatternWithStops ->
        if(p1.pattern.directionId != p2.pattern.directionId)
            return@Comparator p1.pattern.directionId - p2.pattern.directionId
        else
            return@Comparator -1*(p1.stopsIndices.size - p2.stopsIndices.size)

    }

    //map data
    //style and sources are in GeneralMapLibreFragment
    private lateinit var locationComponent: LocationComponent
    private lateinit var polylineSource: GeoJsonSource
    private lateinit var polyArrowSource: GeoJsonSource

    private var savedCameraPosition: CameraPosition? = null
    private var vehShowing = ""

    private var stopsLayerStarted = false
    private var lastStopsSizeShown = 0
    private var lastUpdateTime:Long = -2

    //BUS POSITIONS
    private val updatesByVehDict = HashMap<String, LivePositionTripPattern>(5)
    private val animatorsByVeh = HashMap<String, ValueAnimator>()

    private var lastLocation : Location? = null
    private var enablingPositionFromClick = false

    private var polyline: LineString? = null

    private val showUserPositionRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            ActivityResultCallback { result ->
                if (result == null) {
                    Log.w(DEBUG_TAG, "Got asked permission but request is null, doing nothing?")
                } else if (java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_COARSE_LOCATION]
                    && java.lang.Boolean.TRUE == result[Manifest.permission.ACCESS_FINE_LOCATION]) {
                    // We can use the position, restart location overlay
                    if (context == null || requireContext().getSystemService(Context.LOCATION_SERVICE) == null)
                        return@ActivityResultCallback ///@registerForActivityResult
                    setMapUserLocationEnabled(true, true, enablingPositionFromClick)
                } else Log.w(DEBUG_TAG, "No location permission")
            })
    //private var stopPosList = ArrayList<GeoPoint>()

    //fragment actions
    private lateinit var fragmentListener: CommonFragmentListener

    private var showOnTopOfLine = false
    private var recyclerInitDone = false

    private var useMQTTPositions = true



    //position of live markers

    private val tripMarkersAnimators = HashMap<String, ObjectAnimator>()

    private val liveBusViewModel: LivePositionsViewModel by viewModels()

    //extra items to use the LibreMap
    private lateinit var symbolManager : SymbolManager
    private var stopActiveSymbol: Symbol? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        lineID = args.getString(LINEID_KEY,"")
        stopIDFromToShow = args.getString(STOPID_FROM_KEY)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //reset statuses
        //isBottomSheetShowing = false
        //stopsLayerStarted = false
        lastStopsSizeShown = 0
        mapInitialized.set(false)

        val rootView = inflater.inflate(R.layout.fragment_lines_detail, container, false)
        //lineID = requireArguments().getString(LINEID_KEY, "")
        arguments?.let {
            lineID = it.getString(LINEID_KEY, "")
        }
        switchButton = rootView.findViewById(R.id.switchImageButton)
        locationIcon = rootView.findViewById(R.id.locationEnableIcon)
        favoritesButton = rootView.findViewById(R.id.favoritesButton)
        stopsRecyclerView = rootView.findViewById(R.id.patternStopsRecyclerView)
        descripTextView = rootView.findViewById(R.id.lineDescripTextView)
        descripTextView.visibility = View.INVISIBLE

        //map stuff
        mapView = rootView.findViewById(R.id.lineMap)
        mapView.getMapAsync(this)


        //init bottom sheet
        val bottomSheet = rootView.findViewById<RelativeLayout>(R.id.bottom_sheet)
        bottomLayout = bottomSheet
        stopTitleTextView = bottomSheet.findViewById(R.id.stopTitleTextView)
        stopNumberTextView = bottomSheet.findViewById(R.id.stopNumberTextView)
        linesPassingTextView = bottomSheet.findViewById(R.id.linesPassingTextView)
        arrivalsCard = bottomSheet.findViewById(R.id.arrivalsCardButton)
        directionsCard = bottomSheet.findViewById(R.id.directionsCardButton)
        bottomrightImage = bottomSheet.findViewById(R.id.rightmostImageView)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // Setup close button
        rootView.findViewById<View>(R.id.btnClose).setOnClickListener {
            hideStopBottomSheet()
        }

        val titleTextView = rootView.findViewById<TextView>(R.id.titleTextView)
        titleTextView.text = getString(R.string.line)+" "+FiveTNormalizer.fixShortNameForDisplay(
            GtfsUtils.getLineNameFromGtfsID(lineID), true)

        favoritesButton?.isClickable = true
        favoritesButton?.setOnClickListener {
            if(lineID.isNotEmpty())
                PreferencesHolder.addOrRemoveLineToFavorites(requireContext(),lineID,!isLineInFavorite)
        }
        val preferences = PreferencesHolder.getMainSharedPreferences(requireContext())
        val favorites = preferences.getStringSet(PreferencesHolder.PREF_FAVORITE_LINES, HashSet())
        if(favorites!=null && favorites.contains(lineID)){
            favoritesButton?.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_filled, null))
            isLineInFavorite = true
        }
        appContext = requireContext().applicationContext
        preferences.registerOnSharedPreferenceChangeListener(lineSharedPrefMonitor)

        patternsSpinner = rootView.findViewById(R.id.patternsSpinner)
        patternsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        patternsSpinner.adapter = patternsAdapter


        initializeRecyclerView()

        switchButton.setOnClickListener{
            if(mapView.visibility == View.VISIBLE){
                hideMapAndShowStopList()
            } else{
               hideStopListAndShowMap()
            }
        }
        locationIcon?.let {view ->
            if(!LocationUtils.isLocationEnabled(requireContext()) || !Permissions.anyLocationPermissionsGranted(requireContext()))
                setLocationIconEnabled(false)
            //set click Listener
            view.setOnClickListener(this::onPositionIconButtonClick)
        }
        //set
        //INITIALIZE VIEW MODELS
        viewModel.setRouteIDQuery(lineID)
        liveBusViewModel.setGtfsLineToFilterPos(lineID, null)

        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTPositions = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, "mqtt").contentEquals("mqtt")

        viewModel.patternsWithStopsByRouteLiveData.observe(viewLifecycleOwner){
            patterns -> savePatternsToShow(patterns)
        }
        /*
         */
        viewModel.stopsForPatternLiveData.observe(viewLifecycleOwner) { stops ->
            if(mapView.visibility ==View.VISIBLE)
                patternShown?.let{
                    // We have the pattern and the stops here, time to display them
                    displayPatternWithStopsOnMap(it,stops, true)
                } ?:{
                    Log.w(DEBUG_TAG, "The viewingPattern is null!")
                }
            else{
                if(stopsRecyclerView.visibility==View.VISIBLE)
                    showStopsInRecyclerView(stops)
            }
        }
        viewModel.gtfsRoute.observe(viewLifecycleOwner){route->
            if(route == null){
                //need to close the fragment
                activity?.supportFragmentManager?.popBackStack()
                return@observe
            }
             descripTextView.text = route.longName
            descripTextView.visibility = View.VISIBLE
        }
        /*

         */

        Log.d(DEBUG_TAG,"Data ${viewModel.stopsForPatternLiveData.value}")

        //listeners
        patternsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val currentShownPattern = patternShown?.pattern
                val patternWithStops = currentPatterns[position]

                Log.d(DEBUG_TAG, "request stops for pattern ${patternWithStops.pattern.code}")
                setPatternAndReqStops(patternWithStops)

                if(mapView.visibility == View.VISIBLE) {
                    //Clear buses if we are changing direction
                    currentShownPattern?.let { patt ->
                        if(patt.directionId != patternWithStops.pattern.directionId){
                            stopAnimations()
                            updatesByVehDict.clear()
                            updatePositionsIcons(true)
                            liveBusViewModel.retriggerPositionUpdate()
                        }
                    }
                }
                liveBusViewModel.setGtfsLineToFilterPos(lineID, patternWithStops.pattern)

            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }
        Log.d(DEBUG_TAG, "Views created!")

        return rootView
    }

    // ------------- UI switch stuff ---------

    private fun hideMapAndShowStopList(){
        mapView.visibility = View.GONE
        stopsRecyclerView.visibility = View.VISIBLE
        locationIcon?.visibility = View.GONE

        viewModel.setMapShowing(false)
        if(useMQTTPositions) liveBusViewModel.stopMatoUpdates()
        //map.overlayManager.remove(busPositionsOverlay)

        switchButton.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_map_white_30))

        hideStopBottomSheet()

        if(locationComponent.isLocationComponentEnabled){
            locationComponent.isLocationComponentEnabled = false
            shouldMapLocationBeReactivated = true
        } else
            shouldMapLocationBeReactivated = false
    }

    private fun hideStopListAndShowMap(){
        stopsRecyclerView.visibility = View.GONE
        mapView.visibility = View.VISIBLE
        locationIcon?.visibility = View.VISIBLE
        viewModel.setMapShowing(true)

        //map.overlayManager.add(busPositionsOverlay)
        //map.
        if(useMQTTPositions)
            liveBusViewModel.requestMatoPosUpdates(GtfsUtils.getLineNameFromGtfsID(lineID))
        else
            liveBusViewModel.requestGTFSUpdates()

        switchButton.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_list_30))

        if(shouldMapLocationBeReactivated && Permissions.bothLocationPermissionsGranted(requireContext())){
            locationComponent.isLocationComponentEnabled = true
        }
    }

    private fun setLocationIconEnabled(setTrue: Boolean){
        if(setTrue)
            locationIcon?.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_red))
        else
            locationIcon?.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.location_circlew_grey))
    }

    /**
     * Handles logic of enabling the user location on the map
     */
    @SuppressLint("MissingPermission")
    private fun setMapUserLocationEnabled(enabled: Boolean, assumePermissions: Boolean, fromClick: Boolean) {
        if (enabled) {
            val permissionOk = assumePermissions || Permissions.bothLocationPermissionsGranted(requireContext())

            if (permissionOk) {
                Log.d(DEBUG_TAG, "Permission OK, starting location component, assumed: $assumePermissions")
                locationComponent.isLocationComponentEnabled = true
                //locationComponent.cameraMode = CameraMode.TRACKING //CameraMode.TRACKING

                setLocationIconEnabled(true)
                if (fromClick) Toast.makeText(context, R.string.location_enabled, Toast.LENGTH_SHORT).show()
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
            setLocationIconEnabled(false)
            if (fromClick) {
                Toast.makeText(requireContext(), R.string.location_disabled, Toast.LENGTH_SHORT).show()
                 //TODO: Cancel the request for the enablement of the position if needed
            }
        }

    }

    /**
     * Switch position icon from activ
     */
    private fun onPositionIconButtonClick(view: View){
        if(locationComponent.isLocationComponentEnabled) setMapUserLocationEnabled(false, false, true)
        else{
            setMapUserLocationEnabled(true, false, true)
        }
    }

    // ------------- Map Code -------------------------
    /**
     * This method sets up the map and the layers
     */
    override fun onMapReady(mapReady: MapLibreMap) {
        this.map = mapReady

        val context = requireContext()
        val mjson = MapLibreStyles.getJsonStyleFromAsset(context, PreferencesHolder.getMapLibreStyleFile(context))        //ViewUtils.loadJsonFromAsset(requireContext(),"map_style_good.json")

        activity?.run {
            val builder = Style.Builder().fromJson(mjson!!)

            mapReady.setStyle(builder) { style ->

                mapStyle = style
                //setupLayers(style)

                // Start observing data
                initMapUserLocation(style, mapReady, requireContext())

                //if(!stopsLayerStarted)
                initStopsPolyLineLayers(style, FeatureCollection.fromFeatures(ArrayList<Feature>()), null, null)
                /*if(!stopsLayerStarted) {
                    Log.d(DEBUG_TAG, "Stop layer is not started yet")
                    initStopsPolyLineLayers(style, FeatureCollection.fromFeatures(ArrayList<Feature>()), null)
                }
                 */
                setupBusLayer(style)

                symbolManager = SymbolManager(mapView,mapReady,style)
                symbolManager.iconAllowOverlap = true
                symbolManager.textAllowOverlap = false

                symbolManager.addClickListener{ _ ->
                    if (stopActiveSymbol!=null){
                        hideStopBottomSheet()

                        return@addClickListener true
                    } else
                        return@addClickListener false
                }

                mapViewModel.stopShowing?.let {
                    openStopInBottomSheet(it)
                }
                mapViewModel.stopShowing = null
                toRunWhenMapReady?.run()
                toRunWhenMapReady = null
                mapInitialized.set(true)

                if(patternShown!=null){
                    viewModel.stopsForPatternLiveData.value?.let {
                        Log.d(DEBUG_TAG, "Show stops from the cache")
                        displayPatternWithStopsOnMap(patternShown!!, it, true)
                    }
                }

            }

            mapReady.addOnMapClickListener { point ->
                val screenPoint = mapReady.projection.toScreenLocation(point)
                val features = mapReady.queryRenderedFeatures(screenPoint, STOPS_LAYER_ID)
                val busNearby = mapReady.queryRenderedFeatures(screenPoint, BUSES_LAYER_ID)
                if (features.isNotEmpty()) {
                    val feature = features[0]
                    val id = feature.getStringProperty("id")
                    val name = feature.getStringProperty("name")
                    //Toast.makeText(requireContext(), "Clicked on $name ($id)", Toast.LENGTH_SHORT).show()
                    val stop = viewModel.getStopByID(id)
                    stop?.let {
                        if (isBottomSheetShowing() || vehShowing.isNotEmpty()){
                            hideStopBottomSheet()
                        }
                        openStopInBottomSheet(it)

                        //move camera
                        if(it.latitude!=null && it.longitude!=null)
                            mapReady.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude!!,it.longitude!!)),750)
                    }
                    return@addOnMapClickListener true
                } else if (busNearby.isNotEmpty()){
                    val feature = busNearby[0]
                    val vehid = feature.getStringProperty("veh")
                    val route = feature.getStringProperty("line")
                    if(isBottomSheetShowing())
                        hideStopBottomSheet()
                    //if(context!=null){
                     //   Toast.makeText(context, "Veh $vehid on route ${route.slice(0..route.length-2)}", Toast.LENGTH_SHORT).show()
                    //}
                    showVehicleTripInBottomSheet(vehid)
                    updatesByVehDict[vehid]?.let {
                        //if (it.posUpdate.latitude != null && it.longitude != null)
                            mapReady.animateCamera(
                                CameraUpdateFactory.newLatLng(LatLng(it.posUpdate.latitude, it.posUpdate.longitude)),
                                750
                            )
                    }

                    return@addOnMapClickListener true
                }
                false
            }

            // we start requesting the bus positions now
            observeBusPositionUpdates()

        }
        /*savedMapStateOnPause?.let{
            restoreMapStateFromBundle(it)
            pendingLocationActivation = false
            Log.d(DEBUG_TAG, "Restored map state from the saved bundle")
        }

         */

        val zoom = 12.0
        val latlngTarget = LatLng(MapLibreFragment.DEFAULT_CENTER_LAT, MapLibreFragment.DEFAULT_CENTER_LON)

        mapReady.cameraPosition = savedCameraPosition ?:CameraPosition.Builder().target(latlngTarget).zoom(zoom).build()

        savedCameraPosition = null

        if(shouldMapLocationBeReactivated) setMapUserLocationEnabled(true, false, false)
    }

    private fun observeBusPositionUpdates(){
        //live bus positions
        liveBusViewModel.filteredLocationUpdates.observe(viewLifecycleOwner){ pair ->
            //Log.d(DEBUG_TAG, "Received ${updates.size} updates for the positions")
            val updates = pair.first
            val vehiclesNotOnCorrectDir = pair.second
            if(mapView.visibility == View.GONE || patternShown ==null){
                //DO NOTHING
                Log.w(DEBUG_TAG, "not doing anything because map is not visible")
                return@observe
            }
            //remove vehicles not on this direction
            removeVehiclesData(vehiclesNotOnCorrectDir)
            updateBusPositionsInMap(updates)
            //if not using MQTT positions
            if(!useMQTTPositions){
                liveBusViewModel.requestDelayedGTFSUpdates(2000)
            }
        }

        //download missing tripIDs
        liveBusViewModel.tripsGtfsIDsToQuery.observe(viewLifecycleOwner){
            //gtfsPosViewModel.downloadTripsFromMato(dat);
            MatoTripsDownloadWorker.requestMatoTripsDownload(
                it, requireContext().applicationContext,
                "BusTO-MatoTripDownload"
            )
        }
    }

    private fun isBottomSheetShowing(): Boolean{
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * Initialize the map location, but do not enable the component
     */
    @SuppressLint("MissingPermission")
    private fun initMapUserLocation(style: Style, map: MapLibreMap, context: Context){
        locationComponent = map.locationComponent
        val locationComponentOptions =
            LocationComponentOptions.builder(context)
                .pulseEnabled(false)
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
     * Update the bottom sheet with the stop information
     */
    override fun openStopInBottomSheet(stop: Stop){
        bottomLayout?.let {

            //lay.findViewById<TextView>(R.id.stopTitleTextView).text ="${stop.ID} - ${stop.stopDefaultName}"
            val stopName = stop.stopUserName ?: stop.stopDefaultName
            stopTitleTextView.text = stopName//stop.stopDefaultName
            stopNumberTextView.text = stop.ID
            stopTitleTextView.visibility = View.VISIBLE

            val string_show = if (stop.numRoutesStopping==0) ""
            else if (stop.numRoutesStopping <= 1)
                requireContext().getString(R.string.line_fill, stop.routesThatStopHereToString())
            else requireContext().getString(R.string.lines_fill, stop.routesThatStopHereToString())
            linesPassingTextView.text = string_show

            //SET ON CLICK LISTENER
            arrivalsCard.setOnClickListener{
                fragmentListener?.requestArrivalsForStopID(stop.ID)
            }

            arrivalsCard.visibility = View.VISIBLE

            directionsCard.setOnClickListener {
                ViewUtils.openStopInOutsideApp(stop, context)
            }
            context?.let {
                val colorIcon = ViewUtils.getColorFromTheme(it, android.R.attr.colorAccent)//ResourcesCompat.getColor(resources,R.attr.colorAccent,activity?.theme)
                ViewCompat.setBackgroundTintList(directionsCard, ColorStateList.valueOf(colorIcon))
            }

            bottomrightImage.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.navigation_right,  activity?.theme))

        }
        //add stop marker
        if (stop.latitude!=null && stop.longitude!=null) {
            stopActiveSymbol = symbolManager.create(
                SymbolOptions()
                    .withLatLng(LatLng(stop.latitude!!, stop.longitude!!))
                    .withIconImage(STOP_ACTIVE_IMG)
                    .withIconAnchor(ICON_ANCHOR_CENTER)

            )

        }
        Log.d(DEBUG_TAG, "Shown stop $stop in bottom sheet")
        shownStopInBottomSheet = stop

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        //isBottomSheetShowing = true
    }
    // Hide the bottom sheet and remove extra symbol
    private fun hideStopBottomSheet(){
        if (stopActiveSymbol!=null){
            symbolManager.delete(stopActiveSymbol)
            stopActiveSymbol = null
        }
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        //isBottomSheetShowing = false

        //reset states
        shownStopInBottomSheet = null
        vehShowing = ""

    }

    private fun showVehicleTripInBottomSheet(veh: String){
        val data = updatesByVehDict[veh]
        if(data==null) {
            Log.w(DEBUG_TAG,"Asked to show vehicle $veh, but it's not present in the updates")
            return
        }

        bottomLayout?.let {
            val lineName = FiveTNormalizer.fixShortNameForDisplay(
                GtfsUtils.getLineNameFromGtfsID(data.posUpdate.routeID), true)
            stopNumberTextView.text = requireContext().getString(R.string.line_fill, lineName)
            val pat = data.pattern
            if (pat!=null){
                stopTitleTextView.text = pat.headsign
                stopTitleTextView.visibility = View.VISIBLE
                Log.d(DEBUG_TAG, "Showing headsign ${pat.headsign} for vehicle $veh")
            } else {
                //stopTitleTextView.text = "NN"
                stopTitleTextView.visibility = View.GONE
            }
            linesPassingTextView.text = data.posUpdate.vehicle
        }

        arrivalsCard.visibility=View.GONE
        bottomrightImage.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_magnifying_glass,  activity?.theme))
        directionsCard.setOnClickListener {
            data.pattern?.let {

                if(patternShown?.pattern?.code == it.code){
                    context?.let { c->Toast.makeText(c, R.string.showing_same_direction, Toast.LENGTH_SHORT).show() }
                }else
                    showPatternWithCode(it.code)
            } //TODO
            // ?: {
            //    context?.let { ctx -> Toast.makeText(ctx,"") }
            //}
        }
        //set color
        val colorBlue = ResourcesCompat.getColor(resources,R.color.blue_620,activity?.theme)
        ViewCompat.setBackgroundTintList(directionsCard, ColorStateList.valueOf(colorBlue))

        vehShowing = veh
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        //isBottomSheetShowing = true
        Log.d(DEBUG_TAG, "Shown vehicle $veh in bottom layout")
    }

    // ------- MAP LAYERS INITIALIZE ----
    /**
     * Initialize the map layers for the stops
     */
    private fun initStopsPolyLineLayers(style: Style, stopFeatures:FeatureCollection, lineFeature: Feature?, arrowFeatures: FeatureCollection?){

        Log.d(DEBUG_TAG, "INIT STOPS CALLED")
        stopsSource = GeoJsonSource(STOPS_SOURCE_ID)
        style.addSource(stopsSource)
        //val context = requireContext()
        val stopIcon = ResourcesCompat.getDrawable(resources,R.drawable.ball, activity?.theme)!!

        val imgStop =  ResourcesCompat.getDrawable(resources,R.drawable.bus_stop_new, activity?.theme)!!
        val polyIconArrow = ResourcesCompat.getDrawable(resources, R.drawable.arrow_up_box_fill, activity?.theme)!!
        //set the image tint
        //DrawableCompat.setTint(imgBus,ContextCompat.getColor(context,R.color.line_drawn_poly))

        // add icon
        style.addImage(STOP_IMAGE_ID,stopIcon)
        style.addImage(POLY_ARROW, polyIconArrow)
        style.addImage(STOP_ACTIVE_IMG, ResourcesCompat.getDrawable(resources, R.drawable.bus_stop_new_highlight, activity?.theme)!!)
        // Stops layer
        val stopsLayer = SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID)
        stopsLayer.withProperties(
            PropertyFactory.iconImage(STOP_IMAGE_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )

        polylineSource = GeoJsonSource(POLYLINE_SOURCE) //lineFeature?.let { GeoJsonSource(POLYLINE_SOURCE, it) } ?: GeoJsonSource(POLYLINE_SOURCE)
        style.addSource(polylineSource)

        val color=ContextCompat.getColor(requireContext(),R.color.line_drawn_poly)
        //paint.style = Paint.Style.FILL_AND_STROKE
        //paint.strokeJoin = Paint.Join.ROUND
        //paint.strokeCap = Paint.Cap.ROUND

        val lineLayer = LineLayer(POLYLINE_LAYER, POLYLINE_SOURCE).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(5.0f), //originally 13f
            PropertyFactory.lineOpacity(1.0f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND)

        )
        polyArrowSource = GeoJsonSource(POLY_ARROWS_SOURCE, arrowFeatures)
        style.addSource(polyArrowSource)
        val arrowsLayer = SymbolLayer(POLY_ARROWS_LAYER, POLY_ARROWS_SOURCE).withProperties(
            PropertyFactory.iconImage(POLY_ARROW),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP)
        )

        val layers = style.layers
        val lastLayers = layers.filter { l-> l.id.contains("city") }
        //Log.d(DEBUG_TAG,"Layers:\n ${style.layers.map { l -> l.id }}")
        Log.d(DEBUG_TAG, "City layers: ${lastLayers.map { l-> l.id }}")
        if(lastLayers.isNotEmpty())
            style.addLayerAbove(lineLayer,lastLayers[0].id)
        else
            style.addLayerBelow(lineLayer,"label_country_1")
        style.addLayerAbove(stopsLayer, POLYLINE_LAYER)
        style.addLayerAbove(arrowsLayer, POLYLINE_LAYER)

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
                //PropertyFactory.iconSize(1.2f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP)

            )
        }
        style.addLayerAbove(busesLayer, STOPS_LAYER_ID)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener){
            fragmentListener = context
        } else throw RuntimeException("$context must implement CommonFragmentListener")

    }


    private fun stopAnimations(){
        for(anim in animatorsByVeh.values){
            anim.cancel()
        }
    }

    /**
     * Save the loaded pattern data, without the stops!
     */
    private fun savePatternsToShow(patterns: List<MatoPatternWithStops>){

        currentPatterns = patterns.sortedWith(patternsSorter)

        patternsAdapter?.let {
            it.clear()
            it.addAll(currentPatterns.map { p->"${p.pattern.directionId} - ${p.pattern.headsign}" })
            it.notifyDataSetChanged()
        }
        // if we are loading from a stop, find it
        val patternToShow = stopIDFromToShow?.let { sID ->
            val stopGtfsID = "gtt:$sID"
            var p: MatoPatternWithStops? = null
            var pLength = 0
            for(patt in currentPatterns){
                for(pstop in patt.stopsIndices){
                    if(pstop.stopGtfsId == stopGtfsID){
                        //found
                        if (patt.stopsIndices.size>pLength){
                            p = patt
                            pLength = patt.stopsIndices.size
                        }
                        //break here, we have determined this pattern has the stop we're looking for
                        break
                    }
                }
            }
            p
        }
        if(stopIDFromToShow!=null){
            if(patternToShow==null)
                Log.w(DEBUG_TAG, "We had to show the pattern from stop $stopIDFromToShow, but we didn't find it")
            else
                Log.d(DEBUG_TAG, "Requesting to show pattern from stop $stopIDFromToShow, found pattern ${patternToShow.pattern.code}")
        }
        //unset the stopID to show
        if(patternToShow!=null) {

            //showPattern(patternToShow)
            patternShown = patternToShow
            stopIDFromToShow = null
        }
        patternShown?.let {
            showPattern(it)
        }

    }

    /**
     * Called when the position of the spinner is updated
     */
    private fun setPatternAndReqStops(patternWithStops: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Requesting stops for pattern ${patternWithStops.pattern.code}")
        viewModel.selectedPatternLiveData.value = patternWithStops
        viewModel.currentPatternStops.value =  patternWithStops.stopsIndices.sortedBy { i-> i.order }
        patternShown = patternWithStops

        viewModel.requestStopsForPatternWithStops(patternWithStops)
    }
    private fun showPattern(patternWs: MatoPatternWithStops){
        //Log.d(DEBUG_TAG, "Finding pattern to show: ${patternWs.pattern.code}")
        var pos = -2
        val code = patternWs.pattern.code.trim()
        for (k in currentPatterns.indices) {
            if (currentPatterns[k].pattern.code.trim() == code) {
                pos = k
                break
            }
        }
        Log.d(DEBUG_TAG, "Requesting stops fro pattern $code in position: $pos")
        if (pos !=-2)
            patternsSpinner.setSelection(pos)
        else
            Log.e(DEBUG_TAG, "Pattern with code $code not found!!")
        //request pattern stops from DB
        //setPatternAndReqStops(patternWs)
    }

    private fun zoomToCurrentPattern(){
        if(polyline==null) return
        val NULL_VALUE = -4000.0
        var maxLat = NULL_VALUE
        var minLat = NULL_VALUE
        var minLong = NULL_VALUE
        var maxLong = NULL_VALUE

        polyline?.let {
            for(p in it.coordinates()){
                val lat = p.latitude()
                val lon = p.longitude()
                // get max latitude
                if(maxLat == NULL_VALUE)
                    maxLat =lat
                else if (maxLat < lat) maxLat = lat
                // find min latitude
                if (minLat ==NULL_VALUE)
                    minLat = lat
                else if (minLat > lat) minLat = lat
                if(maxLong == NULL_VALUE || maxLong < lon )
                    maxLong = lon
                if (minLong == NULL_VALUE || minLong > lon)
                    minLong = lon
            }
            val padding = 50 // Pixel di padding intorno ai limiti

            Log.d(DEBUG_TAG, "Setting limits of bounding box of line: $minLat -> $maxLat, $minLong -> $maxLong")
            val bbox = LatLngBounds.from(maxLat,maxLong, minLat, minLong)
            //map.zoomToBoundingBox(BoundingBox(maxLat+del, maxLong+del, minLat-del, minLong-del), false)
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bbox, padding))
        }


    }

    private fun displayPatternWithStopsOnMap(patternWs: MatoPatternWithStops, stopsToSort: List<Stop>, zoomToPattern: Boolean){
        if(!mapInitialized.get()){
            //set the runnable and do nothing else
            Log.d(DEBUG_TAG, "Delaying pattern display to when map is Ready: ${patternWs.pattern.code}")
            toRunWhenMapReady = Runnable {
                displayPatternWithStopsOnMap(patternWs, stopsToSort, zoomToPattern)
            }
            return
        }

        Log.d(DEBUG_TAG, "Got the stops: ${stopsToSort.map { s->s.gtfsID }}}")
        patternShown = patternWs
        //Problem: stops are not sorted
        val stopOrderD = patternWs.stopsIndices.withIndex().associate{it.value.stopGtfsId to it.index}
        val stopsSorted = stopsToSort.sortedBy { s-> stopOrderD[s.gtfsID] }

        val pattern = patternWs.pattern
        val pointsList = PolylineParser.decodePolyline(pattern.patternGeometryPoly, pattern.patternGeometryLength)

        val pointsToShow = pointsList.map { Point.fromLngLat(it.longitude, it.latitude) }
        Log.d(DEBUG_TAG, "The polyline has ${pointsToShow.size} points to display")
        polyline = LineString.fromLngLats(pointsToShow)
        val lineFeature = Feature.fromGeometry(polyline)
        //Log.d(DEBUG_TAG, "Polyline in JSON is: ${lineFeature.toJson()}")

        // --- STOPS---
        val features = ArrayList<Feature>()
        for (s in stopsSorted){
            if (s.latitude!=null && s.longitude!=null) {
                val loc = if (showOnTopOfLine) findOptimalPosition(s, pointsList)
                                else LatLng(s.latitude!!, s.longitude!!)
                features.add(
                    Feature.fromGeometry(
                        Point.fromLngLat(loc.longitude, loc.latitude),
                        JsonObject().apply {
                            addProperty("id", s.ID)
                            addProperty("name", s.stopDefaultName)
                            //addProperty("routes", s.routesThatStopHereToString()) // Add routes array to JSON object
                        }
                    )
                )
            }
        }
        // -- ARROWS --
        //val splitPolyline = MapLibreUtils.splitPolyWhenDistanceTooBig(pointsList, 200.0)
        val arrowFeatures = ArrayList<Feature>()
        val pointsIndexToShowIcon = MapLibreUtils.findPointsToPutDirectionMarkers(pointsList, stopsSorted, 750.0)

        for (idx in pointsIndexToShowIcon){
            val pnow = pointsList[idx]
            val otherp = if(idx>1) pointsList[idx-1] else pointsList[idx+1]
            val bearing = if (idx>1) MapLibreUtils.getBearing(pointsList[idx-1], pnow) else MapLibreUtils.getBearing(pnow, pointsList[idx+1])

            arrowFeatures.add(Feature.fromGeometry(
                Point.fromLngLat((pnow.longitude+otherp.longitude)/2, (pnow.latitude+otherp.latitude)/2 ), //average
                JsonObject().apply {
                    addProperty("bearing", bearing)
                }
            ))
        }
        Log.d(DEBUG_TAG,"Have put ${features.size} stops to display")

        // if the layer is already started, substitute the stops inside, otherwise start it
        if (stopsLayerStarted) {
            stopsSource.setGeoJson(FeatureCollection.fromFeatures(features))
            polylineSource.setGeoJson(lineFeature)
            polyArrowSource.setGeoJson(FeatureCollection.fromFeatures(arrowFeatures))
            lastStopsSizeShown = features.size
        } else
            map?.let {
                Log.d(DEBUG_TAG, "Map stop layer is not started yet, init layer")
                initStopsPolyLineLayers(mapStyle, FeatureCollection.fromFeatures(features),lineFeature, FeatureCollection.fromFeatures(arrowFeatures))
                Log.d(DEBUG_TAG,"Started stops layer on map")
                lastStopsSizeShown = features.size
                stopsLayerStarted = true
            } ?:{
                Log.e(DEBUG_TAG, "Stops layer is not started!!")
            }

        /* OLD CODE
        for(s in stops){
            val gp =

            val marker = MarkerUtils.makeMarker(
                gp, s.ID, s.stopDefaultName,
                s.routesThatStopHereToString(),
                map,stopTouchResponder, stopIcon,
                R.layout.linedetail_stop_infowindow,
                R.color.line_drawn_poly
            )
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            stopsOverlay.add(marker)
        }
         */
        //POINTS LIST IS NOT IN ORDER ANY MORE
        //if(!map.overlayManager.contains(stopsOverlay)){
        //    map.overlayManager.add(stopsOverlay)
        //}
        if(zoomToPattern) zoomToCurrentPattern()
        //map.invalidate()
    }

    private fun initializeRecyclerView(){
        val llManager = LinearLayoutManager(context)
        llManager.orientation = LinearLayoutManager.VERTICAL

        stopsRecyclerView.layoutManager = llManager
    }
    private fun showStopsInRecyclerView(stops: List<Stop>){

        Log.d(DEBUG_TAG, "Setting stops from: "+viewModel.currentPatternStops.value)
        val orderBy = viewModel.currentPatternStops.value!!.withIndex().associate{it.value.stopGtfsId to it.index}
        val stopsSorted = stops.sortedBy { s -> orderBy[s.gtfsID] }
        val numStops = stopsSorted.size
        Log.d(DEBUG_TAG, "RecyclerView adapter is: ${stopsRecyclerView.adapter}")

        val setNewAdapter = true
        if(setNewAdapter){
            stopsRecyclerView.adapter = StopRecyclerAdapter(
                stopsSorted, stopAdapterListener, StopRecyclerAdapter.Use.LINES,
                NameCapitalize.FIRST
            )

        }

    }

    /**
     * This method fixes the display of the pattern, to be used when clicking on a bus
     */
    private fun showPatternWithCode(patternId: String){
        //var index = 0
        Log.d(DEBUG_TAG, "Showing pattern with code $patternId ")
        for (i in currentPatterns.indices){
            val pattStop = currentPatterns[i]
            if(pattStop.pattern.code == patternId){
                Log.d(DEBUG_TAG, "Pattern found in position $i")
                //setPatternAndReqStops(pattStop)
                patternsSpinner.setSelection(i)
                break
            }
        }
    }

    private fun removeVehiclesData(vehs: List<String>){
        for(v in vehs){
            if (updatesByVehDict.contains(v))
                updatesByVehDict.remove(v)
        }
    }

    /**
     * Update function for the bus positions
     * Takes the processed updates and saves them accordingly
     * Copied from MapLibreFragment, removing the labels
     */
    private fun updateBusPositionsInMap(incomingData: HashMap<String, Pair<LivePositionUpdate,TripAndPatternWithStops?>>){
        val vehsNew = HashSet(incomingData.values.map { up -> up.first.vehicle })
        val vehsOld = HashSet(updatesByVehDict.keys)
        Log.d(DEBUG_TAG, "In fragment, have ${incomingData.size} updates to show")

        var countUpds = 0
        //val symbolsToUpdate = ArrayList<Symbol>()
        for (upsWithTrp in incomingData.values){
            val pos = upsWithTrp.first
            val patternStops = upsWithTrp.second
            val vehID = pos.vehicle
            var animate = false
            if (vehsOld.contains(vehID)){
                //update position only if the starting or the stopping position of the animation are in the view
                val oldPos = updatesByVehDict[vehID]?.posUpdate
                val oldPattern = updatesByVehDict[vehID]?.pattern
                var avoidShowingUpdateBecauseIsImpossible = false
                oldPos?.let{

                    if(it.routeID!=pos.routeID) {
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

                val samePosition = oldPos?.let { (it.latitude==pos.latitude)&&(it.longitude == pos.longitude) }?:false
                val setPattern =  (oldPattern==null) && (patternStops!=null)
                if((!samePosition)|| setPattern) {
                    //TODO RESTORE THIS PART
                    /*val isPositionInBounds = isInsideVisibleRegion(
                        pos.latitude, pos.longitude, true
                     ) || (oldPos?.let { isInsideVisibleRegion(it.latitude,it.longitude,true) } ?: false)

                     */
                    val skip = true
                    if (skip) {
                        //animate = true
                        // set the pattern data too
                        updatesByVehDict[vehID]!!.pattern = patternStops?.pattern
                        //this moves both the icon and the label
                        animateNewPositionMove(pos)

                    } else {
                        //update
                        updatesByVehDict[vehID] = LivePositionTripPattern(pos,patternStops?.pattern)
                        /*busLabelSymbolsByVeh[vehID]?.let {
                            it.latLng = LatLng(pos.latitude, pos.longitude)
                            symbolsToUpdate.add(it)
                        }*/
                        //if(vehShowing==vehID)
                        //    map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(pos.latitude, pos.longitude)),500)
                        //TODO: Follow the vehicle
                    }
                }
                countUpds++
            }
            else{
                //not inside
                // update it simply
                updatesByVehDict[vehID] = LivePositionTripPattern(pos, patternStops?.pattern)
                //createLabelForVehicle(pos)
                //if(vehShowing==vehID)
                //    map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(pos.latitude, pos.longitude)),500)
            }
            if (vehID == vehShowing){
                //update the data
                showVehicleTripInBottomSheet(vehID)
            }
        }
        //symbolManager.update(symbolsToUpdate)
        //remove old positions
        Log.d(DEBUG_TAG, "Updated $countUpds vehicles")
        vehsOld.removeAll(vehsNew)
        //now vehsOld contains the vehicles id for those that have NOT been updated
        val currentTimeStamp = System.currentTimeMillis() /1000
        for(vehID in vehsOld){
            //remove after 2 minutes of inactivity
            if (updatesByVehDict[vehID]!!.posUpdate.timestamp - currentTimeStamp > 2*60){
                updatesByVehDict.remove(vehID)
                //removeVehicleLabel(vehID)
            }
        }
        //update UI
        updatePositionsIcons(false)
    }

    /**
     * This is the tricky part, animating the transitions
     * Basically, we need to set the new positions with the data and redraw them all
     */
    private fun animateNewPositionMove(positionUpdate: LivePositionUpdate){
        if (positionUpdate.vehicle !in updatesByVehDict.keys)
            return
        val vehID = positionUpdate.vehicle
        val currentUpdate = updatesByVehDict[positionUpdate.vehicle]
        currentUpdate?.let { it ->
            //cancel current animation on vehicle
            animatorsByVeh[vehID]?.cancel()
            val posUp = it.posUpdate

            val currentPos = LatLng(posUp.latitude, posUp.longitude)
            val newPos = LatLng(positionUpdate.latitude, positionUpdate.longitude)
            val valueAnimator = ValueAnimator.ofObject(MapLibreUtils.LatLngEvaluator(), currentPos, newPos)
            valueAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                private var latLng: LatLng? = null
                override fun onAnimationUpdate(animation: ValueAnimator) {
                    latLng = animation.animatedValue as LatLng
                    //update position on animation
                    val update = updatesByVehDict[positionUpdate.vehicle]!!
                    latLng?.let { ll->
                        update.posUpdate.latitude = ll.latitude
                        update.posUpdate.longitude = ll.longitude
                        updatePositionsIcons(false)
                    }
                }
            })
            /*valueAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    //val update = positionsByVehDict[positionUpdate.vehicle]!!
                    //remove the label at the start of the animation
                    //removeVehicleLabel(vehID)
                    val annot = busLabelSymbolsByVeh[vehID]
                    annot?.let { sym ->
                        sym.textOpacity = 0.0f
                        symbolsToUpdate.add(sym)
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    /*val annot = busLabelSymbolsByVeh[vehID]
                    annot?.let { sym ->
                        sym.textOpacity = 1.0f
                        sym.latLng = newPos //LatLng(newPos)
                        symbolsToUpdate.add(sym)
                    }

                     */
                }
            })
             */
            animatorsByVeh[vehID]?.cancel()
            //set the new position as the current one but with the old lat and lng
            positionUpdate.latitude = posUp.latitude
            positionUpdate.longitude = posUp.longitude
            updatesByVehDict[vehID]!!.posUpdate = positionUpdate
            valueAnimator.duration = 300
            valueAnimator.interpolator = LinearInterpolator()
            valueAnimator.start()

            animatorsByVeh[vehID] = valueAnimator

        } ?: {
            Log.e(DEBUG_TAG, "Have to run animation for veh ${positionUpdate.vehicle} but not in the dict, adding")
            //updatesByVehDict[positionUpdate.vehicle] = positionUpdate
        }
    }

    /**
     * Update the bus positions displayed on the map, from the existing data
     */
    private fun updatePositionsIcons(forced: Boolean){
        //avoid frequent updates
        val currentTime = System.currentTimeMillis()
        if(!forced && currentTime - lastUpdateTime < 60){
            //DO NOT UPDATE THE MAP
            return
        }

        val features = ArrayList<Feature>()//stops.mapNotNull { stop ->
        //stop.latitude?.let { lat ->
        //    stop.longitude?.let { lon ->
        for (dat in updatesByVehDict.values){
            //if (s.latitude!=null && s.longitude!=null)
            val pos = dat.posUpdate
            val point = Point.fromLngLat(pos.longitude, pos.latitude)
            features.add(
                Feature.fromGeometry(
                    point,
                    JsonObject().apply {
                        addProperty("veh", pos.vehicle)
                        addProperty("trip", pos.tripID)
                        addProperty("bearing", pos.bearing ?:0.0f)
                        addProperty("line", pos.routeID)
                    }
                )
            )
            /*busLabelSymbolsByVeh[pos.vehicle]?.let {
                it.latLng = LatLng(pos.latitude, pos.longitude)
                symbolsToUpdate.add(it)
            }

             */
        }
        busesSource.setGeoJson(FeatureCollection.fromFeatures(features))
        //update labels, clear cache to be used
        //symbolManager.update(symbolsToUpdate)
        //symbolsToUpdate.clear()
        lastUpdateTime = System.currentTimeMillis()
    }


    override fun onResume() {
        super.onResume()
        Log.d(DEBUG_TAG, "Resetting paused from onResume")
        mapView.onResume()
        pausedFragment = false

        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTPositions = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, "mqtt").contentEquals("mqtt")

        //separate paths
        if(useMQTTPositions)
            liveBusViewModel.requestMatoPosUpdates(GtfsUtils.getLineNameFromGtfsID(lineID))
        else
            liveBusViewModel.requestGTFSUpdates()


        if(mapViewModel.currentLat.value!=MapViewModel.INVALID) {
            Log.d(DEBUG_TAG, "mapViewModel posi: ${mapViewModel.currentLat.value}, ${mapViewModel.currentLong.value}"+
                    " zoom ${mapViewModel.currentZoom.value}")
            //THIS WAS A FIX FOR THE OLD OSMDROID MAP
            /*val controller = map.controller
            viewLifecycleOwner.lifecycleScope.launch {
                delay(100)
                Log.d(DEBUG_TAG, "zooming back to point")
                controller.animateTo(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!),
                    mapViewModel.currentZoom.value!!,null,null)
                //controller.setCenter(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!))
                //controller.setZoom(mapViewModel.currentZoom.value!!)
            }
             */
        }
        //initialize GUI here
        fragmentListener.readyGUIfor(FragmentKind.LINES)

    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        if(useMQTTPositions) liveBusViewModel.stopMatoUpdates()
        pausedFragment = true
        //save map
        val camera = map?.cameraPosition
        camera?.let {cam->
            mapViewModel.currentLat.value = cam.target?.latitude ?: -400.0
            mapViewModel.currentLong.value = cam.target?.longitude ?: -400.0
            mapViewModel.currentZoom.value = cam.zoom
        }

    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        shownStopInBottomSheet?.let {
            mapViewModel.stopShowing = it
        }
        shouldMapLocationBeReactivated = locationComponent.isLocationComponentEnabled
    }

    override fun onDestroyView() {
        map?.run {
            Log.d(DEBUG_TAG, "Saving camera position")
            savedCameraPosition = cameraPosition
        }

        super.onDestroyView()
        Log.d(DEBUG_TAG, "Destroying the views")

        /*mapStyle.removeLayer(STOPS_LAYER_ID)

        mapStyle?.removeSource(STOPS_SOURCE_ID)

        mapStyle.removeLayer(POLYLINE_LAYER)
        mapStyle.removeSource(POLYLINE_SOURCE)
         */
        //stopsLayerStarted = false
    }

    override fun onMapDestroy() {
        mapStyle.removeLayer(STOPS_LAYER_ID)

        mapStyle.removeSource(STOPS_SOURCE_ID)

        mapStyle.removeLayer(POLYLINE_LAYER)
        mapStyle.removeSource(POLYLINE_SOURCE)
        mapStyle.removeLayer(BUSES_LAYER_ID)
        mapStyle.removeSource(BUSES_SOURCE_ID)


        map?.locationComponent?.isLocationComponentEnabled = false
    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    companion object {
        private const val LINEID_KEY="lineID"
        private const val STOPID_FROM_KEY="stopID"
        private const val STOPS_SOURCE_ID = "stops-source"
        private const val STOPS_LAYER_ID = "stops-layer"
        private const val STOP_ACTIVE_IMG = "stop_active_img"
        private const val STOP_IMAGE_ID = "stop-img"
        private const val POLYLINE_LAYER = "polyline-layer"
        private const val POLYLINE_SOURCE = "polyline-source"

        private const val POLY_ARROWS_LAYER = "arrows-layer"
        private const val POLY_ARROWS_SOURCE = "arrows-source"
        private const val POLY_ARROW ="poly-arrow-img"

        private const val DEBUG_TAG="BusTO-LineDetalFragment"

        fun makeArgs(lineID: String, stopIDFrom: String?): Bundle{
            val b = Bundle()
            b.putString(LINEID_KEY, lineID)
            b.putString(STOPID_FROM_KEY, stopIDFrom)
            return b
        }
        fun newInstance(lineID: String?, stopIDFrom: String?) = LinesDetailFragment().apply {
            lineID?.let { arguments = makeArgs(it, stopIDFrom) }
        }
        @JvmStatic
        private fun findOptimalPosition(stop: Stop, pointsList: MutableList<LatLng>): LatLng{
            if(stop.latitude==null || stop.longitude ==null|| pointsList.isEmpty())
                throw IllegalArgumentException()
            val sLat = stop.latitude!!
            val sLong = stop.longitude!!
            if(pointsList.size < 2)
                return  pointsList[0]
            pointsList.sortBy { utils.measuredistanceBetween(sLat, sLong, it.latitude, it.longitude) }

            val p1 = pointsList[0]
            val p2 = pointsList[1]
            if (p1.longitude == p2.longitude){
                //Log.e(DEBUG_TAG, "Same longitude")
                return LatLng(sLat, p1.longitude)
            } else if (p1.latitude == p2.latitude){
                //Log.d(DEBUG_TAG, "Same latitude")
                return LatLng(p2.latitude,sLong)
            }

            val m = (p1.latitude - p2.latitude) / (p1.longitude - p2.longitude)
            val minv = (p1.longitude-p2.longitude)/(p1.latitude - p2.latitude)
            val cR = p1.latitude - p1.longitude * m

            val longNew = (minv * sLong + sLat -cR ) / (m+minv)
            val latNew = (m*longNew + cR)
            //Log.d(DEBUG_TAG,"Stop ${stop.ID} old pos: ($sLat, $sLong), new pos ($latNew,$longNew)")
            return LatLng(latNew,longNew)
        }

        private const val DEFAULT_CENTER_LAT = 45.12
        private const val DEFAULT_CENTER_LON = 7.6858
    }

    enum class BottomShowing{
        STOP, VEHICLE
    }
}