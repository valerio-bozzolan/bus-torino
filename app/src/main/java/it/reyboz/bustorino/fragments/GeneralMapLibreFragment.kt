package it.reyboz.bustorino.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.PreferencesHolder
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

abstract class GeneralMapLibreFragment: ScreenBaseFragment(), OnMapReadyCallback {
    protected var map: MapLibreMap? = null
    protected var shownStopInBottomSheet : Stop? = null
    protected var savedMapStateOnPause : Bundle? = null

    // Declare a variable for MapView
    protected lateinit var mapView: MapView
    protected lateinit var mapStyle: Style
    protected lateinit var stopsSource: GeoJsonSource
    protected lateinit var busesSource: GeoJsonSource
    protected lateinit var selectedStopSource: GeoJsonSource

    protected lateinit var sharedPreferences: SharedPreferences

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

    private var lastMapStyle =""

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

    override fun onResume() {
        super.onResume()
        val newMapStyle = PreferencesHolder.getMapLibreStyleFile(requireContext())
        Log.d(DEBUG_TAG, "onResume newMapStyle: $newMapStyle, lastMapStyle: $lastMapStyle")
        if(newMapStyle!=lastMapStyle){
            reloadMap()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
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

    abstract fun openStopInBottomSheet(stop: Stop)

    //For extra stuff to do when the map is destroyed
    abstract fun onMapDestroy()

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

    companion object{
        private const val DEBUG_TAG="GeneralMapLibreFragment"

        const val BUSES_SOURCE_ID = "buses-source"
        const val BUSES_LAYER_ID = "buses-layer"

        const val SEL_STOP_SOURCE="selected-stop-source"
        const val SEL_STOP_LAYER = "selected-stop-layer"

        const val KEY_LOCATION_ENABLED="location_enabled"
    }
}