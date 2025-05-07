package it.reyboz.bustorino.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.backend.mato.MatoAPIFetcher
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.middleware.RecursionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class ArrivalsViewModel(application: Application): AndroidViewModel(application) {

    // Arrivals of palina
    val appContext: Context
    init {
        appContext = application.applicationContext
    }

    val palinaLiveData = MediatorLiveData<Palina>()
    val sourcesLiveData = MediatorLiveData<Passaggio.Source>()

    val resultLiveData = MediatorLiveData<Fetcher.Result>()

    val currentFetchers = MediatorLiveData<List<ArrivalsFetcher>>()

    fun requestArrivalsForStop(stopId: String, fetchers: List<ArrivalsFetcher>){
        val context = appContext //application.applicationContext
        currentFetchers.value = fetchers
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
            return
        }

        // Equivalente del doInBackground nell'AsyncTask
        val recursionHelper = RecursionHelper(fetchers.toTypedArray())
        var resultPalina = Palina(stopId)

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


            if (fetcher is MatoAPIFetcher) {
                fetcher.appContext = appContext
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
                //set data
                resultLiveData.postValue(Fetcher.Result.OK)
                palinaLiveData.postValue(palina)
                //TODO: Rotate the fetchers appropriately
                return
            }
            //end Fetchers loop
        }

        // Se arriviamo qui, tutti i fetcher hanno fallito
        //failedAll = true

        // Se abbiamo comunque una palina, la restituiamo
        if (resultPalina != null) {
            resultLiveData.postValue(resultRef.get())
            palinaLiveData.postValue(resultPalina)
        }

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