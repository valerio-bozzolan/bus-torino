package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import it.reyboz.bustorino.backend.Result
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import it.reyboz.bustorino.data.gtfs.PatternStop
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.Executors

class LinesViewModel(application: Application) : AndroidViewModel(application) {

    private val gtfsRepo: GtfsRepository
    private val oldRepo: OldDataRepository
    //val patternsByRouteLiveData: LiveData<List<MatoPattern>>

    private val routeIDToSearch = MutableLiveData<String>()
    private var lastShownPatternStops = ArrayList<String>()

    val currentPatternStops = MutableLiveData<List<PatternStop>>()
    val selectedPatternLiveData = MutableLiveData<MatoPatternWithStops>()


    val stopsForPatternLiveData = MutableLiveData<List<Stop>>()
    private val executor = Executors.newFixedThreadPool(2)

    val mapShowing = MutableLiveData(true)
    fun setMapShowing(yes: Boolean){
        mapShowing.value = yes
        //retrigger redraw
        stopsForPatternLiveData.postValue(stopsForPatternLiveData.value)
    }
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
    val gtfsRoute = routeIDToSearch.switchMap {
        gtfsRepo.getRouteFromGtfsId(it)
    }


    fun setRouteIDQuery(routeID: String){
        routeIDToSearch.value = routeID
    }

    fun getRouteIDQueried(): String?{
        return routeIDToSearch.value
    }
    var shouldShowMessage = true

    fun setPatternToDisplay(patternStops: MatoPatternWithStops){

        selectedPatternLiveData.value = patternStops
    }
    /**
     * Find the
     */
    private fun requestStopsForGTFSIDs(gtfsIDs: List<String>){
        if (gtfsIDs.equals(lastShownPatternStops)){
            //nothing to do
            return
        }
        oldRepo.requestStopsWithGtfsIDs(gtfsIDs) {
            if (it.isSuccess) {
                stopsForPatternLiveData.postValue(it.result)
            } else {
                Log.e("BusTO-LinesVM", "Got error on callback with stops for gtfsID")
                it.exception?.printStackTrace()
            }
        }
        lastShownPatternStops.clear()
        for(id in gtfsIDs)
            lastShownPatternStops.add(id)
    }

    fun requestStopsForPatternWithStops(patternStops: MatoPatternWithStops){
        val gtfsIDs = ArrayList<String>()
        for(pat in patternStops.stopsIndices){
            gtfsIDs.add(pat.stopGtfsId)
        }
        requestStopsForGTFSIDs(gtfsIDs)
    }

    fun getStopByID(id:String) : Stop?{
        //var stop : Stop? = null
        val stop = stopsForPatternLiveData.value?.let { stops ->
            for (s in stops){
                if(s.ID == id)
                    return@let s
            }
            return@let null
        }
        return stop
    }

    private var lastMapPos: Pair<LatLng, Float>? = null

    fun saveMapPos(latLng: LatLng, zoom: Float){
        lastMapPos = Pair(latLng, zoom)
    }

    fun getLastMapPos(): Pair<LatLng,Float>? = lastMapPos


    /*fun getLinesGTT(): MutableLiveData<List<GtfsRoute>> {
        val routesData = MutableLiveData<List<GtfsRoute>>()
            viewModelScope.launch {
                 val routes=gtfsRepo.getLinesForFeed("gtt")
                routesData.postValue(routes)
            }
        return routesData
    }*/
}