/*
	BusTO - View Model components
    Copyright (C) 2025 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.backend.mato.MatoAPIFetcher
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.OldDataRepository
import it.reyboz.bustorino.middleware.RecursionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ArrivalsViewModel(application: Application): AndroidViewModel(application) {

    // Arrivals of palina
    val appContext: Context = application.applicationContext
    private val executor = Executors.newFixedThreadPool(2)
    private val oldRepo = OldDataRepository(executor, application)


    val palinaFromArrivals = MediatorLiveData<Palina>()
    val palinaToShow = MediatorLiveData<Palina>()
    val sourcesLiveData = MediatorLiveData<Passaggio.Source>()

    val resultLiveData = MutableLiveData<Fetcher.Result>()

    val currentFetchers = MediatorLiveData<List<ArrivalsFetcher>>()

    private val stopID = MutableLiveData<String>()

    fun setStopId(stopId: String) { stopID.value = (stopId) }
    val stopFavoritesData = stopID.switchMap { oldRepo.getFavoritesLiveDataByStopId(listOf(it))}
    val stopInFavorites = stopFavoritesData.map { it!=null && it.isNotEmpty() }

        /// OLD REPO for stops instance

    private var stopIdRequested = ""
    private val stopFromDB = MutableLiveData<Stop>()

    val arrivalsRequestRunningLiveData = MutableLiveData(false)

    private val oldRepoStopCallback = OldDataRepository.Callback<ArrayList<Stop>>{ stopListRes ->
        if(stopIdRequested.isEmpty()) return@Callback

        if(stopListRes.isSuccess) {
            val stopF = stopListRes.result!!.filter { s -> s.ID == stopIdRequested }
            if (stopF.isEmpty()) {
                Log.w(DEBUG_TAG, "Requested stop $stopIdRequested but is not in the list from database: ${stopListRes.result}")
            } else{
                stopFromDB.postValue(stopF[0])
                Log.d(DEBUG_TAG, "Setting new stop ${stopF[0]} from database")
            }
        } else{
            Log.e(DEBUG_TAG, "Requested stop ${stopIdRequested} from database but error occured: ${stopListRes.exception}")
        }
    }

    init {

        palinaFromArrivals.addSource(stopFromDB){
            s ->
            val hasSource = palinaFromArrivals.value?.passaggiSourceIfAny
            //Log.d(DEBUG_TAG, "Have current palina ${palinaLiveData.value!=null}, source passaggi $hasSource,  new incoming stop $s from database")
            val newp = if(palinaFromArrivals.value == null) Palina(s) else Palina.mergePaline(palinaFromArrivals.value, Palina(s))
            //Log.d(DEBUG_TAG, "Merged palina: $newp, num passages: ${newp?.totalNumberOfPassages}, has coords: ${newp?.hasCoords()}")
            newp?.let { pal -> palinaFromArrivals.postValue(pal) }
        }
        palinaToShow.addSource(stopFavoritesData){ dat ->
            val current = palinaFromArrivals.value
            Log.d(DEBUG_TAG, "have palina $current and favorites data: $dat")
            if(dat!=null && current!=null){
                if(dat.size>0 && dat[0].stopUserName!=null) // is in the favorites
                    current.stopUserName = dat[0].stopUserName
                //set new data in palinaLiveData
                palinaToShow.value = current
            }
        }
        palinaToShow.addSource(palinaFromArrivals){ p->
            stopFavoritesData.value?.let {it ->
                if(it.isNotEmpty() && it[0].stopUserName!=null) {
                    p.stopUserName = it[0].stopUserName
                }
            }
            palinaToShow.value = p
        }

    }

    fun clearPalinaArrivals(palina: Palina) : Palina{
        palina.clearRoutes()
        return palina
    }


    fun requestArrivalsForStop(stopId: String, fetchers: List<ArrivalsFetcher>){
        val context = appContext //application.applicationContext
        currentFetchers.value = fetchers
        //THIS IS TOTALLY WRONG!!!
        /*palinaLiveData.value?.let{
            palinaLiveData.value = clearPalinaArrivals(it)
        }

         */
        //request stop from the DB
        stopIdRequested = stopId
        oldRepo.requestStopsWithGtfsIDs(listOf("gtt:$stopId"), oldRepoStopCallback)
        arrivalsRequestRunningLiveData.value = true
        viewModelScope.launch(Dispatchers.IO){
            runArrivalsFetching(stopId, fetchers, context)
        }
    }

    fun requestArrivalsForStop(stopId: String, fetchersSources: Array<String>){
        val fetchers = constructFetchersFromStrList(fetchersSources)
        requestArrivalsForStop(stopId, fetchers)
    }

    private suspend fun runArrivalsFetching(stopId: String, fetchers: List<ArrivalsFetcher>, appContext: Context) {

        if (fetchers.isEmpty()) {
            //do nothing
            arrivalsRequestRunningLiveData.postValue(false)
            return
        }

        // Equivalente del doInBackground nell'AsyncTask
        val recursionHelper = RecursionHelper(fetchers.toTypedArray())
        var resultPalina : Palina? = null

        val stringBuilder = StringBuilder()
        for (f in fetchers) {
            stringBuilder.append("")
            stringBuilder.append(f.javaClass.simpleName)
            stringBuilder.append("; ")
        }
        Log.d(DEBUG_TAG, "Using fetchers: $stringBuilder")

        val resultRef = AtomicReference<Fetcher.Result>()

        while (recursionHelper.valid()) {

            val fetcher = recursionHelper.getAndMoveForward()

            sourcesLiveData.postValue(fetcher.sourceForFetcher)


            if (fetcher is ArrivalsFetcherContext) {
                fetcher.setContext(appContext)
            }
            Log.d(DEBUG_TAG, "Using the ArrivalsFetcher: ${fetcher.javaClass}")

            // Verifica se è un fetcher per MetroStop da saltare
            try {
                if (fetcher is FiveTAPIFetcher && stopId.toInt() >= 8200) {
                    continue
                }
            } catch (ex: NumberFormatException) {
                Log.e(DEBUG_TAG, "The stop number is not a valid integer, expect failures")
            }

            // Legge i tempi di arrivo
            val palina = fetcher.ReadArrivalTimesAll(stopId, resultRef)

            Log.d(DEBUG_TAG, "Arrivals fetcher: $fetcher\n\tProgress: ${resultRef.get()}")


            // Gestione del FiveTAPIFetcher per ottenere le direzioni
            if (fetcher is FiveTAPIFetcher) {
                val branchResultRef = AtomicReference<Fetcher.Result>()
                val branches = fetcher.getDirectionsForStop(stopId, branchResultRef)
                Log.d(DEBUG_TAG, "FiveTArrivals fetcher: $fetcher\n\tDetails req: ${branchResultRef.get()}")

                if (branchResultRef.get() == Fetcher.Result.OK) {
                    palina.addInfoFromRoutes(branches)

                    // Inserisce i dati nel database
                    viewModelScope.launch(Dispatchers.IO) {
                        //modify the DB in another coroutine in the background
                        NextGenDB.insertBranchesIntoDB(appContext,branches)
                    }

                } else {
                    resultRef.set(Fetcher.Result.NOT_FOUND)
                }
            }

            // Unisce percorsi duplicati
            palina.mergeDuplicateRoutes(0)

            if (resultRef.get() == Fetcher.Result.OK && palina.getTotalNumberOfPassages() == 0) {
                resultRef.set(Fetcher.Result.EMPTY_RESULT_SET)
                Log.d(DEBUG_TAG, "Setting empty results")
            }
            //reportProgress
            resultLiveData.postValue(resultRef.get())

            // Se è un MatoAPIFetcher con risultati validi, salviamo i dati
            if (resultPalina == null && fetcher is MatoAPIFetcher && palina.queryAllRoutes().size > 0) {
                resultPalina = palina
            }

            // Se abbiamo un risultato OK, restituiamo la palina
            if (resultRef.get() == Fetcher.Result.OK) {
                setResultAndPalinaFromFetchers(palina, Fetcher.Result.OK)

                return
            }
            //end Fetchers loop
        }

        // Se arriviamo qui, tutti i fetcher hanno fallito
        //failedAll = true

        // Se abbiamo comunque una palina, la restituiamo
        resultPalina?.let {
            setResultAndPalinaFromFetchers(it, resultRef.get())
        }
        //in ogni caso, settiamo la richiesta come conclusa
        arrivalsRequestRunningLiveData.postValue(false)
        Log.d(DEBUG_TAG, "Finished fetchers available to search arrivals for palina stop $stopId")
    }

    private fun setResultAndPalinaFromFetchers(palina: Palina, fetcherResult: Fetcher.Result) {
        arrivalsRequestRunningLiveData.postValue(false)
        resultLiveData.postValue(fetcherResult)
        Log.d(DEBUG_TAG, "Have new result palina for stop ${palina.ID}, source ${palina.passaggiSourceIfAny} has coords: ${palina.hasCoords()}")
        Log.d(DEBUG_TAG, "Old palina liveData is: ${palinaFromArrivals.value?.stopDisplayName}, has Coords ${palinaFromArrivals.value?.hasCoords()}")
        palinaFromArrivals.postValue(Palina.mergePaline(palina, palinaFromArrivals.value))
    }
    companion object{
        const val DEBUG_TAG="BusTO-ArrivalsViMo"

        @JvmStatic
        fun getFetcherFromStrSource(src:String): ArrivalsFetcher?{
            val srcEnum = Passaggio.Source.valueOf(src)

            val fe: ArrivalsFetcher? = when(srcEnum){
                Passaggio.Source.FiveTAPI -> FiveTAPIFetcher()
                Passaggio.Source.GTTJSON ->  GTTJSONFetcher()
                Passaggio.Source.FiveTScraper -> FiveTScraperFetcher()
                Passaggio.Source.MatoAPI -> MatoAPIFetcher()
                Passaggio.Source.UNDETERMINED -> null
                null -> null
            }
            return fe
        }

        @JvmStatic
        fun constructFetchersFromStrList(sources: Array<String>): List<ArrivalsFetcher>{
            val fetchers = mutableListOf<ArrivalsFetcher>()
            for(s in sources){
                val fe = getFetcherFromStrSource(s)
                if(fe!=null){
                    fetchers.add(fe)
                } else{
                    Log.d(DEBUG_TAG, "Cannot convert fetcher source $s to a fetcher")
                }
            }

            return fetchers
        }
    }

}