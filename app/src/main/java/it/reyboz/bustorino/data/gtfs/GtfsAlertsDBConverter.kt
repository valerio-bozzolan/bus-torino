package it.reyboz.bustorino.data.gtfs

import com.google.transit.realtime.GtfsRealtime

/**
 * Risultato del mapping di un singolo FeedEntity:
 * tutte le righe pronte per essere passate a [AlertDao.upsertAlert].
 */
data class MappedAlert(
    val alert: GtfsAlertEntity,
    val translations: List<GtfsAlertsTranslation>,
    val activePeriods: List<GtfsAlertsActivePeriods>,
    val informedEntities: List<GtfsAlertInformedEntity>
)

public object GtfsAlertsDBConverter {

    /**
     * Converte un FeedEntity GTFS-RT (che contiene un Alert) nelle entity Room.
     *
     * @param entity il FeedEntity dal feed. Deve avere `hasAlert() == true`.
     * @param fetchedAtMillis epoch millis del momento di ricezione/salvataggio.
     * @return null se il FeedEntity non contiene un alert (es. è un TripUpdate).
     */
    fun fromFeedEntity(
        entity: GtfsRealtime.FeedEntity,
        fetchedAtMillis: Long
    ): MappedAlert {
        if (!entity.hasAlert()) throw IllegalArgumentException("Alert entity can't be null")

        val al = entity.alert
        val alertId = entity.id

        val alert = GtfsAlertEntity(
            id = alertId,
            cause = al.cause,
            effect = al.effect,
            fetchedAt = fetchedAtMillis,
            userSeen = false
        )

        val translations = buildList {
            // Header
            if (al.hasHeaderText()) {
                al.headerText.translationList.forEach { t ->
                    add(
                        GtfsAlertsTranslation(
                            alertId = alertId,
                            field = GtfsAlertsTranslation.FIELD_HEADER,
                            language = if (t.hasLanguage()) t.language else null,
                            text = t.text
                        )
                    )
                }
            }
            // Description
            if (al.hasDescriptionText()) {
                al.descriptionText.translationList.forEach { t ->
                    add(
                        GtfsAlertsTranslation(
                            alertId = alertId,
                            field = GtfsAlertsTranslation.FIELD_DESCRIPTION,
                            language = if (t.hasLanguage()) t.language else null,
                            text = t.text
                        )
                    )
                }
            }
            // URL (anche lui TranslatedString in GTFS-RT)
            if (al.hasUrl()) {
                al.url.translationList.forEach { t ->
                    add(
                        GtfsAlertsTranslation(
                            alertId = alertId,
                            field = GtfsAlertsTranslation.FIELD_URL,
                            language = if (t.hasLanguage()) t.language else null,
                            text = t.text
                        )
                    )
                }
            }
        }

        val activePeriods = al.activePeriodList.map { tr ->
            GtfsAlertsActivePeriods(
                alertId = alertId,
                start = if (tr.hasStart()) tr.start else null,
                end = if (tr.hasEnd()) tr.end else null
            )
        }

        val informedEntities = al.informedEntityList.map { e ->


            val (tripId, tripRouteId, directionId) = if (e.hasTrip()) {
                val td = e.trip
                Triple(
                    if (td.hasTripId()) "gtt:${td.tripId}" else null,
                    if (td.hasRouteId()) "gtt:${td.routeId}" else null,
                    if (td.hasDirectionId()) td.directionId else null
                )
            } else {
                Triple(null, null, null)
            }

            GtfsAlertInformedEntity(
                alertId = alertId,
                //agencyId = if (e.hasAgencyId()) e.agencyId else null,
                routeId = if (e.hasRouteId()) "gtt:${e.routeId}" else null,
                routeType = if (e.hasRouteType()) e.routeType else null,
                stopId = if (e.hasStopId()) "gtt:${e.stopId}" else null,
                tripId = tripId,
                tripRouteId = tripRouteId,
                directionId = directionId
            )
        }

        return MappedAlert(alert, translations, activePeriods, informedEntities)
    }

    /**
     * Comodità: prende un intero FeedMessage e mappa solo i FeedEntity che sono alert,
     * ignorando TripUpdate e VehiclePosition.
     */
    fun fromFeedMessage(
        feed: GtfsRealtime.FeedMessage,
        fetchedAtMillis: Long = System.currentTimeMillis()
    ): List<MappedAlert> {
        return feed.entityList.mapNotNull { fe ->
            // Salta gli entity marcati come deleted
            if (fe.isDeleted || !fe.hasAlert()) null
            else fromFeedEntity(fe, fetchedAtMillis)
        }
    }
}