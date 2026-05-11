package it.reyboz.bustorino.viewmodels

import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import it.reyboz.bustorino.map.MapCameraState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapStateViewModel : ViewModel() {

    var savedCameraState: MapCameraState? = null
        private set

    val lastOpenStopID = MutableLiveData<String>()

    fun saveMapState(map: MapLibreMap){
        val cp = map.cameraPosition
        val newBbox = map.projection.visibleRegion.latLngBounds

        val cameraState = MapCameraState(
            latitude = newBbox.center.latitude,
            longitude = newBbox.center.longitude,
            zoom = cp.zoom,
            bearing = cp.bearing,
            tilt = cp.tilt
        )

        savedCameraState = cameraState
    }
    fun restoreMapState(map: MapLibreMap): Boolean {
        return restoreMapState(map, this.savedCameraState)
    }

    var locationToShow: Location? = null

    val locationUserActive = MutableLiveData(false)
    val followingUserPosition = MutableLiveData(false)
    val locationDeviceEnabled = MutableLiveData(false)

    companion object{
        fun restoreMapState(map: MapLibreMap, savedCameraState: MapCameraState?): Boolean {
            val state = savedCameraState ?: return false
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(state.latitude, state.longitude))
                .zoom(state.zoom)
                .bearing(state.bearing)
                .tilt(state.tilt)
                .build()
            return true
        }
    }
}