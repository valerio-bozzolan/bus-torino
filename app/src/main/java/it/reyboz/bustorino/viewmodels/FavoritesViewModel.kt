package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.application
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.StopFavoritesData
import it.reyboz.bustorino.data.DBUpdateWorker.Companion.getWorkInfoLiveData
import it.reyboz.bustorino.data.FavoritesLiveData
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.data.QueryLiveData
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    val oldRepo: OldDataRepository

    init {
        val executor = Executors.newCachedThreadPool()
        oldRepo = OldDataRepository(executor, application)
    }
    /*var favoritesLiveData: FavoritesLiveData? = null

    override fun onCleared() {
        if (favoritesLiveData != null) favoritesLiveData!!.onClear()
        super.onCleared()
    }

    val favorites: FavoritesLiveData
        get() {
            if (favoritesLiveData == null) {
                favoritesLiveData = FavoritesLiveData(application, true)
            }
            return favoritesLiveData!!
        }
*/
    val isDBUpdating = getWorkInfoLiveData(application).map { wilist ->
        var isUpdating = false
        if(wilist.isNotEmpty()){
            val wi = wilist[0]
            isUpdating = wi.state == WorkInfo.State.RUNNING
        }
        isUpdating
    }


    // ---- NEW CODE -----
    // this code is not active now, but it is gonna be useful for the day when the ContentObserver is gonna be dismissed
    //for all favorites
    val favoritesWithStop = MediatorLiveData<List<Stop>>()
    val favoritesNoStop = oldRepo.getFavoritesLiveData()

    val stopsForFavorites = favoritesNoStop.switchMap {
        val sids = it.map { d-> d.stopID }
        oldRepo.getStopsForIdsLiveData(sids)
    }

    init{
        // this fetches the stops when I have gotten the favorites
        favoritesWithStop.addSource(favoritesNoStop){ dat ->
            if(dat!=null) stopsForFavorites.value?.let{ stops ->
                matchFavoritesStopsAndUpdate(dat, stops)
            }
        }

        favoritesWithStop.addSource(stopsForFavorites) { stops ->
            favoritesNoStop.value?.let { fav ->
                if(stops!=null){
                    matchFavoritesStopsAndUpdate(fav, stops)
                }
            }
        }
    }
    fun matchFavoritesStopsAndUpdate(fav: List<StopFavoritesData>, stops: List<Stop>) {
        //copy favorites info
        for(s in stops) {
            val di = fav.first{ it.stopID == s.ID}
            di.addToStop(s)
        }
        favoritesWithStop.value = stops
    }
    companion object {
        const val DEBUG_TAG = "BusTO-FavoritesViewM"
    }
}
