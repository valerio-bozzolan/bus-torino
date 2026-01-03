package it.reyboz.bustorino.fragments

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.LivePositionTripPattern
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.MapLibreUtils
import it.reyboz.bustorino.util.ViewUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_CENTER
import org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP
import org.maplibre.android.style.layers.Property.TEXT_ANCHOR_CENTER
import org.maplibre.android.style.layers.Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

abstract class GeneralMapLibreFragment: ScreenBaseFragment(), OnMapReadyCallback {
    protected var map: MapLibreMap? = null
    protected var shownStopInBottomSheet : Stop? = null
    protected var savedMapStateOnPause : Bundle? = null


    protected var fragmentListener: CommonFragmentListener? = null

    // Declare a variable for MapView
    protected lateinit var mapView: MapView
    protected lateinit var mapStyle: Style
    protected lateinit var stopsSource: GeoJsonSource
    protected lateinit var busesSource: GeoJsonSource
    protected lateinit var selectedStopSource: GeoJsonSource
    protected lateinit var selectedBusSource: GeoJsonSource //= GeoJsonSource(SEL_BUS_SOURCE)

    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var bottomSheetBehavior: BottomSheetBehavior<RelativeLayout>


    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener(){ pref, key ->
        /*when(key){
            SettingsFragment.LIBREMAP_STYLE_PREF_KEY -> reloadMap()
        }

         */
        if(key == SettingsFragment.LIBREMAP_STYLE_PREF_KEY){
            Log.d(DEBUG_TAG,"ASKING RELOAD OF MAP")

            reloadMap()
        }
    }
    //Bottom sheet behavior in GeneralMapLibreFragment
    protected var bottomLayout: RelativeLayout? = null
    protected lateinit var stopTitleTextView: TextView
    protected lateinit var stopNumberTextView: TextView
    protected lateinit var linesPassingTextView: TextView
    protected lateinit var arrivalsCard: CardView
    protected lateinit var directionsCard: CardView
    protected lateinit var bottomrightImage: ImageView

    protected lateinit var locationComponent: LocationComponent
    protected var lastLocation : Location? = null


    private var lastMapStyle =""

    //BUS POSITIONS
    protected val updatesByVehDict = HashMap<String, LivePositionTripPattern>(5)
    protected val animatorsByVeh = HashMap<String, ValueAnimator>()
    protected var vehShowing = ""
    protected var lastUpdateTime:Long = -2

    private val lifecycleOwnerLiveData = getViewLifecycleOwnerLiveData()


    //extra items to use the LibreMap
    protected lateinit var symbolManager : SymbolManager
    protected var stopActiveSymbol: Symbol? = null
    protected var stopsLayerStarted = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        lastMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())

        //init map
        MapLibre.getInstance(requireContext())

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        lastMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())
        Log.d(DEBUG_TAG, "onCreateView lastMapStyle: $lastMapStyle")
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //init bottom sheet
        val bottomSheet = view.findViewById<RelativeLayout>(R.id.bottom_sheet)
        bottomLayout = bottomSheet
        stopTitleTextView = view.findViewById(R.id.stopTitleTextView)
        stopNumberTextView = view.findViewById(R.id.stopNumberTextView)
        linesPassingTextView = view.findViewById(R.id.linesPassingTextView)
        arrivalsCard = view.findViewById(R.id.arrivalsCardButton)
        directionsCard = view.findViewById(R.id.directionsCardButton)
        bottomrightImage = view.findViewById(R.id.rightmostImageView)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
    }

    override fun onResume() {
        mapView.onResume()
        super.onResume()
        val newMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())
        Log.d(DEBUG_TAG, "onResume newMapStyle: $newMapStyle, lastMapStyle: $lastMapStyle")
        if(newMapStyle!=lastMapStyle){
            reloadMap()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        mapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        Log.d(DEBUG_TAG, "Destroyed mapView Fragment!!")
        super.onDestroy()
    }

    override fun onDestroyView() {
        bottomLayout = null
        super.onDestroyView()
    }

    protected fun reloadMap(){
        /*map?.let {
            Log.d("GeneralMapFragment", "RELOADING MAP")
            //save map state
            savedMapStateOnPause = saveMapStateInBundle()

            onMapDestroy()
            //Destroy and recreate MAP
            mapView.onDestroy()
            mapView.onCreate(null)
            mapView.getMapAsync(this)
        }

         */
        //TODO figure out how to switch map safely
    }

    //For extra stuff to do when the map is destroyed
    abstract fun onMapDestroy()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener){
            fragmentListener = context
        } else throw RuntimeException("$context must implement CommonFragmentListener")

    }
    protected fun restoreMapStateFromBundle(bundle: Bundle): Boolean{
        val nullDouble = -10_000.0
        var boundsRestored =false
        val latCenter = bundle.getDouble("center_map_lat", nullDouble)
        val lonCenter = bundle.getDouble("center_map_lon",nullDouble)
        val zoom = bundle.getDouble("map_zoom", nullDouble)
        val bearing = bundle.getDouble("map_bearing", nullDouble)
        val tilt = bundle.getDouble("map_tilt", nullDouble)
        if(lonCenter!=nullDouble &&latCenter!=nullDouble) map?.let {
            val center = LatLng(latCenter, lonCenter)
            val newPos = CameraPosition.Builder().target(center)
            if(zoom>0) newPos.zoom(zoom)
            if(bearing!=nullDouble) newPos.bearing(bearing)
            if(tilt != nullDouble) newPos.tilt(tilt)
            it.cameraPosition=newPos.build()

            Log.d(DEBUG_TAG, "Restored map state from Bundle, center: $center, zoom: $zoom, bearing $bearing, tilt $tilt")
            boundsRestored =true
        } else{
            Log.d(DEBUG_TAG, "Not restoring map state, center: $latCenter,$lonCenter; zoom: $zoom, bearing: $bearing, tilt $tilt")
        }
        val mStop = bundle.getBundle("shown_stop")?.let {
            Stop.fromBundle(it)
        }
        mStop?.let { openStopInBottomSheet(it) }
        return boundsRestored
    }

    protected fun saveMapStateBeforePause(bundle: Bundle){
        map?.let {
            val newBbox = it.projection.visibleRegion.latLngBounds


            val cp = it.cameraPosition
            bundle.putDouble("center_map_lat", newBbox.center.latitude)
            bundle.putDouble("center_map_lon", newBbox.center.longitude)
            it.cameraPosition.zoom.let { z-> bundle.putDouble("map_zoom",z) }
            bundle.putDouble("map_bearing",cp.bearing)
            bundle.putDouble("map_tilt", cp.tilt)

            val locationComponent = it.locationComponent
            bundle.putBoolean(KEY_LOCATION_ENABLED,locationComponent.isLocationComponentEnabled)
            bundle.putParcelable("last_location", locationComponent.lastKnownLocation)
        }
        shownStopInBottomSheet?.let {
            bundle.putBundle("shown_stop", it.toBundle())
        }
    }

    protected fun saveMapStateInBundle(): Bundle {
        val b = Bundle()
        saveMapStateBeforePause(b)
        return b
    }

    protected fun stopToGeoJsonFeature(s: Stop): Feature{
        return Feature.fromGeometry(
            Point.fromLngLat(s.longitude!!, s.latitude!!),
            JsonObject().apply {
                addProperty("id", s.ID)
                addProperty("name", s.stopDefaultName)
                //addProperty("routes", s.routesThatStopHereToString()) // Add routes array to JSON object
            }
        )
    }
    protected fun isPointInsideVisibleRegion(p: LatLng, other: Boolean): Boolean{
        val bounds = map?.projection?.visibleRegion?.latLngBounds
        var inside = other
        bounds?.let { inside = it.contains(p) }
        return inside
    }

    protected fun isPointInsideVisibleRegion(lat: Double, lon: Double, other: Boolean): Boolean{
        val p = LatLng(lat, lon)
        return isPointInsideVisibleRegion(p, other)
    }


    protected fun removeVehiclesData(vehs: List<String>){
        for(v in vehs){
            if (updatesByVehDict.contains(v)) {
                updatesByVehDict.remove(v)
                if (animatorsByVeh.contains(v)){
                    animatorsByVeh[v]?.cancel()
                    animatorsByVeh.remove(v)
                }
            }
            if (vehShowing==v){
                hideStopBottomSheet()
            }
        }
    }

    // Hide the bottom sheet and remove extra symbol
    protected fun hideStopBottomSheet(){
        if (stopActiveSymbol!=null){
            symbolManager.delete(stopActiveSymbol)
            stopActiveSymbol = null
        }
        if(!showOpenStopWithSymbolLayer()){
            selectedStopSource.setGeoJson(FeatureCollection.fromFeatures(ArrayList<Feature>()))
        }
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        //isBottomSheetShowing = false

        //reset states
        shownStopInBottomSheet = null
        if (vehShowing!=""){
            //we are hiding a vehicle
            vehShowing = ""
            updatePositionsIcons(true)
        }

    }

    protected fun initSymbolManager(mapReady: MapLibreMap , style: Style){
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

    }

    /**
     * Initialize the map location, but do not enable the component
     */
    @SuppressLint("MissingPermission")
    protected fun initMapUserLocation(style: Style, map: MapLibreMap, context: Context){
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
     * Update function for the bus positions
     * Takes the processed updates and saves them accordingly
     * Unified version that works with both fragments
     *
     * @param incomingData Map of updates with optional trip and pattern information
     * @param checkCoordinateValidity If true, validates that coordinates are positive (default: false)
     * @param hasVehicleTracking If true, checks if vehShowing is updated and calls callback (default: true)
     * @param trackVehicleCallback Optional callback to show vehicle details when vehShowing is updated
     */
    protected fun updateBusPositionsInMap(
        incomingData: HashMap<String, Pair<LivePositionUpdate,TripAndPatternWithStops?>>,
        hasVehicleTracking: Boolean = false,
        trackVehicleCallback: ((String) -> Unit)? = null
    ) {
        val vehsNew = HashSet(incomingData.values.map { up -> up.first.vehicle })
        val vehsOld = HashSet(updatesByVehDict.keys)

        Log.d(DEBUG_TAG, "In fragment, have ${incomingData.size} updates to show")

        var countUpds = 0
        var createdVehs = 0

        for (upsWithTrp in incomingData.values) {
            val newPos = upsWithTrp.first
            val patternStops = upsWithTrp.second
            val vehID = newPos.vehicle

            // Validate coordinates
            if (!vehsOld.contains(vehID)) {
                if (newPos.latitude <= 0 || newPos.longitude <= 0) {
                    Log.w(DEBUG_TAG, "Update ignored for veh $vehID on line ${newPos.routeID}, lat: ${newPos.latitude}, lon ${newPos.longitude}")
                    continue
                }
            }

            if (vehsOld.contains(vehID)) {
                // Changing the location of an existing bus
                val oldPosData = updatesByVehDict[vehID]!!
                val oldPos = oldPosData.posUpdate
                val oldPattern = oldPosData.pattern

                var avoidShowingUpdateBecauseIsImpossible = false

                // Check for impossible route changes
                if (oldPos.routeID != newPos.routeID) {
                    val dist = LatLng(oldPos.latitude, oldPos.longitude).distanceTo(
                        LatLng(newPos.latitude, newPos.longitude)
                    )
                    val speed = dist * 3.6 / (newPos.timestamp - oldPos.timestamp) // km/h
                    Log.w(DEBUG_TAG, "Vehicle $vehID changed route from ${oldPos.routeID} to ${newPos.routeID}, distance: $dist, speed: $speed")
                    if (speed > 120 || speed < 0) {
                        avoidShowingUpdateBecauseIsImpossible = true
                    }
                }

                if (avoidShowingUpdateBecauseIsImpossible) {
                    Log.w(DEBUG_TAG, "Update for vehicle $vehID skipped")
                    continue
                }

                // Check if position actually changed
                val samePosition = (oldPos.latitude == newPos.latitude) &&
                        (oldPos.longitude == newPos.longitude)

                val setPattern = (oldPattern == null) && (patternStops != null)

                // Copy old bearing if new one is missing
                if (newPos.bearing == null && oldPos.bearing != null) {
                    newPos.bearing = oldPos.bearing
                }

                if (!samePosition || setPattern) {
                    val newOrOldPosInBounds = isPointInsideVisibleRegion(
                        newPos.latitude, newPos.longitude, true
                    ) || isPointInsideVisibleRegion(oldPos.latitude, oldPos.longitude, true)

                    if (newOrOldPosInBounds) {
                        // Update pattern data if available
                        patternStops?.let {
                            updatesByVehDict[vehID]!!.pattern = it.pattern
                        }
                        // Animate the position change
                        animateNewPositionMove(newPos)
                    } else {
                        // Update position without animation
                        updatesByVehDict[vehID] = LivePositionTripPattern(
                            newPos,
                            patternStops?.pattern
                        )
                    }
                }
                countUpds++
            } else {
                // New vehicle - create entry
                updatesByVehDict[vehID] = LivePositionTripPattern(
                    newPos,
                    patternStops?.pattern
                )
                createdVehs++
            }

            // Update vehicle details if this is the shown/tracked vehicle
            if (hasVehicleTracking && vehShowing.isNotEmpty() && vehID == vehShowing) {
                trackVehicleCallback?.invoke(vehID)
            }
        }

        // Remove old positions
        Log.d(DEBUG_TAG, "Updated $countUpds vehicles, created $createdVehs vehicles")
        vehsOld.removeAll(vehsNew)

        // Clean up stale vehicles (not updated for 2 minutes)
        val currentTimeStamp = System.currentTimeMillis() / 1000
        for (vehID in vehsOld) {
            val posData = updatesByVehDict[vehID]!!
            if (currentTimeStamp - posData.posUpdate.timestamp > 2 * 60) {
                // Remove the bus
                updatesByVehDict.remove(vehID)
                // Cancel and remove animator if exists
                animatorsByVeh[vehID]?.cancel()
                animatorsByVeh.remove(vehID)
            }
        }

        // Update UI
        updatePositionsIcons(false)
    }

    /**
     * Update the bus positions displayed on the map, from the existing data
     *
     * @param forced If true, forces immediate update ignoring the 60ms throttle
     */
    protected fun updatePositionsIcons(forced: Boolean) {
        // Avoid frequent updates - throttle to max once per 60ms
        val currentTime = System.currentTimeMillis()
        if (!forced && currentTime - lastUpdateTime < 60) {
            // Schedule delayed update
            if(lifecycleOwnerLiveData.value != null)
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(200)
                    updatePositionsIcons(forced)
                }
            return
        }

        val busFeatures = ArrayList<Feature>()
        val selectedBusFeatures = ArrayList<Feature>()

        for (dat in updatesByVehDict.values) {
            val pos = dat.posUpdate
            val point = Point.fromLngLat(pos.longitude, pos.latitude)

            val newFeature = Feature.fromGeometry(
                point,
                JsonObject().apply {
                    addProperty("veh", pos.vehicle)
                    addProperty("trip", pos.tripID)
                    addProperty("bearing", pos.bearing ?: 0.0f)
                    addProperty("line", pos.routeID.substringBeforeLast('U'))
                }
            )

            // Separate selected vehicle from others
            if (vehShowing.isNotEmpty() && vehShowing == dat.posUpdate.vehicle) {
                selectedBusFeatures.add(newFeature)
            } else {
                busFeatures.add(newFeature)
            }
        }

        busesSource.setGeoJson(FeatureCollection.fromFeatures(busFeatures))
        selectedBusSource.setGeoJson(FeatureCollection.fromFeatures(selectedBusFeatures))

        lastUpdateTime = System.currentTimeMillis()
    }

    /**
     * Animates the transition of a vehicle from its current position to a new position
     * This is the tricky part - we need to set the new positions with the data and redraw them all
     *
     * @param positionUpdate The new position update to animate to
     */
    protected fun animateNewPositionMove(positionUpdate: LivePositionUpdate) {
        val vehID = positionUpdate.vehicle

        // Check if vehicle exists in our tracking dictionary
        if (vehID !in updatesByVehDict.keys) {
            return
        }

        val currentUpdate = updatesByVehDict[vehID] ?: run {
            Log.e(DEBUG_TAG, "Have to run animation for veh $vehID but not in the dict")
            return
        }

        // Cancel any current animation for this vehicle
        animatorsByVeh[vehID]?.cancel()

        val posUp = currentUpdate.posUpdate
        val currentPos = LatLng(posUp.latitude, posUp.longitude)
        val newPos = LatLng(positionUpdate.latitude, positionUpdate.longitude)

        // Create animator for smooth transition
        val valueAnimator = ValueAnimator.ofObject(
            MapLibreUtils.LatLngEvaluator(),
            currentPos,
            newPos
        )

        valueAnimator.addUpdateListener { animation ->
            val latLng = animation.animatedValue as LatLng

            // Update position during animation
            updatesByVehDict[vehID]?.let { update ->
                update.posUpdate.latitude = latLng.latitude
                update.posUpdate.longitude = latLng.longitude
                updatePositionsIcons(false)
            } ?: run {
                Log.w(DEBUG_TAG, "The bus position to animate has been removed, but the animator is still running!")
            }
        }

        // Set the new position as current but keep old coordinates for animation start
        positionUpdate.latitude = posUp.latitude
        positionUpdate.longitude = posUp.longitude
        updatesByVehDict[vehID]!!.posUpdate = positionUpdate

        // Configure and start animation
        valueAnimator.duration = 300
        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.start()

        // Store animator for potential cancellation
        animatorsByVeh[vehID] = valueAnimator
    }

    /// STOP OPENING
    abstract fun showOpenStopWithSymbolLayer(): Boolean
    /**
     * Update the bottom sheet with the stop information
     */
    protected fun openStopInBottomSheet(stop: Stop){
        bottomLayout?.let {

            //lay.findViewById<TextView>(R.id.stopTitleTextView).text ="${stop.ID} - ${stop.stopDefaultName}"
            val stopName = stop.stopUserName ?: stop.stopDefaultName
            stopTitleTextView.text = stopName//stop.stopDefaultName
            stopNumberTextView.text = getString(R.string.stop_fill,stop.ID)
            stopTitleTextView.visibility = View.VISIBLE

            val string_show = if (stop.numRoutesStopping==0) ""
            else requireContext().getString(R.string.lines_fill, stop.routesThatStopHereToString())
            linesPassingTextView.text = string_show

            //SET ON CLICK LISTENER
            arrivalsCard.setOnClickListener{
                fragmentListener?.requestArrivalsForStopID(stop.ID)
            }

            arrivalsCard.visibility = View.VISIBLE
            directionsCard.visibility = View.VISIBLE

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
            Log.d(DEBUG_TAG, "Showing stop: ${stop.ID}")

            if (showOpenStopWithSymbolLayer()) {
                stopActiveSymbol = symbolManager.create(
                    SymbolOptions()
                        .withLatLng(LatLng(stop.latitude!!, stop.longitude!!))
                        .withIconImage(STOP_ACTIVE_IMG)
                        .withIconAnchor(ICON_ANCHOR_CENTER)

                )
            } else{
                val list = ArrayList<Feature>()
                list.add(stopToGeoJsonFeature(stop))
                selectedStopSource.setGeoJson(
                    FeatureCollection.fromFeatures(list)
                )
            }

        }
        Log.d(DEBUG_TAG, "Shown stop $stop in bottom sheet")
        shownStopInBottomSheet = stop

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }



    protected fun stopAnimations(){
        for(anim in animatorsByVeh.values){
            anim.cancel()
        }
    }

    protected fun addImagesStyle(style: Style){
        style.addImage(
            STOP_IMAGE_ID,
            ResourcesCompat.getDrawable(resources,R.drawable.bus_stop_new, activity?.theme)!!)

        style.addImage(STOP_ACTIVE_IMG, ResourcesCompat.getDrawable(resources, R.drawable.bus_stop_new_highlight, activity?.theme)!!)
        style.addImage("ball",ResourcesCompat.getDrawable(resources, R.drawable.ball, activity?.theme)!!)
        style.addImage(BUS_IMAGE_ID,ResourcesCompat.getDrawable(resources, R.drawable.map_bus_position_icon, activity?.theme)!!)
        style.addImage(BUS_SEL_IMAGE_ID, ResourcesCompat.getDrawable(resources, R.drawable.map_bus_position_icon_sel, activity?.theme)!!)
        val polyIconArrow = ResourcesCompat.getDrawable(resources, R.drawable.arrow_up_box_fill, activity?.theme)!!
        style.addImage(POLY_ARROW, polyIconArrow)

    }

    protected fun initStopsLayer(style: Style, stopsFeatures: FeatureCollection?){
        //determine default layer
        var layerAbove = ""
        if (lastMapStyle == MapLibreUtils.STYLE_OSM_RASTER){
            layerAbove = "osm-raster"
        } else if (lastMapStyle == MapLibreUtils.STYLE_VECTOR){
            layerAbove = "symbol-transit-airfield"
        }
        initStopsLayer(style, stopsFeatures, layerAbove)
    }

    protected fun initStopsLayer(style: Style, stopsFeatures: FeatureCollection?, stopsLayerAbove: String){


        stopsSource = GeoJsonSource(STOPS_SOURCE_ID,stopsFeatures ?: FeatureCollection.fromFeatures(ArrayList<Feature>()))
        style.addSource(stopsSource)


        // Stops layer
        val stopsLayer = SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID)
        stopsLayer.withProperties(
            PropertyFactory.iconImage(STOP_IMAGE_ID),
            PropertyFactory.iconAnchor(ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
            )

        style.addLayerAbove(stopsLayer, stopsLayerAbove ) //"label_country_1") this with OSM Bright


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
    protected fun setupBusLayer(style: Style, withLabels: Boolean =false, busIconsScale: Float = 1.0f) {
        // Buses source
        busesSource = GeoJsonSource(BUSES_SOURCE_ID)
        style.addSource(busesSource)
        //style.addImage("bus_symbol",ResourcesCompat.getDrawable(resources, R.drawable.map_bus_position_icon, activity?.theme)!!)

        selectedBusSource = GeoJsonSource(SEL_BUS_SOURCE)
        style.addSource(selectedBusSource)

        // Buses layer
        val busesLayer = SymbolLayer(BUSES_LAYER_ID, BUSES_SOURCE_ID).apply {
            withProperties(
                PropertyFactory.iconImage(BUS_IMAGE_ID),
                PropertyFactory.iconSize(busIconsScale),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP)

            )
            if (withLabels){
                withProperties(PropertyFactory.textAnchor(TEXT_ANCHOR_CENTER),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textField(Expression.get("line")),
                    PropertyFactory.textColor(Color.WHITE),
                    PropertyFactory.textRotationAlignment(TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textFont(arrayOf("noto_sans_regular")))
            }
        }
        style.addLayerAbove(busesLayer, STOPS_LAYER_ID)

        val selectedBusLayer = SymbolLayer(SEL_BUS_LAYER, SEL_BUS_SOURCE).withProperties(
            PropertyFactory.iconImage(BUS_SEL_IMAGE_ID),
            PropertyFactory.iconSize(busIconsScale),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP)

        )
        style.addLayerAbove(selectedBusLayer, BUSES_LAYER_ID)

    }

    protected fun isBottomSheetShowing(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }


    companion object{
        private const val DEBUG_TAG="GeneralMapLibreFragment"

        const val BUSES_SOURCE_ID = "buses-source"
        const val BUSES_LAYER_ID = "buses-layer"

        const val SEL_STOP_SOURCE="selected-stop-source"
        const val SEL_STOP_LAYER = "selected-stop-layer"

        const val SEL_BUS_SOURCE = "sel_bus_source"
        const val SEL_BUS_LAYER = "sel_bus_layer"

        const val KEY_LOCATION_ENABLED="location_enabled"


        protected const val STOPS_SOURCE_ID = "stops-source"
        protected const val STOPS_LAYER_ID = "stops-layer"

        protected const val STOP_IMAGE_ID = "stop-img"
        protected const val STOP_ACTIVE_IMG = "stop_active_img"
        protected const val BUS_IMAGE_ID = "bus_symbol"
        protected const val BUS_SEL_IMAGE_ID = "sel_bus_symbol"

        protected const val POLYLINE_LAYER = "polyline-layer"
        protected const val POLYLINE_SOURCE = "polyline-source"

        protected const val POLY_ARROWS_LAYER = "arrows-layer"
        protected const val POLY_ARROWS_SOURCE = "arrows-source"
        protected const val POLY_ARROW ="poly-arrow-img"

    }
}