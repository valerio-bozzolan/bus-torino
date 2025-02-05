package it.reyboz.bustorino.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import com.google.gson.Gson
import com.google.gson.JsonObject
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.map.Styles
import it.reyboz.bustorino.util.ViewUtils
import it.reyboz.bustorino.viewmodels.StopsMapViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MapLibreFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapLibreFragment : Fragment(), OnMapReadyCallback {

    //private var param1: String? = null
    //private var param2: String? = null
    // Declare a variable for MapView
    private lateinit var mapView: MapView
    private lateinit var locationComponent: LocationComponent
    private var lastLocation: Location? = null
    private val stopsViewModel: StopsMapViewModel by viewModels()
    private val gson = Gson()
    private var stopsShowing = ArrayList<Stop>(0)


    protected var map: MapLibreMap? = null
    // Sources for stops and buses
    private lateinit var stopsSource: GeoJsonSource
    private lateinit var busesSource: GeoJsonSource
    private var isStopsLayerStarted = false
    private var lastStopsSizeShown = 0
    private var lastBBox = LatLngBounds.from(2.0, 2.0, 1.0,1.0)
    private lateinit var mapStyle: Style


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

         */
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView =  inflater.inflate(R.layout.fragment_map_libre,
            container, false)



        // Init layout view

        // Init the MapView
        mapView = rootView.findViewById(R.id.libreMapView)
        mapView.getMapAsync(this) //{ //map ->
            //map.setStyle("https://demotiles.maplibre.org/style.json") }

        return rootView
    }

    override fun onMapReady(mapReady: MapLibreMap) {
        this.map = mapReady
        mapReady.cameraPosition = CameraPosition.Builder().target(LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)).zoom(
            15.0).build()
        val mjson = Styles.getJsonStyleFromAsset(requireContext(), "map_style_good_noshops.json")//ViewUtils.loadJsonFromAsset(requireContext(),"map_style_good.json")
        activity?.run {

            mapReady.setStyle(Style.Builder().fromJson(mjson!!)) { style ->
                mapStyle = style
                setupLayers(style)

                // Start observing data
                observeViewModels()
                initLocation(style, mapReady, requireContext())
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
                //makeStyleMapBoxUrl(false))

        }

    }
    @SuppressLint("MissingPermission")
    private fun initLocation(style: Style, map: MapLibreMap, context: Context){
        locationComponent = map.locationComponent
        val locationComponentOptions =
            LocationComponentOptions.builder(context)
                .pulseEnabled(true)
                .build()
        val locationComponentActivationOptions =
            buildLocationComponentActivationOptions(style, locationComponentOptions, context)
        locationComponent.activateLocationComponent(locationComponentActivationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING //CameraMode.TRACKING
        locationComponent.forceLocationUpdate(lastLocation)
    }
    private fun buildLocationComponentActivationOptions(
        style: Style,
        locationComponentOptions: LocationComponentOptions,
        context: Context
    ): LocationComponentActivationOptions {
        return LocationComponentActivationOptions
            .builder(context, style)
            .locationComponentOptions(locationComponentOptions)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(
                LocationEngineRequest.Builder(750)
                    .setFastestInterval(750)
                    .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                    .build()
            )
            .build()
    }

    private fun startLayerStops(style: Style, features:FeatureCollection){

        stopsSource = GeoJsonSource(STOPS_SOURCE_ID,features)
        style.addSource(stopsSource)

        // add icon
        style.addImage(STOP_IMAGE_ID,
            ResourcesCompat.getDrawable(resources,R.drawable.bus_stop, activity?.theme)!!)
        // Stops layer
        val stopsLayer = SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID)
        stopsLayer.withProperties(
                PropertyFactory.iconImage(STOP_IMAGE_ID),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )

        style.addLayerBelow(stopsLayer, "label_country_1")

        isStopsLayerStarted = true
    }

    /**
     * Setup the Map Layers
     */
    private fun setupLayers(style: Style) {
        // Stops source


        // Buses source
        // TODO when adding the buses
        //busesSource = GeoJsonSource(BUSES_SOURCE_ID)
        //style.addSource(busesSource)

        /*
        // TODO when adding the buses
        // Buses layer
        val busesLayer = SymbolLayer(BUSES_LAYER_ID, BUSES_SOURCE_ID).apply {
            withProperties(
                PropertyFactory.iconImage("bus"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconRotate(Expression.get("bearing"))
            )
        }
        style.addLayer(busesLayer)

         */
    }


    /**
     * Incremental updates of the layers
     */
    fun updateLayerIncrementally(newPoints: List<Point>, layerSourceId: String) {
        val source = map?.style?.getSourceAs<GeoJsonSource>(layerSourceId) ?: return

        //source.querySourceFeatures(null)
        // Get existing features
        val existingFeatures = source.querySourceFeatures(null).toMutableList()

        // Add new features
        val newFeatures = newPoints.map { point ->
            Feature.fromGeometry(point)
        }
        existingFeatures.addAll(newFeatures)

        // Update source
        source.setGeoJson(FeatureCollection.fromFeatures(existingFeatures))
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun observeViewModels() {
        // Observe stops
        stopsViewModel.stopsToShow.observe(viewLifecycleOwner) { stops ->
            stopsShowing = stops
            displayStops(stops)
        }
    }

    /**
     * Add the stops to the layers
     */
    private fun displayStops(stops: List<Stop>?) {
        if (stops.isNullOrEmpty()) return

        if (stops.size==lastStopsSizeShown){
            Log.d(DEBUG_TAG, "Not updating, we have the same stop (can only increase!)")
            return
        }

        val features = stops.mapNotNull { stop ->
            stop.latitude?.let { lat ->
                stop.longitude?.let { lon ->
                    Feature.fromGeometry(
                        Point.fromLngLat(lon, lat),
                        JsonObject().apply {
                            addProperty("id", stop.ID)
                            addProperty("name", stop.stopDefaultName)
                            addProperty("routes", stop.routesThatStopHereToString()) // Add routes array to JSON object
                        }
                        )
                }
            }
        }
        Log.d(DEBUG_TAG,"Have put ${features.size} stops to display")

        if (isStopsLayerStarted) {
            stopsSource.setGeoJson(FeatureCollection.fromFeatures(features))
            lastStopsSizeShown = features.size
        } else
            map?.let { startLayerStops(mapStyle, FeatureCollection.fromFeatures(features))
                Log.d(DEBUG_TAG,"Started stops layer on map")
                lastStopsSizeShown = features.size
            }
    }

    companion object {
        private const val STOPS_SOURCE_ID = "stops-source"
        private const val STOPS_LAYER_ID = "stops-layer"
        private const val BUSES_SOURCE_ID = "buses-source"
        private const val BUSES_LAYER_ID = "buses-layer"
        private const val STOP_IMAGE_ID ="bus-stop-icon"
        private const val DEFAULT_CENTER_LAT = 45.0708
        private const val DEFAULT_CENTER_LON = 7.6858
        private const val POSITION_FOUND_ZOOM = 16.5
        private const val NO_POSITION_ZOOM = 17.1
        private const val ACCESS_TOKEN="KxO8lF4U3kiO63m0c7lzqDCDrMUVg1OA2JVzRXxxmYSyjugr1xpe4W4Db5rFNvbQ"
        private const val MAPLIBRE_URL = "https://api.jawg.io/styles/"
        private const val DEBUG_TAG = "BusTO-MapLibreFrag"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MapLibreFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MapLibreFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
        private fun makeStyleUrl(style: String = "jawg-streets") =
            "${MAPLIBRE_URL+ style}.json?access-token=${ACCESS_TOKEN}"
        private fun makeStyleMapBoxUrl(dark: Boolean) =
             if(dark)
                "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
            else //"https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
                "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"

        const val OPENFREEMAP_LIBERY = "https://tiles.openfreemap.org/styles/liberty"

        const val OPENFREEMAP_BRIGHT = "https://tiles.openfreemap.org/styles/bright"

    }
}