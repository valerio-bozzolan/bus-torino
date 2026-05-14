package it.reyboz.bustorino.backend

import android.util.Log

data class StopFavoritesData(
    val stopID: String,
    val stopUserName: String? = null
) {
    constructor(s: Stop) : this(s.ID,s.stopUserName)

    fun addToStop(s: Stop) {
        if(s.ID!=stopID){
            Log.e("BusTO-FavoritesData", "Trying to add info to stop with different ID")
        } else{
            s.stopUserName =stopUserName
        }
    }
}
