package it.reyboz.bustorino.fragments

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import java.util.concurrent.Executors

class LinesViewModel(application: Application) : AndroidViewModel(application) {

    private val gtfsRepo: GtfsRepository
    private val oldRepo: OldDataRepository
    //val patternsByRouteLiveData: LiveData<List<MatoPattern>>

    private val routeIDToSearch = MutableLiveData<String>()

    val stopsForPatternLiveData = MutableLiveData<List<Stop>>()
    val executor = Executors.newFixedThreadPool(2)

    init {
        val gtfsDao = GtfsDatabase.getGtfsDatabase(application).gtfsDao()
        gtfsRepo = GtfsRepository(gtfsDao)

        oldRepo = OldDataRepository(executor, NextGenDB.getInstance(application))

    }

    val routesGTTLiveData: LiveData<List<GtfsRoute>> by lazy{
        gtfsRepo.getLinesLiveDataForFeed("gtt")
    }
    val patternsWithStopsByRouteLiveData = routeIDToSearch.switchMap {
        gtfsRepo.getPatternsWithStopsForRouteID(it)

    }
    val routesName: LiveData<List<String>> = Transformations.map(routesGTTLiveData) {
        it.map { route -> route.longName }
    }

    fun setRouteIDQuery(routeID: String){
        routeIDToSearch.value = routeID
    }

    fun getRouteIDQueried(): String?{
        return routeIDToSearch.value
    }
    var shouldShowMessage = true;

    fun requestStopsForGTFSIDs(gtfsIDs: List<String>){
        oldRepo.requestStopsWithGtfsIDs(gtfsIDs) {
            if (it.isSuccess) {
                stopsForPatternLiveData.postValue(it.result)
            } else {
                Log.e("BusTO-LinesVM", "Got error on callback with stops for gtfsID")
                it.exception?.printStackTrace()
            }
        }
    }

    fun requestStopsForPatternWithStops(patternStops: MatoPatternWithStops){
        val gtfsIDs = ArrayList<String>()
        for(pat in patternStops.stopsIndices){
            gtfsIDs.add(pat.stopGtfsId)
        }
        requestStopsForGTFSIDs(gtfsIDs)
    }


    /*fun getLinesGTT(): MutableLiveData<List<GtfsRoute>> {
        val routesData = MutableLiveData<List<GtfsRoute>>()
            viewModelScope.launch {
                 val routes=gtfsRepo.getLinesForFeed("gtt")
                routesData.postValue(routes)
            }
        return routesData
    }*/
}