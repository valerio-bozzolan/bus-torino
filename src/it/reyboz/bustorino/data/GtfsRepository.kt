package it.reyboz.bustorino.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.data.gtfs.GtfsDBDao
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.data.gtfs.MatoPattern
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops

class GtfsRepository(
        val gtfsDao: GtfsDBDao
) {


    fun getLinesLiveDataForFeed(feed: String): LiveData<List<GtfsRoute>>{
        //return withContext(Dispatchers.IO){
            return gtfsDao.getRoutesForFeed(feed)
        //}
    }
    fun getPatternsForRouteID(routeID: String): LiveData<List<MatoPattern>>{
        return if(routeID.isNotEmpty())
            gtfsDao.getPatternsByRouteID(routeID)
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
}