package it.reyboz.bustorino.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import it.reyboz.bustorino.data.GtfsRepository
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.data.gtfs.GtfsDatabase

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
}