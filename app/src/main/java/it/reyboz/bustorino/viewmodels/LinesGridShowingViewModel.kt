package it.reyboz.bustorino.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.util.LinesNameSorter

class LinesGridShowingViewModel(application: Application) : AndroidViewModel(application) {

    private val gtfsRepo: GtfsRepository

    init {
        val gtfsDao = GtfsDatabase.getGtfsDatabase(application).gtfsDao()
        gtfsRepo = GtfsRepository(gtfsDao)

    }

    val routesLiveData = gtfsRepo.getAllRoutes()

    val isUrbanExpanded = MutableLiveData(true)
    val isExtraUrbanExpanded = MutableLiveData(false)
    val isTouristExpanded = MutableLiveData(false)
    val favoritesExpanded = MutableLiveData(true)

    val favoritesLinesIDs = MutableLiveData<HashSet<String>>()

    private val linesNameSorter = LinesNameSorter()
    private val linesComparator = Comparator<GtfsRoute> { a,b ->
        return@Comparator linesNameSorter.compare(a.shortName, b.shortName)
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