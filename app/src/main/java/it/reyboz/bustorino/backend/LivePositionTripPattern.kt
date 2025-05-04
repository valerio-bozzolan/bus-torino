package it.reyboz.bustorino.backend

import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.data.gtfs.MatoPattern

data class LivePositionTripPattern(
    var posUpdate: LivePositionUpdate,
    var pattern: MatoPattern?
)
