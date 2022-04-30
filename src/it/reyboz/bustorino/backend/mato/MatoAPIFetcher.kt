/*
	BusTO  - Backend components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino.backend.mato

import android.content.Context
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.RequestFuture
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.data.gtfs.GtfsAgency
import it.reyboz.bustorino.data.gtfs.GtfsFeed
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.data.gtfs.MatoPattern
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList


open class MatoAPIFetcher(val minNumPassaggi: Int) : ArrivalsFetcher {
    var appContext: Context? = null
        set(value) {
            field = value!!.applicationContext
        }
    constructor(): this(2)


    override fun ReadArrivalTimesAll(stopID: String?, res: AtomicReference<Fetcher.Result>?): Palina {
        stopID!!

        val now = Calendar.getInstance().time
        var numMinutes = 0
        var palina = Palina(stopID)
        var numPassaggi = 0
        var trials = 0
        val numDepartures = 4
        while (numPassaggi < minNumPassaggi && trials < 4) {

            //numDepartures+=2
            numMinutes += 20
            val future = RequestFuture.newFuture<Palina>()
            val request = MapiArrivalRequest(stopID, now, numMinutes * 60, numDepartures, res, future, future)
            if (appContext == null || res == null) {
                Log.e("BusTO:MatoAPIFetcher", "ERROR: Given null context or null result ref")
                return Palina(stopID)
            }
            val requestQueue = NetworkVolleyManager.getInstance(appContext).requestQueue
            request.setTag(getVolleyReqTag(MatoQueries.QueryType.ARRIVALS))
            requestQueue.add(request)

            try {
                val palinaResult =  future.get(5, TimeUnit.SECONDS)
                if (palinaResult!=null) {
                    if (BuildConfig.DEBUG)
                    for (r in palinaResult.queryAllRoutes()){
                        Log.d(DEBUG_TAG, "route " + r.gtfsId + " has " + r.passaggi.size + " passaggi: "+ r.passaggiToString)
                    }
                    palina = palinaResult
                    numPassaggi = palina.minNumberOfPassages
                } else{
                    Log.d(DEBUG_TAG, "Result palina is null")
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                res.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                if (res.get() == Fetcher.Result.OK)
                res.set(Fetcher.Result.SERVER_ERROR)
            } catch (e: TimeoutException) {
                res.set(Fetcher.Result.CONNECTION_ERROR)
                e.printStackTrace()
            }
            trials++

        }

        return palina
    }

    override fun getSourceForFetcher(): Passaggio.Source {
        return Passaggio.Source.MatoAPI
    }

    companion object{
        const val VOLLEY_TAG = "MatoAPIFetcher"

        const val DEBUG_TAG = "BusTO:MatoAPIFetcher"

        val REQ_PARAMETERS = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "DNT" to "1",
            "Host" to "mapi.5t.torino.it")

        private val longRetryPolicy = DefaultRetryPolicy(10000,5,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)

        fun getVolleyReqTag(type: MatoQueries.QueryType): String{
            return when (type){
                MatoQueries.QueryType.ALL_STOPS -> VOLLEY_TAG +"_AllStops"
                MatoQueries.QueryType.ARRIVALS -> VOLLEY_TAG+"_Arrivals"
                MatoQueries.QueryType.FEEDS -> VOLLEY_TAG +"_Feeds"
                MatoQueries.QueryType.ROUTES -> VOLLEY_TAG +"_AllRoutes"
                MatoQueries.QueryType.PATTERNS_FOR_ROUTES -> VOLLEY_TAG + "_PatternsForRoute"
            }
        }

        /**
         * Get stops from the MatoAPI, set [res] accordingly
         */
        fun getAllStopsGTT(context: Context, res: AtomicReference<Fetcher.Result>?): List<Palina>{
            val requestQueue = NetworkVolleyManager.getInstance(context).requestQueue
            val future = RequestFuture.newFuture<List<Palina>>()

            val request = VolleyAllStopsRequest(future, future)
            request.tag = getVolleyReqTag(MatoQueries.QueryType.ALL_STOPS)
            request.retryPolicy = longRetryPolicy

            requestQueue.add(request)

            var palinaList:List<Palina> = mutableListOf()

            try {
                palinaList = future.get(120, TimeUnit.SECONDS)

                res?.set(Fetcher.Result.OK)
            }catch (e: InterruptedException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.SERVER_ERROR)
            } catch (e: TimeoutException) {
                res?.set(Fetcher.Result.CONNECTION_ERROR)
                e.printStackTrace()
            }
            return palinaList
        }
        /*
        fun makeRequest(type: QueryType?, variables: JSONObject) : String{
            type.let {
                val requestData = JSONObject()
                when (it){
                    QueryType.ARRIVALS ->{
                        requestData.put("operationName","AllStopsDirect")
                        requestData.put("variables", variables)
                        requestData.put("query", MatoQueries.QUERY_ARRIVALS)
                    }
                    else -> {
                        //TODO all other cases
                    }
                }


                //todo make the request...
                //https://pablobaxter.github.io/volley-docs/com/android/volley/toolbox/RequestFuture.html
                //https://stackoverflow.com/questions/16904741/can-i-do-a-synchronous-request-with-volley

            }
            return ""
        }
         */
        fun parseStopJSON(jsonStop: JSONObject): Palina{
            val latitude = jsonStop.getDouble("lat")
            val longitude = jsonStop.getDouble("lon")
            val palina = Palina(
                jsonStop.getString("code"),
                jsonStop.getString("name"),
                null, null, latitude, longitude,
                jsonStop.getString("gtfsId")
            )
            val routesStoppingJSON = jsonStop.getJSONArray("routes")
            val baseRoutes = mutableListOf<Route>()
            // get all the possible routes
            for (i in 0 until routesStoppingJSON.length()){
                val routeBaseInfo = routesStoppingJSON.getJSONObject(i)
                val r = Route(routeBaseInfo.getString("shortName"), Route.Type.UNKNOWN,"")
                r.setGtfsId(routeBaseInfo.getString("gtfsId").trim())
                baseRoutes.add(r)

            }
            if (jsonStop.has("desc")){
                palina.location = jsonStop.getString("desc")
            }
            //there is also "zoneId" which is the zone of the stop (0-> city, etc)

            if(jsonStop.has("stoptimesForPatterns")) {
                val routesStopTimes = jsonStop.getJSONArray("stoptimesForPatterns")

                for (i in 0 until routesStopTimes.length()) {
                    val patternJSON = routesStopTimes.getJSONObject(i)
                    val mRoute = parseRouteStoptimesJSON(patternJSON)

                    //Log.d("BusTO-MapiFetcher")
                    //val directionId = patternJSON.getJSONObject("pattern").getInt("directionId")
                    //TODO: use directionId
                    palina.addRoute(mRoute)
                    for (r in baseRoutes) {
                        if (mRoute.gtfsId != null && r.gtfsId.equals(mRoute.gtfsId)) {
                            baseRoutes.remove(r)
                            break
                        }
                    }
                }
            }
            for (noArrivalRoute in baseRoutes){
                palina.addRoute(noArrivalRoute)
            }
            //val gtfsRoutes = mutableListOf<>()
            return palina
        }
        fun parseRouteStoptimesJSON(jsonPatternWithStops: JSONObject): Route{
            val patternJSON = jsonPatternWithStops.getJSONObject("pattern")
            val routeJSON = patternJSON.getJSONObject("route")

            val passaggiJSON = jsonPatternWithStops.getJSONArray("stoptimes")
            val gtfsId = routeJSON.getString("gtfsId").trim()
            val passages = mutableListOf<Passaggio>()
            for( i in 0 until passaggiJSON.length()){
                val stoptime = passaggiJSON.getJSONObject(i)
                val scheduledTime = stoptime.getInt("scheduledArrival")
                val realtimeTime = stoptime.getInt("realtimeArrival")
                val realtime = stoptime.getBoolean("realtime")
                passages.add(
                    Passaggio(realtimeTime,realtime, realtimeTime-scheduledTime,
                        Passaggio.Source.MatoAPI)
                )
            }
            var routeType = Route.Type.UNKNOWN
            if (gtfsId[gtfsId.length-1] == 'E')
                routeType = Route.Type.LONG_DISTANCE_BUS
            else when( routeJSON.getString("mode").trim()){
                "BUS" -> routeType = Route.Type.BUS
                "TRAM" -> routeType = Route.Type.TRAM
            }
            val route = Route(
                routeJSON.getString("shortName"),
                patternJSON.getString("headsign"),
                routeType,
                passages,
            )
            route.setGtfsId(gtfsId)
            return route
        }


        fun makeRequestParameters(requestName:String, variables: JSONObject, query: String): JSONObject{
            val data = JSONObject()
            data.put("operationName", requestName)
            data.put("variables", variables)
            data.put("query", query)
            return  data
        }


        fun getFeedsAndAgencies(context: Context, res: AtomicReference<Fetcher.Result>?):
                Pair<List<GtfsFeed>, ArrayList<GtfsAgency>> {
            val requestQueue = NetworkVolleyManager.getInstance(context).requestQueue
            val future = RequestFuture.newFuture<JSONObject>()

            val request = MatoVolleyJSONRequest(MatoQueries.QueryType.FEEDS, JSONObject(), future, future)
            request.setRetryPolicy(longRetryPolicy)
            request.tag = getVolleyReqTag(MatoQueries.QueryType.FEEDS)

            requestQueue.add(request)

            val feeds = ArrayList<GtfsFeed>()
            val agencies = ArrayList<GtfsAgency>()
            var outObj = ""
            try {
                val resObj = future.get(120,TimeUnit.SECONDS)
                outObj = resObj.toString(1)
                val feedsJSON = resObj.getJSONArray("feeds")
                for (i in 0 until feedsJSON.length()){
                    val resTup = ResponseParsing.parseFeedJSON(feedsJSON.getJSONObject(i))
                    feeds.add(resTup.first)

                    agencies.addAll(resTup.second)
                }


            } catch (e: InterruptedException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.SERVER_ERROR)
            } catch (e: TimeoutException) {
                res?.set(Fetcher.Result.CONNECTION_ERROR)
                e.printStackTrace()
            } catch (e: JSONException){
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
                Log.e(DEBUG_TAG, "Downloading feeds: $outObj")
            }
            return Pair(feeds,agencies)

        }
        fun getRoutes(context: Context, res: AtomicReference<Fetcher.Result>?):
                ArrayList<GtfsRoute>{
            val requestQueue = NetworkVolleyManager.getInstance(context).requestQueue
            val future = RequestFuture.newFuture<JSONObject>()

            val params = JSONObject()
            params.put("feeds","gtt")

            val request = MatoVolleyJSONRequest(MatoQueries.QueryType.ROUTES, params, future, future)
            request.tag = getVolleyReqTag(MatoQueries.QueryType.ROUTES)
            request.retryPolicy = longRetryPolicy

            requestQueue.add(request)

            val routes = ArrayList<GtfsRoute>()
            var outObj = ""
            try {
                val resObj = future.get(120,TimeUnit.SECONDS)
                outObj = resObj.toString(1)
                val routesJSON = resObj.getJSONArray("routes")
                for (i in 0 until routesJSON.length()){
                    val route = ResponseParsing.parseRouteJSON(routesJSON.getJSONObject(i))
                    routes.add(route)
                }


            } catch (e: InterruptedException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.SERVER_ERROR)
            } catch (e: TimeoutException) {
                res?.set(Fetcher.Result.CONNECTION_ERROR)
                e.printStackTrace()
            } catch (e: JSONException){
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
                Log.e(DEBUG_TAG, "Downloading feeds: $outObj")
            }
            return routes

        }
        fun getPatternsWithStops(context: Context, routesGTFSIds: ArrayList<String>, res: AtomicReference<Fetcher.Result>?): ArrayList<MatoPattern>{
            val requestQueue = NetworkVolleyManager.getInstance(context).requestQueue

            val future = RequestFuture.newFuture<JSONObject>()

            val params = JSONObject()
            for (r in routesGTFSIds){
                if(r.isEmpty()) routesGTFSIds.remove(r)
            }
            val routes = JSONArray(routesGTFSIds)

            params.put("routes",routes)

            val request = MatoVolleyJSONRequest(MatoQueries.QueryType.PATTERNS_FOR_ROUTES, params, future, future)
            request.retryPolicy = longRetryPolicy
            request.tag = getVolleyReqTag(MatoQueries.QueryType.PATTERNS_FOR_ROUTES)

            requestQueue.add(request)

            val patterns = ArrayList<MatoPattern>()
            //var outObj = ""
            try {
                val resObj = future.get(60,TimeUnit.SECONDS)
                //outObj = resObj.toString(1)
                val routesJSON = resObj.getJSONArray("routes")
                for (i in 0 until routesJSON.length()){
                    val patternList = ResponseParsing.parseRoutePatternsStopsJSON(routesJSON.getJSONObject(i))
                    patterns.addAll(patternList)
                }


            } catch (e: InterruptedException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
                res?.set(Fetcher.Result.SERVER_ERROR)
            } catch (e: TimeoutException) {
                res?.set(Fetcher.Result.CONNECTION_ERROR)
                e.printStackTrace()
            } catch (e: JSONException){
                e.printStackTrace()
                res?.set(Fetcher.Result.PARSER_ERROR)
                //Log.e(DEBUG_TAG, "Downloading feeds: $outObj")
            }
            /*
            var numRequests = 0
            for(routeName in routesGTFSIds){
                if (!routeName.isEmpty()) numRequests++
            }
            val countDownForRequests = CountDownLatch(numRequests)
            val lockSave = ReentrantLock()
            //val countDownFor
            for (routeName in routesGTFSIds){
                val pars = JSONObject()
                pars.put("")

            }
            val goodResponseListener = Response.Listener<JSONObject> {  }
            val errorResponseListener = Response.ErrorListener {  }
             */

            return patterns
        }


    }

}