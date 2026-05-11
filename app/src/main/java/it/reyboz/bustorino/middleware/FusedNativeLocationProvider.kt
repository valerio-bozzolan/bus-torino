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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

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

        fun onFusedStatusChanged(isEnabled: Boolean) {}
    }

    fun interface LocationStatusListener {
        fun onLocationStatusChanged(isEnabled: Boolean)
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
        val minIntervalMs: Long = 1000L,
        val minDisplacementM: Float = 5f,
        val looper: Looper? = null,
        val useGps: Boolean = true,
        val useNetwork: Boolean = true,
        val usePassive: Boolean = true,
    ){
        constructor(minIntervalMs: Long, minDisplacementM: Float) : this(
            minIntervalMs = minIntervalMs,
            minDisplacementM = minDisplacementM,
            useGps = true
        )
    }


    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // List of registered listeners (called on the configured looper)
    private val listeners = CopyOnWriteArraySet<LocationUpdateListener>()
    private val statusListeners = CopyOnWriteArraySet<LocationStatusListener>()

    // Active Android listeners, one per provider
    private val activeAndroidListeners = mutableListOf<LocationListener>()

    @Volatile
    private var bestLocation: Location? = null

    private var running = AtomicBoolean(false)

    private var runningOptions = Options(500L, 5f, null, true, true, true)

    private val availableProviders = ArrayList<String>()

    private var lastStatusUpdateEnabled = false

    private val providersAreEnabled = ConcurrentHashMap<String, Boolean>()

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

    fun addListener(listener: LocationStatusListener) {
        synchronized(listeners) {
            statusListeners.add(listener)
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

    fun removeListener(listener: LocationStatusListener) {
        synchronized(listeners) {
            statusListeners.remove(listener)
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
    fun startUpdates(options: Options?) {
        val wasNotRunning = running.compareAndSet(false, true)

        if (!wasNotRunning) {
            //it's already running, no need to stop
            Log.d(DEBUG_TAG, "Requested to start updates, but provider is running")
            if(options!=null){
                if(runningOptions !== options){
                    Log.d(DEBUG_TAG, "Stopping and restarting")
                    //need to restart
                    stopUpdatesInternal()
                    startUpdates(options)
                }
            }
            return
        }
        if (options!=null){
            runningOptions = options
        }
        lastStatusUpdateEnabled = false
        val selectedProviders = buildList {
            if (runningOptions.useGps)     add(LocationManager.GPS_PROVIDER)
            if (runningOptions.useNetwork) add(LocationManager.NETWORK_PROVIDER)
            if (runningOptions.usePassive) add(LocationManager.PASSIVE_PROVIDER)
        }

        val effectiveLooper = runningOptions.looper ?: Looper.getMainLooper()

        selectedProviders.forEach { provider ->
            val isEnabled = locationManager.isProviderEnabled(provider)
            if(isEnabled) {
                lastStatusUpdateEnabled = true
            }
            providersAreEnabled[provider] = isEnabled
            //listen for location, even if the provider is not started yet
            val locListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onReceiveLocation(location)
                }

                override fun onProviderDisabled(provider: String) {
                    super.onProviderDisabled(provider)
                    onProviderStatusChanged(provider, false)
                }

                override fun onProviderEnabled(provider: String) {
                    super.onProviderEnabled(provider)
                    onProviderStatusChanged(provider, true)
                }
            }

            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    runningOptions.minIntervalMs,
                    runningOptions.minDisplacementM,
                    locListener,
                    effectiveLooper,
                )
                activeAndroidListeners.add(locListener)
                availableProviders.add(provider)
            }
        }
        notifyListenerStatus(lastStatusUpdateEnabled)
        Log.d(DEBUG_TAG, "Started location updates, running: ${running.get()}, with providers: $availableProviders")
    }

    /**
     * Stops all updates and releases the Android listeners.
     * [LocationUpdateListener]s registered via [addListener] are retained:
     * calling [startUpdates] again will resume delivering updates to them.
     */
    private fun stopUpdatesInternal() {
        if(running.compareAndSet(true, false)) {
            //we have to stop updates
            Log.d(DEBUG_TAG, "Actually stopping location updates, active providers: $availableProviders")
            activeAndroidListeners.forEach { listener ->
                runCatching { locationManager.removeUpdates(listener) }
            }
            activeAndroidListeners.clear()
            //running = false is set by compareAndSet
            availableProviders.clear()
        }
    }

    fun isRunning(): Boolean = running.get()

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
    private fun notifyListenerStatus(enabled: Boolean){
        Log.d(DEBUG_TAG, "Notifying listeners, the position is enabled: $enabled")
        listeners.forEach { it.onFusedStatusChanged(enabled) }
        statusListeners.forEach { it.onLocationStatusChanged(enabled) }
    }

    private fun onReceiveLocation(location: Location) {
        if (isBetterLocation(location, bestLocation)) {
            bestLocation = location
            //Log.d(DEBUG_TAG, "New best location: $bestLocation")
            notifyListeners(location)
        }
    }

    private fun onProviderStatusChanged(provider: String,enabled: Boolean) {
        providersAreEnabled.put(provider, enabled)
        val actu = providersAreEnabled.reduceValues(1, Boolean::or)
        if (actu!=null && actu!=lastStatusUpdateEnabled){
            lastStatusUpdateEnabled = actu
            notifyListenerStatus(actu)
        }

    }

    fun isLocationEnabled(): Boolean {
        val probValue = providersAreEnabled.reduceValues(1, Boolean::or)
        return probValue ?: true
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