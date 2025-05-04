package it.reyboz.bustorino.map

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import it.reyboz.bustorino.backend.Stop

class MapViewModel : ViewModel() {

    val currentLat = MutableLiveData(INVALID)
    val currentLong = MutableLiveData(INVALID)
    val currentZoom = MutableLiveData(-10.0)

    var stopShowing: Stop? = null

    companion object{
        const val INVALID = -1000.0
    }
}