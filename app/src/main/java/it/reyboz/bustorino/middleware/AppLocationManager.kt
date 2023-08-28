/*
	BusTO (middleware)
    Copyright (C) 2019 Fabio Mazza

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
package it.reyboz.bustorino.middleware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import it.reyboz.bustorino.util.LocationCriteria
import it.reyboz.bustorino.util.Permissions
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * Singleton class used to access location. Possibly extended with other location sources.
 */
class AppLocationManager private constructor(context: Context) : LocationListener {
    private val appContext: Context
    private val locMan: LocationManager
    private val BUNDLE_LOCATION = "location"
    private var oldGPSLocStatus = LOCATION_UNAVAILABLE
    private var minimum_time_milli = -1
    private val requestersRef = ArrayList<WeakReference<LocationRequester?>>()

    init {
        appContext = context.applicationContext
        locMan = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Throws(SecurityException::class)
    private fun requestGPSPositionUpdates(): Boolean {
        val timeinterval =
            if (minimum_time_milli > 0 && minimum_time_milli < Int.MAX_VALUE) minimum_time_milli else 2000
        locMan.removeUpdates(this)
        if (!checkLocationPermission(appContext)){
            Log.e(DEBUG_TAG, "No location permission!!")
            return false
        }
        if (locMan.allProviders.contains("gps")) locMan.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            timeinterval.toLong(),
            5f,
            this
        )
        /*LocationManagerCompat.requestLocationUpdates(locMan, LocationManager.GPS_PROVIDER,
                    new LocationRequestCompat.Builder(timeinterval).setMinUpdateDistanceMeters(5.F).build(),this, );
                    TODO: find a way to do this
             */
        return true
    }

    private fun cleanAndUpdateRequesters() {
        minimum_time_milli = Int.MAX_VALUE
        val iter = requestersRef.listIterator()
        while (iter.hasNext()) {
            val cReq = iter.next().get()
            if (cReq == null) iter.remove() else {
                minimum_time_milli = min(cReq.locationCriteria.timeInterval.toDouble(), minimum_time_milli.toDouble())
                    .toInt()
            }
        }
        Log.d(
            DEBUG_TAG,
            "Updated requesters, got " + requestersRef.size + " listeners to update every " + minimum_time_milli + " ms at least"
        )
    }

    fun addLocationRequestFor(req: LocationRequester) {
        var present = false
        minimum_time_milli = Int.MAX_VALUE
        var countNull = 0
        val iter = requestersRef.listIterator()
        while (iter.hasNext()) {
            val cReq = iter.next().get()
            if (cReq == null) {
                countNull++
                iter.remove()
            } else if (cReq == req) {
                present = true
                minimum_time_milli = min(cReq.locationCriteria.timeInterval.toDouble(), minimum_time_milli.toDouble())
                    .toInt()
            }
        }
        Log.d(DEBUG_TAG, "$countNull listeners have been removed because null")
        if (!present) {
            val newref = WeakReference(req)
            requestersRef.add(newref)
            minimum_time_milli = min(req.locationCriteria.timeInterval.toDouble(), minimum_time_milli.toDouble())
                .toInt()
            Log.d(DEBUG_TAG, "Added new stop requester, instance of " + req.javaClass.simpleName)
        }
        if (requestersRef.size > 0) {
            Log.d(DEBUG_TAG, "Requesting location updates")
            requestGPSPositionUpdates()
        }
    }

    fun removeLocationRequestFor(req: LocationRequester) {
        minimum_time_milli = Int.MAX_VALUE
        val iter = requestersRef.listIterator()
        while (iter.hasNext()) {
            val cReq = iter.next().get()
            if (cReq == null || cReq == req) iter.remove() else {
                minimum_time_milli = min(cReq.locationCriteria.timeInterval.toDouble(), minimum_time_milli.toDouble())
                    .toInt()
            }
        }
        if (requestersRef.size <= 0) {
            locMan.removeUpdates(this)
        }
    }

    private fun sendLocationStatusToAll(status: Int) {
        val iter = requestersRef.listIterator()
        while (iter.hasNext()) {
            val cReq = iter.next().get()
            if (cReq == null) iter.remove() else cReq.onLocationStatusChanged(status)
        }
    }

    fun isRequesterRegistered(requester: LocationRequester): Boolean {
        for (regRef in requestersRef) {
            if (regRef.get() != null && regRef.get() === requester) return true
        }
        return false
    }

    override fun onLocationChanged(location: Location) {
        Log.d(
            DEBUG_TAG, "found location: \nlat: ${location.latitude} lon: ${location.longitude} accuracy: ${location.accuracy}"
        )
        val iter = requestersRef.listIterator()
        var new_min_interval = Int.MAX_VALUE
        while (iter.hasNext()) {
            val requester = iter.next().get()
            if (requester == null) iter.remove() else {
                val timeNow = System.currentTimeMillis()
                val criteria = requester.locationCriteria
                if (location.accuracy < criteria.minAccuracy &&
                    timeNow - requester.lastUpdateTimeMillis > criteria.timeInterval
                ) {
                    requester.onLocationChanged(location)
                    Log.d(
                        "AppLocationManager",
                        "Updating position for instance of requester " + requester.javaClass.simpleName
                    )
                }
                //update minimum time interval
                new_min_interval = min(requester.locationCriteria.timeInterval.toDouble(), new_min_interval.toDouble())
                    .toInt()
            }
        }
        minimum_time_milli = new_min_interval
        if (requestersRef.size == 0) {
            //stop requesting the position
            locMan.removeUpdates(this)
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        //IF ANOTHER LOCATION SOURCE IS READY, USE IT
        //OTHERWISE, SIGNAL THAT WE HAVE NO LOCATION
        if (oldGPSLocStatus != status) {
            if (status == LocationProvider.OUT_OF_SERVICE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                sendLocationStatusToAll(LOCATION_UNAVAILABLE)
            } else if (status == LocationProvider.AVAILABLE) {
                sendLocationStatusToAll(LOCATION_GPS_AVAILABLE)
            }
            oldGPSLocStatus = status
        }
        Log.d(DEBUG_TAG, "Provider status changed: $provider status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        cleanAndUpdateRequesters()
        requestGPSPositionUpdates()
        Log.d(DEBUG_TAG, "Provider: $provider enabled")
        for (req in requestersRef) {
            if (req.get() == null) continue
            req.get()!!.onLocationProviderAvailable()
        }
    }

    override fun onProviderDisabled(provider: String) {
        cleanAndUpdateRequesters()
        for (req in requestersRef) {
            if (req.get() == null) continue
            req.get()!!.onLocationDisabled()
        }
        //locMan.removeUpdates(this);
        Log.d(DEBUG_TAG, "Provider: $provider disabled")
    }

    fun anyLocationProviderMatchesCriteria(cr: Criteria?): Boolean {
        return Permissions.anyLocationProviderMatchesCriteria(locMan, cr, true)
    }

    /**
     * Interface to be implemented to get the location request
     */
    interface LocationRequester {
        /**
         * Do something with the newly obtained location
         * @param loc the obtained location
         */
        fun onLocationChanged(loc: Location?)

        /**
         * Inform the requester that the GPS status has changed
         * @param status new status
         */
        fun onLocationStatusChanged(status: Int)

        /**
         * We have a location provider available
         */
        fun onLocationProviderAvailable()

        /**
         * Called when location is disabled
         */
        fun onLocationDisabled()

        /**
         * Give the last time of update the requester has
         * Set it to -1 in order to receive each new location
         * @return the time for update in milliseconds since epoch
         */
        val lastUpdateTimeMillis: Long

        /**
         * Get the specifications for the location
         * @return fully parsed LocationCriteria
         */
        val locationCriteria: LocationCriteria
    }

    companion object {
        const val LOCATION_GPS_AVAILABLE = 22
        const val LOCATION_UNAVAILABLE = -22
        private const val DEBUG_TAG = "BUSTO LocAdapter"
        private var instance: AppLocationManager? = null
        @JvmStatic
        fun getInstance(con: Context): AppLocationManager? {
            if (instance == null) instance = AppLocationManager(con)
            return instance
        }

        fun checkLocationPermission(context: Context?): Boolean {
            return ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
