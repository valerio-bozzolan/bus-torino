package it.reyboz.bustorino.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.data.gtfs.*

class GtfsRepository(
    context: Context
) {

    val gtfsDao: GtfsDBDao
    val alertsDao: AlertsDao
    init{
        val gtfsDB = GtfsDatabase.getGtfsDatabase(context)
        gtfsDao = gtfsDB.gtfsDao()
        alertsDao = gtfsDB.alertsDao()
    }
    fun getLinesLiveDataForFeed(feed: String): LiveData<List<GtfsRoute>>{
        //return withContext(Dispatchers.IO){
            return gtfsDao.getRoutesForFeed(feed)
        //}
    }
    fun getPatternsForRouteID(routeID: String): LiveData<List<MatoPattern>>{
        return if(routeID.isNotEmpty())
            gtfsDao.getPatternsLiveDataByRouteID(routeID)
        else
            MutableLiveData(listOf())
    }

    /**
     * Get the patterns with the stops lists (gtfsIDs only)
     */
    fun getPatternsWithStopsForRouteID(routeID: String): LiveData<List<MatoPatternWithStops>>{
        return if(routeID.isNotEmpty())
            gtfsDao.getPatternsWithStopsByRouteID(routeID)
        else
            MutableLiveData(listOf())
    }

    fun getAllRoutes(): LiveData<List<GtfsRoute>>{
        return  gtfsDao.getAllRoutes()
    }

    fun getRouteFromGtfsId(gtfsId: String): LiveData<GtfsRoute>{
        return gtfsDao.getRouteByGtfsID(gtfsId)
    }

    fun getAlertsByRouteID(routeID: String): LiveData<List<AlertWithDetails>>{
        return alertsDao.getAlertsForRoute(routeID)
    }
}