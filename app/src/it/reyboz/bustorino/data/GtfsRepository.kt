package it.reyboz.bustorino.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import it.reyboz.bustorino.data.gtfs.*

class GtfsRepository(
        val gtfsDao: GtfsDBDao
) {

    constructor(context: Context) : this(GtfsDatabase.getGtfsDatabase(context).gtfsDao())
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
}