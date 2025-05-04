package it.reyboz.bustorino.map

import android.animation.ObjectAnimator
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.data.gtfs.MatoPattern
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class BusPositionUtils {
    companion object{
        @JvmStatic
        public fun updateBusPositionMarker(map: MapView, marker: Marker?, posUpdate: LivePositionUpdate,
                                           tripMarkersAnimators: HashMap<String, ObjectAnimator>,
                                           justCreated: Boolean) {
            val position: GeoPoint
            val updateID = posUpdate.tripID
            if (!justCreated) {
                position = marker!!.position
                if (posUpdate.latitude != position.latitude || posUpdate.longitude != position.longitude) {
                    val newpos = GeoPoint(posUpdate.latitude, posUpdate.longitude)
                    val valueAnimator = MarkerUtils.makeMarkerAnimator(
                        map, marker, newpos, MarkerUtils.LINEAR_ANIMATION, 1200
                    )
                    valueAnimator.setAutoCancel(true)
                    tripMarkersAnimators.put(updateID, valueAnimator)
                    valueAnimator.start()
                }
                //marker.setPosition(new GeoPoint(posUpdate.getLatitude(), posUpdate.getLongitude()));
            } else {
                position = GeoPoint(posUpdate.latitude, posUpdate.longitude)
                marker!!.position = position
            }
            //if (posUpdate.bearing != null) marker.rotation = posUpdate.bearing * -1f
            marker.rotation = posUpdate.bearing?.let { it*-1f } ?: 0.0f
        }
    }
}