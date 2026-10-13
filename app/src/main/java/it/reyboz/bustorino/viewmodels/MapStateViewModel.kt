package it.reyboz.bustorino.viewmodels

import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import it.reyboz.bustorino.map.MapCameraState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.util.concurrent.atomic.AtomicBoolean

class MapStateViewModel : ViewModel() {

    var savedCameraState: MapCameraState? = null
        private set

    val lastOpenStopID = MutableLiveData<String>()

    fun saveMapState(map: MapLibreMap){
        val cp = map.cameraPosition
        val newBbox = map.projection.visibleRegion.latLngBounds

        val cameraState = MapCameraState(
            latitude = cp.target?.latitude ?: newBbox.center.latitude,
            longitude = cp.target?.longitude ?: newBbox.center.longitude,
            zoom = cp.zoom,
            bearing = cp.bearing,
            tilt = cp.tilt
        )
        Log.d(DEBUG_TAG, "Saved camera state: $cameraState")

        savedCameraState = cameraState
    }
    fun restoreMapState(map: MapLibreMap): Boolean {
        return restoreMapState(map, this.savedCameraState)
    }

    var userLocationToShow: Location? = null
    var mapInitOnce = AtomicBoolean(false)

    val locationUserActive = MutableLiveData(false)
    //val locationUserAvailable = MutableLiveData(false)
    val followingUserPosition = MutableLiveData(false)
    val locationDeviceEnabled = MutableLiveData(false)

    fun setLocationNotAvailable(){
        //locationUserAvailable.value = false
        locationUserActive.value = false
    }

    companion object{
        fun restoreMapState(map: MapLibreMap, savedCameraState: MapCameraState?): Boolean {
            val state = savedCameraState ?: return false
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(state.latitude, state.longitude))
                .zoom(state.zoom)
                .bearing(state.bearing)
                .tilt(state.tilt)
                .build()
            Log.d(DEBUG_TAG, "Restored camera state: $state")
            return true
        }

        const val DEBUG_TAG ="BusTO-MapState"
    }
}