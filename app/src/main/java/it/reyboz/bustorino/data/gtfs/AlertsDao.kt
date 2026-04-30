package it.reyboz.bustorino.data.gtfs

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AlertsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: GtfsAlertEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlerts(alerts: List<GtfsAlertEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslations(items: List<GtfsAlertsTranslation>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivePeriods(items: List<GtfsAlertsActivePeriods>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInformedEntities(items: List<GtfsAlertInformedEntity>)

    @Query("DELETE FROM gtfsrt_alert_translations WHERE alertId = :id")
    suspend fun deleteTranslationsFor(id: String)

    @Query("DELETE FROM alerts_active_periods WHERE alertId = :id")
    suspend fun deleteActivePeriodsFor(id: String)

    @Query("DELETE FROM alerts_informed_entities WHERE alertId = :id")
    suspend fun deleteInformedEntitiesFor(id: String)

    /**
     * Inserisce o aggiorna un alert e tutti i suoi figli atomicamente.
     *
     * Nota: se l'alert esiste già, ne preserviamo il valore di `seen` esistente
     * (non vogliamo che un re-fetch del feed reimposti a false un alert già letto).
     * Il chiamante può forzare un valore passandolo dentro `alert.seen`; in quel
     * caso si usa quello.
     */
    @Transaction
    suspend fun insertMissingAlerts(
        alerts: List<GtfsAlertEntity>,
        translations: List<GtfsAlertsTranslation>,
        periods: List<GtfsAlertsActivePeriods>,
        entities: List<GtfsAlertInformedEntity>,
        preserveSeen: Boolean = true
    ) {
        /*
        *** CONSIDER THIS if we ever need to replace the data instead of ignoring ***
        val toInsert = if (preserveSeen) {
            val existingSeen = isUserSeen(alert.id)
            if (existingSeen != null) alert.copy(userSeen = existingSeen) else alert
        } else {
            alert
        }
                insertAlert(toInsert)

         */


        // Pulizia esplicita dei figli prima di reinserirli.
        // Le CASCADE coprirebbero il caso di REPLACE su PK, ma essere espliciti
        // evita sorprese e funziona anche se un giorno cambiamo strategia.
        //deleteTranslationsFor(alert.id)
        //deleteActivePeriodsFor(alert.id)
        //deleteInformedEntitiesFor(alert.id)
        if(alerts.isNotEmpty()) insertAlerts(alerts)
        if (translations.isNotEmpty()) insertTranslations(translations)
        if (periods.isNotEmpty()) insertActivePeriods(periods)
        if (entities.isNotEmpty()) insertInformedEntities(entities)
    }

    // ---------- "Seen" flag ----------

    @Query("SELECT userSeen FROM gtfsrt_alerts WHERE id = :id")
    suspend fun isUserSeen(id: String): Boolean?

    @Query("UPDATE gtfsrt_alerts SET userSeen = :seen WHERE id = :id")
    suspend fun setSeen(id: String, seen: Boolean)

    @Query("UPDATE gtfsrt_alerts SET userSeen = 1")
    suspend fun markAllSeen()

    //@Query("SELECT COUNT(*) FROM gtfsrt_alerts WHERE userSeen = 0")
    //suspend fun countUnseen(): Int

    // ---------- Read ----------

    @Transaction
    @Query("SELECT * FROM gtfsrt_alerts ORDER BY fetchedAt DESC")
    fun getAllAlertsLiveData(): LiveData<List<AlertWithDetails>>

    @Transaction
    @Query("SELECT * FROM gtfsrt_alerts")
    suspend fun getAllAlerts(): List<AlertWithDetails>

    @Transaction
    @Query("SELECT * FROM gtfsrt_alerts WHERE userSeen = 0 ORDER BY fetchedAt DESC")
    suspend fun getUnseenAlerts(): List<AlertWithDetails>

    @Transaction
    @Query("SELECT * FROM gtfsrt_alerts WHERE id = :id")
    suspend fun getAlert(id: String): AlertWithDetails?

    @Transaction
    @Query("""
        SELECT a.* FROM gtfsrt_alerts a
        INNER JOIN alerts_informed_entities ie ON ie.alertId = a.id
        WHERE ie.stopId = :stopId
        ORDER BY a.fetchedAt DESC
    """)
    fun getAlertsForStop(stopId: String): LiveData<List<AlertWithDetails>>

    @Transaction
    @Query("""
        SELECT al.* FROM gtfsrt_alerts al
        INNER JOIN alerts_informed_entities ie ON ie.alertId = al.id
        WHERE ie.routeId = :routeId OR ie.tripRouteId = :routeId
        ORDER BY al.fetchedAt DESC
    """)
    fun getAlertsForRoute(routeId: String): LiveData<List<AlertWithDetails>>

    // ---------- Delete ----------

    @Query("DELETE FROM gtfsrt_alerts WHERE id = :id")
    suspend fun deleteAlert(id: String)


    @Delete
    suspend fun deleteAlerts(alerts: List<GtfsAlertEntity>)
    @Query("DELETE FROM gtfsrt_alerts")
    suspend fun deleteAll()



    /**
     * Cancella tutti gli alert ricevuti più di 48 ore fa.
     * Le CASCADE sulle FK puliscono automaticamente translations,
     * active_periods e informed_entities.
     *
     * @param now epoch millis "adesso" (default: System.currentTimeMillis()).
     *            Esposto come parametro per facilitare i test.
     * @return numero di righe cancellate.
     */
    @Query("DELETE FROM gtfsrt_alerts WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    //TODO use this to remove inactive alerts
    suspend fun deleteInactiveAlerts() {
        val alerts = getAllAlerts()
        val alertsRemove = ArrayList<GtfsAlertEntity>()
        val currentUnixTime = (System.currentTimeMillis()/1000).toInt()
        for (a in alerts) {
            var active = false
            for(p in a.activePeriods){
                if(p.end==null || p.start==null) continue
                if (p.start <= currentUnixTime && p.end>=currentUnixTime) {
                    active = true
                    break
                }
            }
            if(!active)
                alertsRemove.add(a.alert)
        }
        deleteAlerts(alertsRemove)
    }

    suspend fun deleteOlderThan48h(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - 48L * 60L * 60L * 1000L
        return deleteOlderThan(cutoff)
    }

    suspend fun deleteOlderThanHours(hours: Long, now : Long = System.currentTimeMillis()): Int {
        val cutoff = now - hours *60L*60L*1000
        return deleteOlderThan(cutoff)
    }
}
