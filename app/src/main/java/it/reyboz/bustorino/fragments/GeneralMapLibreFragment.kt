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

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.FiveTNormalizer
import it.reyboz.bustorino.backend.LivePositionTripPattern
import it.reyboz.bustorino.backend.LivePositionsServiceStatus
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.VehicleUtils
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.MapLibreLocationEngine
import it.reyboz.bustorino.map.MapLibreUtils
import it.reyboz.bustorino.middleware.FusedNativeLocationProvider
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.util.ViewUtils
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel
import it.reyboz.bustorino.viewmodels.MapStateViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
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
import kotlin.time.Duration.Companion.milliseconds

abstract class GeneralMapLibreFragment: ScreenBaseFragment(), OnMapReadyCallback {
    protected var map: MapLibreMap? = null
    protected var shownStopInBottomSheet : Stop? = null
    //protected var savedMapStateOnPause : Bundle? = null


    protected var fragmentListener: CommonFragmentListener? = null

    // Declare a variable for MapView
    protected var mapView: MapView? = null
    protected lateinit var mapStyle: Style
    protected lateinit var stopsSource: GeoJsonSource
    protected lateinit var busesSource: GeoJsonSource
    protected lateinit var selectedStopSource: GeoJsonSource
    protected lateinit var selectedBusSource: GeoJsonSource //= GeoJsonSource(SEL_BUS_SOURCE)

    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    protected var locationEngine: MapLibreLocationEngine? = null
    protected var locationProvider: FusedNativeLocationProvider? = null

    protected var shownToastNoPosition = false
    protected var locationEnabledOnDevice = true
    protected var busLayerStarted = false

    //TODO ACTIVATE THIS
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener(){ pref, key ->
        /*when(key){
            SettingsFragment.LIBREMAP_STYLE_PREF_KEY -> reloadMap()
        }

         */
        if(key == SettingsFragment.LIBREMAP_STYLE_PREF_KEY){
            Log.d(DEBUG_TAG,"ASKING RELOAD OF MAP")

            //reloadMap()
        }
    }

    /**
     * What to do when requesting the permission, when it's ok, initialize the map location component
     */
    protected val positionRequestResponder = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ActivityResultCallback{ res ->
            if(!(res.containsKey(PERM_LOC_COARSE)&&res.containsKey(PERM_LOC_FINE))){
                Log.e(DEBUG_TAG, "Location request does not have the correct keys")
            } else if(res[PERM_LOC_COARSE]!! && res[PERM_LOC_FINE]!!){
                //permission OK, init map location
                val mMap = map
                if(mMap == null){
                    Log.w(DEBUG_TAG, "Location request completed, but map is null!")
                }else{
                    initializeMapLocationComponent(mMap,requireContext(), null)
                }
            } else{
                // PERMISSION DENIED
                // TODO find better way to show the necessity of the permission
                if(shouldShowRequestPermissionRationale(PERM_LOC_FINE))
                    Toast.makeText(requireContext(),
                        R.string.enable_position_message_map, Toast.LENGTH_SHORT).show()
            }
        }
    )
    //Bottom sheet behavior in GeneralMapLibreFragment
    protected var bottomLayout: ConstraintLayout? = null
    protected lateinit var stopTitleTextView: TextView
    protected lateinit var stopNumberTextView: TextView
    protected lateinit var linesPassingTextView: TextView
    protected lateinit var extraBottomTextView: TextView
    protected lateinit var linesBottomTextView: TextView
    protected lateinit var arrivalsCard: CardView
    protected lateinit var directionsCard: CardView
    protected lateinit var bottomrightImage: ImageView
    protected lateinit var locationComponent: LocationComponent
    protected lateinit var busPositionsIconButton: ImageButton
    protected lateinit var vehicleIcon: ImageView

    protected var lastLocation : Location? = null


    private var lastMapStyle =""

    //BUS POSITIONS
    protected val updatesByVehDict = HashMap<String, LivePositionTripPattern>(5)
    protected val animatorsByVeh = HashMap<String, ValueAnimator>()
    protected var vehShowing: String? = null
    protected var lastUpdateTime:Long = -2
    protected var jobUpdate: Job? = null

    //private val lifecycleOwnerLiveData = viewLifecycleOwnerLiveData


    //extra items to use the LibreMap
    protected var symbolManager : SymbolManager? = null
    protected var stopActiveSymbol: Symbol? = null
    protected var stopsLayerStarted = false
    protected val livePositionsViewModel : LivePositionsViewModel by activityViewModels()

    //private lateinit var symbolManager: SymbolManager
    protected val mapStateViewModel: MapStateViewModel by viewModels()
    protected var locationInitialized = false
    protected var mapInitialized = false
    protected var receivedFirstLocation = false


    //location callback to decide if to zoom to the user position
    @SuppressLint("MissingPermission")
    protected val mapLibreLocationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location: Location? = result.lastLocation
            Log.d(DEBUG_TAG, "Received location $location")
            location?.let {
                //check timing of the location
                val currentTime = System.currentTimeMillis()
                val discard = (currentTime - it.time) > 90 * 1000.0  // discard if it is Older than 60 seconds
                if(!discard) {
                    if (!receivedFirstLocation) {
                        onFirstReceivedLocation(it)
                    }
                    receivedFirstLocation = true
                }
            }

            if(receivedFirstLocation){
                //remove this listener once we have received the location
                locationEngine?.removeLocationUpdates(this)
            }

        }

        override fun onFailure(exception: Exception) {
            Log.e(DEBUG_TAG, "Error in getting position: ${exception.message}")
        }
    }

    protected val deviceLocationStatusListener = FusedNativeLocationProvider.LocationStatusListener { isEnabled ->
        mapStateViewModel.locationDeviceEnabled.value = isEnabled
        if(locationEnabledOnDevice && !isEnabled && locationInitialized) {
            warnLocationNotEnabledOnDevice()
            //setMapLocationEnabled(false)
        }
        locationEnabledOnDevice = isEnabled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        lastMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())

        //init map
        MapLibre.getInstance(requireContext())

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        //TODO: Re-create map when this preference changes
        lastMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())
        Log.d(DEBUG_TAG, "onCreateView lastMapStyle: $lastMapStyle")
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    protected fun initBottomSheet(view: View){
        val bottomSheet = view.findViewById<ConstraintLayout>(R.id.bottom_sheet)
        bottomLayout = bottomSheet
        stopTitleTextView = view.findViewById(R.id.stopTitleTextView)
        stopNumberTextView = view.findViewById(R.id.stopNumberTextView)
        linesPassingTextView = view.findViewById(R.id.descriptionTextView)
        arrivalsCard = view.findViewById(R.id.arrivalsCardButton)
        directionsCard = view.findViewById(R.id.directionsCardButton)
        vehicleIcon = view.findViewById(R.id.vehicleIcon)
        linesBottomTextView = view.findViewById(R.id.linesBottomTextView)
        linesBottomTextView.text = getString(R.string.lines_fill, "")
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //init bottom sheet
        initBottomSheet(view)

        bottomrightImage = view.findViewById(R.id.rightmostImageView)
        extraBottomTextView = view.findViewById(R.id.extraBottomTextView)

    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        val newMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())
        Log.d(DEBUG_TAG, "onResume newMapStyle: $newMapStyle, lastMapStyle: $lastMapStyle")
        // TODO: reload style if user changed preferences
        //if(newMapStyle!=lastMapStyle){
        //    reloadMap()
        //}
        if(busLayerStarted)
            updatePositionsIcons(false)
    }

    override fun onLowMemory() {
        mapView?.onLowMemory()
        super.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        Log.d(DEBUG_TAG, "Destroyed mapView Fragment!!")
        busLayerStarted = false
        super.onDestroy()
    }

    override fun onStop() {
        locationProvider?.removeListener(deviceLocationStatusListener)
        mapView?.onStop()
        super.onStop()
    }

    override fun onPause() {
        jobUpdate?.cancel()
        mapView?.onPause()
        super.onPause()
    }


    override fun onDestroyView() {
        bottomLayout = null
        mapInitialized = false
        locationInitialized = false
        super.onDestroyView()
    }

    protected fun warnLocationNotEnabledOnDevice(){
        context?.let{
            Toast.makeText(it,R.string.enable_location_message,Toast.LENGTH_SHORT).show()
        }
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
    }

    //For extra stuff to do when the map is destroyed
    abstract fun onMapDestroy()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener){
            fragmentListener = context
        } else throw RuntimeException("$context must implement CommonFragmentListener")

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
                hideStopOrBusBottomSheet()
            }
        }
    }

    // Hide the bottom sheet and remove extra symbol
    protected open fun hideStopOrBusBottomSheet(){
        if (stopActiveSymbol!=null){
            symbolManager?.delete(stopActiveSymbol)
            stopActiveSymbol = null
        }
        if(!showOpenStopWithSymbolLayer()){
            selectedStopSource.setGeoJson(FeatureCollection.fromFeatures(ArrayList<Feature>()))
        }
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        //isBottomSheetShowing = false

        //reset states
        shownStopInBottomSheet = null
        if (vehShowing!=null){
            //we are hiding a vehicle
            vehShowing = null
            updatePositionsIcons(true)
        }
        extraBottomTextView.visibility = View.GONE

    }

    protected fun initSymbolManager(mapReady: MapLibreMap , style: Style){
        val sm = SymbolManager(mapView!!, mapReady, style)
        sm.iconAllowOverlap = true
        sm.textAllowOverlap = false
        sm.addClickListener { _ ->
            if (stopActiveSymbol != null) {
                hideStopOrBusBottomSheet()
                return@addClickListener true
            } else
                return@addClickListener false
        }
        symbolManager = sm
    }

    /**
     * Change the icon indicating the status of the live Positions
     */
    protected fun setBusPositionsIcon(enabled: Boolean, error: Boolean){
        val ctx = requireContext()
        if(!enabled)
            busPositionsIconButton.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bus_pos_circle_inactive))
        else if(error)
            busPositionsIconButton.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bus_pos_circle_notworking))
        else
            busPositionsIconButton.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bus_pos_circle_active))

    }


    abstract fun onMapLocationComponentInitialized()

    @SuppressLint("MissingPermission")
    protected fun setLocationComponentEnabled(enabled: Boolean): Boolean{
        var changed = false
        map?.apply {
            if(locationComponent.isLocationComponentEnabled !=enabled)
            locationComponent.isLocationComponentEnabled= enabled
        changed = true}
        Log.d(DEBUG_TAG, "Asked to set location component enabled: $enabled, changed: $changed")
        mapStateViewModel.locationUserActive.value = enabled


        return changed
    }

    @SuppressLint("MissingPermission")
    protected fun initializeMapLocationComponent(map: MapLibreMap, context: Context, style: Style?){
        val mStyle = style ?: map.style
        if(locationInitialized){
            Log.w(DEBUG_TAG, "trying to initialize Location Component, but it is already done")
            return
        }
        mStyle?.let{ style ->
            locationComponent = map.locationComponent

            val locProvider = FusedNativeLocationProvider(context)
            locProvider.addListener(deviceLocationStatusListener)
            locationEngine = MapLibreLocationEngine(locProvider)
            locationProvider = locProvider
            val options = LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(false)
                .locationEngine(locationEngine)
                .build()
            locationComponent.activateLocationComponent(options)

            if(BuildConfig.DEBUG) Log.d(DEBUG_TAG, "Initializing location, request initial position")
            startInitialPositionRequest()

            if(!locationEnabledOnDevice){
                warnLocationNotEnabledOnDevice()
            }else {
                setLocationComponentEnabled(true)
            }
            locationInitialized = true
            onMapLocationComponentInitialized()
        }
    }

    @SuppressLint("MissingPermission")
    protected fun startInitialPositionRequest(){
        locationEngine?.requestLocationUpdates(LocationEngineRequest.Builder(500).setDisplacement(20.0f).build(),
            mapLibreLocationCallback, null)

    }
    protected fun stopInitialPositionRequest(){
        locationEngine?.removeLocationUpdates(mapLibreLocationCallback)
    }


    /**
     * Update function for the bus positions
     * Takes the processed updates and saves them accordingly
     * Unified version that works with both fragments
     *
     * @param incomingData Map of updates with optional trip and pattern information
     * @param hasVehicleTracking If true, checks if vehShowing is updated and calls callback (default: true)
     * @param trackVehicleCallback Optional callback to show vehicle details when vehShowing is updated
     */
    protected fun updateBusPositionsInMap(
        incomingData: HashMap<String, Pair<LivePositionUpdate,TripAndPatternWithStops?>>,
        hasVehicleTracking: Boolean = true,
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
            if (hasVehicleTracking && vehShowing?.isNotEmpty() == true && vehID == vehShowing) {
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
     * Shared bottom sheet setup. The [onDirectionsClick] lambda is called when
     * directionsCard is tapped; it receives the pattern code (empty string when
     * no pattern is available) so each subclass can navigate as it sees fit.
     */
    protected fun showVehicleTripInBottomSheet(
        veh: String,
        onDirectionsClick: (patternCode: String, veh: String) -> Unit
    ) {
        val data = updatesByVehDict[veh] ?: run {
            Log.w(DEBUG_TAG, "Asked to show vehicle $veh, but it's not present in the updates")
            return
        }
        bottomLayout?.let {
            val lineName = FiveTNormalizer.fixShortNameForDisplay(
                GtfsUtils.getLineNameFromGtfsID(data.posUpdate.routeID), false
            )
            val pat = data.pattern
            if (pat != null) {
                stopTitleTextView.text = pat.headsign
                stopTitleTextView.visibility = View.VISIBLE
                stopNumberTextView.text = getString(R.string.line_fill_towards, lineName)
            } else {
                stopTitleTextView.visibility = View.GONE
                stopNumberTextView.text = getString(R.string.line_fill, lineName)
            }
            directionsCard.setOnClickListener {
                onDirectionsClick(pat?.code ?: "", veh)
            }
            directionsCard.visibility = View.VISIBLE
            bottomrightImage.setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_magnifying_glass, activity?.theme)
            )
            // if you change this, remember to change the color of the vehicleIcon
            val colorBlue = ResourcesCompat.getColor(resources, R.color.bus_marker_color_selected, activity?.theme)
            ViewCompat.setBackgroundTintList(directionsCard, ColorStateList.valueOf(colorBlue))
            linesPassingTextView.text = getString(R.string.vehicle_fill, data.posUpdate.vehicle)
            linesPassingTextView.gravity = Gravity.CENTER_VERTICAL
            linesBottomTextView.visibility = View.GONE
            arrivalsCard.visibility = View.GONE

            extraBottomTextView.text = getString(R.string.updated_fill,  utils.unixTimestampToLocalTime(data.posUpdate.timestamp))
            extraBottomTextView.visibility = View.VISIBLE
            val update = data.posUpdate
            val vehInfo = VehicleUtils.getTypeForLabel(update.vehicle)
            if(vehInfo == null){
                vehicleIcon.visibility = View.GONE
            } else{
                val ico = when(vehInfo.type){
                    VehicleUtils.VehicleType.BUS -> R.drawable.ic_bus
                    VehicleUtils.VehicleType.ELECTRIC_BUS -> R.drawable.ic_bus_electric_filled
                    VehicleUtils.VehicleType.TRAM -> R.drawable.ic_tram_material
                }
                vehicleIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, ico, activity?.theme))
                vehicleIcon.visibility = View.VISIBLE

                vehicleIcon.setOnClickListener {
                    val print = "${vehInfo.type.getName()}: ${vehInfo.name}"
                    makeToast(print)
                }
            }

        }
        vehShowing = veh
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        updatePositionsIcons(true)
        Log.d(DEBUG_TAG, "Shown vehicle $veh in bottom sheet")
    }

    /**
     * Update the bus positions displayed on the map, from the existing data
     *
     * @param forced If true, forces immediate update ignoring the 100ms throttle
     */
    protected fun updatePositionsIcons(forced: Boolean) {
        // Avoid frequent updates - throttle to max once per 60ms
        val currentTime = System.currentTimeMillis()
        val isStarted = (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        if(forced){
            // if we're running a forced update, cancel the pending one
            jobUpdate?.apply{
                cancel()
                //Log.d(DEBUG_TAG, "Cancelled update")
            }

        }
        else if (currentTime - lastUpdateTime < 100) {
            // Schedule delayed update
            if(viewLifecycleOwnerLiveData.value != null) {
                jobUpdate?.cancel()
                jobUpdate = viewLifecycleOwner.lifecycleScope.launch {
                    delay(100.milliseconds)
                    //Log.d(DEBUG_TAG, "Running update from delayed")
                    updatePositionsIcons(false)
                }
                //Log.d(DEBUG_TAG, "Cancelled previous job, delaying update")
            }
            return
        }
        if(!isStarted){
            Log.w(DEBUG_TAG, "fragment is not started, ")
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
            if (vehShowing?.isNotEmpty() == true && vehShowing == dat.posUpdate.vehicle) {
                selectedBusFeatures.add(newFeature)
                //Log.d(DEBUG_TAG, "Update position for bus $vehShowing")
                //TODO: Recenter the map on the vehicle
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
            else stop.routesThatStopHereToString() //requireContext().getString(R.string.lines_fill, stop.routesThatStopHereToString())
            linesPassingTextView.text = string_show
            linesPassingTextView.visibility = View.VISIBLE
            linesPassingTextView.gravity = Gravity.TOP
            linesBottomTextView.visibility =View.VISIBLE

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
                val colorIcon = ViewUtils.getColorFromTheme(it, R.attr.colorAccent)//ResourcesCompat.getColor(resources,R.attr.colorAccent,activity?.theme)
                ViewCompat.setBackgroundTintList(directionsCard, ColorStateList.valueOf(colorIcon))
            }

            bottomrightImage.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.navigation_right,  activity?.theme))

            vehicleIcon.visibility = View.GONE

        }
        //add stop marker
        if (stop.latitude!=null && stop.longitude!=null) {
            Log.d(DEBUG_TAG, "Showing stop: ${stop.ID}")

            if (showOpenStopWithSymbolLayer()) {
                stopActiveSymbol = symbolManager?.create(
                    SymbolOptions()
                        .withLatLng(LatLng(stop.latitude!!, stop.longitude!!))
                        .withIconImage(STOP_ACTIVE_IMG)
                        .withIconAnchor(ICON_ANCHOR_CENTER)
                )
            } else {
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

        val layerAbove = if (lastMapStyle == MapLibreUtils.STYLE_OSM_RASTER){
           "osm-raster"
        } else// if (lastMapStyle == MapLibreUtils.STYLE_VERSATILES_ECLIPSE_JSON){
            "symbol-transit-airfield"
        /*} else {
            //
            "poi_park"
        }

         */
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

        val selectedBusLayer = SymbolLayer(SEL_BUS_LAYER, SEL_BUS_SOURCE).apply {
            withProperties(
            PropertyFactory.iconImage(BUS_SEL_IMAGE_ID),
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

        style.addLayerAbove(selectedBusLayer, BUSES_LAYER_ID)
        busLayerStarted = true
    }
    /**
     * Method used for enabling / disabling the location from the buttons
     */
    protected fun switchUserLocationStatus(view: View?){
        val enabled = if(locationInitialized) locationComponent.isLocationComponentEnabled else false
        val context = context ?: return
        if(enabled) {
            if(!receivedFirstLocation){
                //use case: the user has decided to disable the location before the first position arrived
                stopInitialPositionRequest()
            }
            // we have to disable it
            setMapLocationEnabled(false)
        }
        else if(deviceHasLocationProvider()) {
            if(Permissions.bothLocationPermissionsGranted(context)){
                if(!locationEnabledOnDevice){
                    warnLocationNotEnabledOnDevice()
                } else{
                    setMapLocationEnabled(true)
                }
            } else{
                Log.d(DEBUG_TAG, "Requesting permissions to show location")
                Permissions.getInstance(context).checkRequestLocationPermissions(requireActivity(), positionRequestResponder)
            }
        } else{
            context.let {
                Toast.makeText(it, R.string.no_gps_on_device, Toast.LENGTH_SHORT).show()
            }
            //adjust ui
            setLocationIconEnabled(false)
        }

    }

    /**
     * Set the map location component enabled
     */
    @SuppressLint("MissingPermission")
    protected fun setMapLocationEnabled(enabled: Boolean){
        Log.d(DEBUG_TAG, "Setting map location enabled: $enabled")
        map?.locationComponent?.isLocationComponentEnabled = enabled
        //map?.cameraPosition =
        mapStateViewModel.locationUserActive.value = enabled
        onMapLocationEnabled(enabled)
    }

    /**
     * Function to run at the first time the fragment is opened
     * Check if we have the permissions, and then initialize the map location component
     * If we don't have it, request the permission
     */
    protected fun checkInitMapLocation(mapReady: MapLibreMap,style: Style, context: Context) {
        //enable location
        val hasGps = deviceHasLocationProvider()
        val permissions = Permissions.getInstance(context)
        if(hasGps) {
            if (Permissions.bothLocationPermissionsGranted(context)) {
                Log.d(DEBUG_TAG, "Have got the location permission, init location component")
                initializeMapLocationComponent(mapReady, context, style)
            }else {
                var req = false
                activity?.let{
                    req = permissions.checkRequestLocationPermissions(it, positionRequestResponder)
                }

                if(!req) {
                    setMapLocationEnabled(false)
                }

            }
        }
    }

    /**
     * Set the UI elements showing that the user location is disabled
     */
    abstract fun onMapLocationEnabled(active: Boolean)

    /**
     * Helper function to actually set the icon
     */
    abstract fun setLocationIconEnabled(enabled: Boolean)

    /**
     * Called when we receive the first fix on the user location
     */
    abstract fun onFirstReceivedLocation(location: Location)

    protected fun isBottomSheetShowing(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    protected fun deviceHasLocationProvider(): Boolean{
        val locManager = requireContext().getSystemService(LOCATION_SERVICE) as LocationManager
        return locManager.allProviders.isNotEmpty()
    }

    /**
     * Update automatically the icon when the live position service changes status
     */
    protected fun observeStatusLivePositions(){
        livePositionsViewModel.serviceStatus.observe(viewLifecycleOwner){ status ->
            //if service is active, update the bus positions icon
            when(status) {
                LivePositionsServiceStatus.OK ->
                    setBusPositionsIcon(true, error = false)

                LivePositionsServiceStatus.NO_POSITIONS -> setBusPositionsIcon(true, error = true)

                else -> setBusPositionsIcon( true, error = true)
            }
        }
    }

    /**
     * Clear all buses from the map
     */
    protected fun clearAllBusPositionsInMap(){
        for ((k, anim) in animatorsByVeh){
            anim.cancel()
        }
        animatorsByVeh.clear()
        updatesByVehDict.clear()
        updatePositionsIcons(forced = false)
    }

    protected fun setCameraPosition(latitude: Double, longitude: Double, zoom: Double) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(latitude, longitude))
            .zoom(zoom)
            .build()
    }

    protected fun showToastLocation(enabled: Boolean){
        val textid = if (enabled) R.string.location_enabled  else R.string.location_disabled
        context?.let{
            Toast.makeText(it,textid,Toast.LENGTH_SHORT).show()
        }
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

        private const val PERM_LOC_COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val PERM_LOC_FINE = Manifest.permission.ACCESS_FINE_LOCATION

        //TODO: this is hardcoded, make it modifiable by the user
        protected const val MAX_DIST_KM = 90.0

    }
}