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
    private val filteredLinesLiveData = MediatorLiveData<List<GtfsRoute>>()
    fun getLinesLiveData(): LiveData<List<GtfsRoute>> {
        return filteredLinesLiveData
    }

    private fun filterLinesForQuery(lines: List<GtfsRoute>, query: String): List<GtfsRoute>{
        val result=  lines.filter { r-> query.lowercase() in r.shortName.lowercase() }

        return result
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