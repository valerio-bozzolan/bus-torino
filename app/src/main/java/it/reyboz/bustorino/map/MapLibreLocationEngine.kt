package it.reyboz.bustorino.map

import android.app.PendingIntent
import android.os.Looper
import android.util.Log
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult

import it.reyboz.bustorino.middleware.FusedNativeLocationProvider

/**
 * Adattatore che implementa l'interfaccia [LocationEngine] di MapLibre
 * delegando a [FusedNativeLocationProvider].
 *
 * Separa completamente la logica di fusione dei provider (in [FusedNativeLocationProvider])
 * dalla traduzione nel contratto MapLibre (qui).
 *
 */
class MapLibreLocationEngine(
    private val provider: FusedNativeLocationProvider,
) : LocationEngine {

    // Mappa callback MapLibre → listener del provider, per poterli rimuovere
    private val callbackListeners =
        HashMap<LocationEngineCallback<LocationEngineResult>, FusedNativeLocationProvider.LocationUpdateListener>()


    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        val location = provider.getLastLocationFromProviders()
        if (location != null) {
            callback.onSuccess(LocationEngineResult.create(location))
        } else {
            callback.onFailure(NoLocationException())
        }
    }

    // -------------------------------------------------------------------------
    // requestLocationUpdates — overload con Looper (quello usato da MapLibre)
    // -------------------------------------------------------------------------

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        val providerListener = FusedNativeLocationProvider.LocationUpdateListener { location ->
            callback.onSuccess(LocationEngineResult.create(location))
        }

        callbackListeners[callback] = providerListener
        provider.addListener(providerListener)

        // Avvia (o riavvia) il provider con i parametri della request MapLibre.
        // Se il provider è già attivo con altri listener, stop+start lo ri-configura.
        provider.startUpdates(
            FusedNativeLocationProvider.Options(
                minIntervalMs    = request.interval,
                minDisplacementM = request.displacement,
                looper           = looper,
            )
        )
    }

    // -------------------------------------------------------------------------
    // requestLocationUpdates — overload con PendingIntent (background/geofencing)
    // -------------------------------------------------------------------------

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent,
    ) {
        // PendingIntent is used for background updates via BroadcastReceiver.
        // FusedNativeLocationProvider operates in the foreground: delegating to PendingIntent
        // would require a different architecture (LocationManager.requestLocationUpdates
        // with native PendingIntent). Not supported in this implementation.
        throw UnsupportedOperationException(
            "MapLibreLocationEngine does not support updates via PendingIntent. " +
                    "Use requestLocationUpdates(request, callback, looper) or " +
                    "implement a dedicated BroadcastReceiver."
        )
    }

    // -------------------------------------------------------------------------
    // removeLocationUpdates — overload con callback
    // -------------------------------------------------------------------------

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        callbackListeners.remove(callback)?.let { providerListener ->
            provider.removeListener(providerListener)
        }
        Log.d(DEBUG_TAG, "Removed location updates callback $callback")
    }

    // -------------------------------------------------------------------------
    // removeLocationUpdates — overload con PendingIntent
    // -------------------------------------------------------------------------

    override fun removeLocationUpdates(pendingIntent: PendingIntent) {
        throw UnsupportedOperationException(
            "MapLibreLocationEngine does not support PendingIntent removal."
        )
    }
    class NoLocationException : Exception()


    companion object {
        const val DEBUG_TAG = "BusTO-MapLocationEngine"

    }
}
