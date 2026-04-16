package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.util.LinesNameSorter

class LinesGridShowingViewModel(application: Application) : AndroidViewModel(application) {

    private val linesNameSorter = LinesNameSorter()
    private val linesComparator = Comparator<GtfsRoute> { a,b ->
        return@Comparator linesNameSorter.compare(a.shortName, b.shortName)
    }

    private val gtfsRepo: GtfsRepository

    private val routesLiveData: LiveData<List<GtfsRoute>> //= gtfsRepo.getAllRoutes()

    val isUrbanExpanded = MutableLiveData(true)
    val isExtraUrbanExpanded = MutableLiveData(false)
    val isTouristExpanded = MutableLiveData(false)
    val favoritesExpanded = MutableLiveData(true)

    val favoritesLinesIDs = MutableLiveData<HashSet<String>>()

    private val queryLiveData = MutableLiveData("")
    fun setLineQuery(query: String){
        if(query!=queryLiveData.value)
            queryLiveData.value = query
    }
    fun getLineQueryValue():String{
        return queryLiveData.value ?: ""
    }
    private val filteredLinesLiveData = MediatorLiveData<List<Pair<GtfsRoute,Int >>>()
    fun getLinesLiveData() = filteredLinesLiveData

    private fun filterLinesForQuery(lines: List<GtfsRoute>, query: String): ArrayList<Pair<GtfsRoute,Int>>{
        var result=  lines.filter { r-> query.lowercase() in r.shortName.lowercase() }
        //EXCLUDE gtt:F - ferrovie (luckily, gtt does not run rail service anymore)
        result = result.filter { r -> r.agencyID != "gtt:F" }

        val out  = ArrayList<Pair<GtfsRoute,Int>>()
        for (r in result){
            out.add(Pair(r,1))
        }
        // add those matching the query in the description
        for (r: GtfsRoute in lines) {
            if (query.lowercase() in r.description.lowercase()) {
                if (r !in result){
                    out.add(Pair(r,2))
                }
            }
        }
        return out
    }

    init {
        val gtfsDao = GtfsDatabase.getGtfsDatabase(application).gtfsDao()
        gtfsRepo = GtfsRepository(gtfsDao)
        routesLiveData = gtfsRepo.getAllRoutes()

        filteredLinesLiveData.addSource(routesLiveData){
            filteredLinesLiveData.value = filterLinesForQuery(it,queryLiveData.value ?: "" )
        }
        filteredLinesLiveData.addSource(queryLiveData){
            routesLiveData.value?.let { routes ->
                filteredLinesLiveData.value = filterLinesForQuery(routes, it)
            }
        }
    }

    fun setFavoritesLinesIDs(linesIds: HashSet<String>){
        favoritesLinesIDs.value = linesIds
    }


    val favoritesLines = favoritesLinesIDs.map {lineIds ->
        val linesList = ArrayList<GtfsRoute>()
        if (lineIds.size == 0 || routesLiveData.value==null) return@map linesList
        for(line in routesLiveData.value!!){
            if(lineIds.contains(line.gtfsId))
                linesList.add(line)
        }
        linesList.sortWith(linesComparator)
        return@map linesList
    }
}