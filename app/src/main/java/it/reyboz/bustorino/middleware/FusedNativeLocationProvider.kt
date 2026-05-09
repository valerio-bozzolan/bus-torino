package it.reyboz.bustorino.middleware

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.util.Permissions
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Native Android location provider that fuses GPS_PROVIDER, NETWORK_PROVIDER
 * and PASSIVE_PROVIDER with no dependency on Google Play Services.
 *
 * Standalone class to be used anywhere in the app
 *
 */
class FusedNativeLocationProvider(context: Context) {

    // -------------------------------------------------------------------------
    // Public interface for location update consumers
    // -------------------------------------------------------------------------

    fun interface LocationUpdateListener {
        fun onLocationUpdate(location: Location)
    }

    /**
     * Configuration for location updates.
     *
     * @param minIntervalMs     Minimum interval between updates in ms.
     * @param minDisplacementM  Minimum displacement in meters to trigger an update.
     * @param looper            Thread on which to receive callbacks. Null = main thread.
     * @param useGps            Enables GPS_PROVIDER.
     * @param useNetwork        Enables NETWORK_PROVIDER (WiFi + cell).
     * @param usePassive        Enables PASSIVE_PROVIDER (zero consumption, opportunistic updates).
     */
    data class Options(
        val minIntervalMs: Long = 500L,
        val minDisplacementM: Float = 5f,
        val looper: Looper? = null,
        val useGps: Boolean = true,
        val useNetwork: Boolean = true,
        val usePassive: Boolean = true,
    )


    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // List of registered listeners (called on the configured looper)
    private val listeners = CopyOnWriteArraySet<LocationUpdateListener>()

    // Active Android listeners, one per provider
    private val activeAndroidListeners = mutableListOf<LocationListener>()

    @Volatile
    private var bestLocation: Location? = null

    @Volatile
    private var running = false

    private var runningOptions = Options(500L, 5f, null, true, true, true)

    private val activeProviders = ArrayList<String>()

    private var havePermissions = false

    //private val removedListener = mutableSetOf<LocationUpdateListener>()

    private val handler by lazy { Handler(runningOptions.looper ?: Looper.getMainLooper()) }

    //private var appContext = context.applicationContext

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Adds a listener. Can be called before or after [startUpdates].
     */
    fun addListener(listener: LocationUpdateListener) {
        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "Adding listener $listener")
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a previously registered listener.
     */
    fun removeListener(listener: LocationUpdateListener) {
        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "Removing listener $listener")
        synchronized(listeners){
            if(listeners.remove(listener)){
                if(listeners.isEmpty()) stopUpdates()
            }
            if(BuildConfig.DEBUG)
                Log.d(DEBUG_TAG, "Listener now size: ${listeners.size}")
        }
    }

    /**
     * Starts receiving location updates from the enabled providers.
     * If already running, stops the existing providers first and restarts
     * them with the new configuration.
     *
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
     */
    @SuppressLint("MissingPermission")
    fun startUpdates(options: Options?): Boolean {
        if (running) stopUpdates()
        if (options!=null){
            runningOptions = options
        }
        val selectedProviders = buildList {
            if (runningOptions.useGps)     add(LocationManager.GPS_PROVIDER)
            if (runningOptions.useNetwork) add(LocationManager.NETWORK_PROVIDER)
            if (runningOptions.usePassive) add(LocationManager.PASSIVE_PROVIDER)
        }

        val effectiveLooper = runningOptions.looper ?: Looper.getMainLooper()

        selectedProviders.forEach { provider ->
            if (!locationManager.isProviderEnabled(provider)) return@forEach

            val locListener = LocationListener { location ->
                if (isBetterLocation(location, bestLocation)) {
                    bestLocation = location
                    //Log.d(DEBUG_TAG, "New best location: $bestLocation")
                    notifyListeners(location)
                }
            }

            //runCatching {
            locationManager.requestLocationUpdates(
                provider,
                runningOptions.minIntervalMs,
                runningOptions.minDisplacementM,
                locListener,
                effectiveLooper,
            )
            activeAndroidListeners.add(locListener)
            activeProviders.add(provider)
            //}
        }

        running = activeAndroidListeners.isNotEmpty()
        Log.d(DEBUG_TAG, "Started location updates, running: $running, with providers: $activeProviders")
        return running
    }

    /**
     * Stops all updates and releases the Android listeners.
     * [LocationUpdateListener]s registered via [addListener] are retained:
     * calling [startUpdates] again will resume delivering updates to them.
     */
    private fun stopUpdatesInternal() {
        if(!running) //we have already done this
            return
        Log.d(DEBUG_TAG, "Actually stopping location updates, active providers: $activeProviders")
        activeAndroidListeners.forEach { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeAndroidListeners.clear()
        running = false
        activeProviders.clear()
    }

    /**
     * Returns the best known location cached by the enabled providers,
     * without starting continuous updates.
     *
     * May return null if no provider has ever acquired a fix
     * (e.g. first launch, device just turned on).
     *
     * Do not use if we do not have the Location permission
     */
    @SuppressLint("MissingPermission")
    fun getLastLocationFromProviders(): Location? {
        val candidatesLocations = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
             locationManager.getLastKnownLocation(provider)
        }

        // Among the candidates, the most accurate wins. On equal accuracy,
        // the most recent wins.
        return candidatesLocations.minWithOrNull(
            compareBy({ it.accuracy }, { -it.time })
        )
    }

    //fun getLastReceivedBestLocation(): Location? {
    //    return bestLocation
    //}


    private fun notifyListeners(location: Location) {
        //synchronized(listeners) {
            listeners.forEach { it.onLocationUpdate(location) }
    }

    /**
     * Public call for stopping the updates
     */
    fun stopUpdates() {
        Log.d(DEBUG_TAG, "Stopping updates")
        if (Looper.myLooper() == handler.looper) {
            stopUpdatesInternal()
        } else {
            handler.post { stopUpdatesInternal() }
        }
    }



    companion object {
        private const val TIME_DELAY = 2 * 60 * 1_000L  // two minutes
        private const val ACCURACY_DEGRADATION_THRESHOLD_M = 200f
        private const val DEBUG_TAG = "BusTO-FusedLocationProv"

        /**
         * Determines whether [candidate] is a better location than [current].
         *
         * Criteria, in priority order:
         * 1. If the candidate is newer than [TIME_DELAY], always accept it.
         * 2. If it is significantly older, reject it.
         * 3. Equal freshness: the one with lower accuracy (tighter radius) wins.
         * 4. Same provider, same freshness delta, and not degrading too much: accept.
         */
        @JvmStatic
        fun isBetterLocation(candidate: Location, current: Location?): Boolean {
            if (current == null) return true

            val timeDeltaMs = candidate.time - current.time

            return when {
                timeDeltaMs > TIME_DELAY  -> true   // much more recent: accept immediately
                timeDeltaMs < -TIME_DELAY -> false  // much older: reject immediately
                else -> {
                    val accuracyDeltaM = candidate.accuracy - current.accuracy
                    when {
                        accuracyDeltaM < 0 -> true   // more accurate
                        accuracyDeltaM == 0f && timeDeltaMs > 0 -> true   // same accuracy, fresher
                        timeDeltaMs > 0
                                && accuracyDeltaM <= ACCURACY_DEGRADATION_THRESHOLD_M
                                && candidate.provider == current.provider -> true
                        else -> false
                    }
                }
            }
        }
    }
}