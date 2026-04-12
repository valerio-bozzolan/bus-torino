package it.reyboz.bustorino.map

import android.os.Bundle


data class MapCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
){
    fun toBundle(): Bundle = Bundle().apply {
        putDouble(KEY_LATITUDE, latitude)
        putDouble(KEY_LONGITUDE, longitude)
        putDouble(KEY_ZOOM, zoom)
        putDouble(KEY_BEARING, bearing)
        putDouble(KEY_TILT, tilt)
    }

    companion object {
        private const val KEY_LATITUDE = "cam-latitude"
        private const val KEY_LONGITUDE = "cam-longitude"
        private const val KEY_ZOOM = "cam-zoom"
        private const val KEY_BEARING = "cam-bearing"
        private const val KEY_TILT = "cam-tilt"

        fun fromBundle(bundle: Bundle): MapCameraState = MapCameraState(
            latitude = bundle.getDouble(KEY_LATITUDE),
            longitude = bundle.getDouble(KEY_LONGITUDE),
            zoom = bundle.getDouble(KEY_ZOOM),
            bearing = bundle.getDouble(KEY_BEARING),
            tilt = bundle.getDouble(KEY_TILT)
        )

        fun checkInBundle(bundle: Bundle): Boolean {
           val chck =  bundle.containsKey(KEY_LATITUDE) && bundle.containsKey(KEY_LONGITUDE) && bundle.containsKey(KEY_ZOOM)
            return chck
        }
    }
}