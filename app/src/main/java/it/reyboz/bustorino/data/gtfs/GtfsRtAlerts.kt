package it.reyboz.bustorino.data.gtfs

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.google.transit.realtime.GtfsRealtime.Alert.Cause
import com.google.transit.realtime.GtfsRealtime.Alert.Effect
import it.reyboz.bustorino.backend.utils
import java.nio.Buffer
import java.security.MessageDigest


@Entity(tableName = "gtfsrt_alerts")
data class GtfsAlertEntity(
    /** FeedEntity.id dal feed GTFS-RT, unico nel FeedMessage. */
    @PrimaryKey val id: String,

    /** Alert.cause.name, es. "TECHNICAL_PROBLEM", "STRIKE", ... */
    val cause: Cause,

    /** Alert.effect.name, es. "NO_SERVICE", "DETOUR", ... */
    val effect: Effect,

    /** Timestamp (epoch millis) di quando questo alert è stato ricevuto/salvato. */
    val fetchedAt: Long,

    /** True se l'utente ha già visto/letto questo alert. Default false. */
    val userSeen: Boolean = false
)
/**
 * Traduzioni per i campi testuali dell'alert.
 * `field` discrimina tra HEADER, DESCRIPTION e URL (tutti TranslatedString in GTFS-RT).
 */
@Entity(
    tableName = "gtfsrt_alert_translations",
    foreignKeys = [
        ForeignKey(
            entity = GtfsAlertEntity::class,
            parentColumns = ["id"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("alertId")]
)
data class GtfsAlertsTranslation(
    @PrimaryKey val hash: String,
    val alertId: String,

    /** "HEADER" | "DESCRIPTION" | "URL" */
    val field: String,

    /** BCP-47, può mancare nel feed (Translation.language è optional). */
    val language: String?,

    /** Translation.text è required nel .proto, quindi non-null qui. */
    val text: String
) {
    constructor(alertId: String, field: String, language: String?, text: String) : this(
        calcHash(alertId, field, language, text),
        alertId,
        field,
        language,
        text
    )
    companion object {
        const val FIELD_HEADER = "HEADER"
        const val FIELD_DESCRIPTION = "DESCRIPTION"
        const val FIELD_URL = "URL"

        fun calcHash(alertId: String, field: String, language: String?, text: String): String {
            val md = MessageDigest.getInstance("MD5")
            val coS = "$alertId|$field|$language|$text"
            return md.digest(coS.toByteArray()).toHexString()
        }
    }

}

/**
 * Un Alert può avere più TimeRange. Sia `start` che `end` sono optional nel.proto:
 * - start mancante = "da sempre"
 * - end mancante = "fino a tempo indeterminato"
 */
@Entity(
    tableName = "alerts_active_periods",
    foreignKeys = [
        ForeignKey(
            entity = GtfsAlertEntity::class,
            parentColumns = ["id"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("alertId")],
)
data class GtfsAlertsActivePeriods(
    @PrimaryKey val hash: String,
    val alertId: String,

    /** Epoch seconds (POSIX time, come da spec GTFS-RT). Null se non specificato. */
    val start: Long?,
    val end: Long?
){
    constructor(alertId: String, start: Long?, end: Long?) : this(
        calcHash(alertId, start, end),
        alertId, start, end
    )
    companion object{
        fun calcHash(alertId: String, start: Long?, end: Long?): String {
            val input = "${alertId}|${start ?: ""}|${end ?: ""}"
            val md = MessageDigest.getInstance("MD5")
            return md.digest(input.toByteArray()).toHexString()
        }
    }
}
/**
 * Un EntitySelector dal feed. Tutti i campi sono optional nel .proto:
 * almeno uno deve essere valorizzato, ma quale dipende dal feed.
 */
@Entity(
    tableName = "alerts_informed_entities",
    foreignKeys = [
        ForeignKey(
            entity = GtfsAlertEntity::class,
            parentColumns = ["id"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("alertId"),
        Index("routeId"),
        Index("stopId"),
        Index("tripId")
    ]
)
data class GtfsAlertInformedEntity(
    @PrimaryKey val internalId: String,
    val alertId: String,

    val routeId: String?,
    /** route_type GTFS (0=tram, 1=metro, 2=rail, 3=bus, ...). */
    val routeType: Int?,
    val stopId: String?,

    /** Campi dal TripDescriptor annidato, se presente. */
    val tripId: String?,
    val tripRouteId: String?,
    val directionId: Int?
){
    constructor(
        alertId: String, routeId: String?, routeType: Int?, stopId: String?, tripId: String?, tripRouteId: String?, directionId: Int?
    ): this(
        calcHash(alertId, routeId, routeType, stopId, tripId, tripRouteId, directionId),
        alertId,
        routeId,
        routeType,
        stopId,
        tripId,
        tripRouteId,
        directionId
    )
    companion object{
        fun calcHash(alertId: String,routeId: String?, routeType: Int?, stopId: String?, tripId: String?, tripRouteId: String?, directionId: Int?): String {
            val input = "${alertId}|${routeId ?: ""}|${routeType ?: ""}|${stopId ?: ""}|${tripId ?: ""}|${tripRouteId ?: ""}|${directionId ?: ""}"
            val md = MessageDigest.getInstance("MD5")
            return md.digest(input.toByteArray()).toHexString()
        }
    }
}

/**
 * POJO di lettura: un alert con tutti i suoi figli.
 * Usato dai @Query @Transaction nel DAO.
 */
data class AlertWithDetails(
    @Embedded val alert: GtfsAlertEntity,

    @Relation(parentColumn = "id", entityColumn = "alertId")
    val translations: List<GtfsAlertsTranslation>,

    @Relation(parentColumn = "id", entityColumn = "alertId")
    val activePeriods: List<GtfsAlertsActivePeriods>,

    @Relation(parentColumn = "id", entityColumn = "alertId")
    val informedEntities: List<GtfsAlertInformedEntity>
) {
    fun longPrint(): String {
        val sb = StringBuilder()
        sb.append("======== ALERT ${alert.id} ======= \n")
        for (t in translations){
            sb.append(t.field).append("\n")
            sb.append(t.language).append(" : ").append(t.text).append("\n")
        }
        sb.append("-- Cause: ").append(alert.cause.name).append("\n")
        sb.append("-- Active periods:\n")

        for(p in activePeriods){
            if(p.start==null || p.end==null){
                continue
            }
            sb.append("From: ").append(utils.unixTimestampToLocalTime(p.start))
            sb.append(" to: ").append(utils.unixTimestampToLocalTime(p.end)).append("\n")
        }
        val ies = informedEntities
        sb.append("-- Valid for: \n")
        for (i in ies){
            sb.append("Stop ${i.stopId}; Route ${i.routeId}; TripID ${i.tripId}; Trip Route ${i.tripRouteId}\n")
        }
        sb.append("\n")
        return sb.toString()
    }

    fun isActive(unixTimeStamp: Long): Boolean {
        var active = false
        for( ac in activePeriods){
            if(ac.start==null || ac.end == null)
                continue
            if (ac.start <= unixTimeStamp && ac.end >= unixTimeStamp) {
                active = true
                break
            }
        }
        return active
    }


}

