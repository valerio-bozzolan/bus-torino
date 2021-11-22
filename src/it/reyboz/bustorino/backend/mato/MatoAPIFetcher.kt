package it.reyboz.bustorino.backend.mato

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.RequestFuture
import it.reyboz.bustorino.backend.*
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeoutException

import java.util.concurrent.ExecutionException




open class MatoAPIFetcher : ArrivalsFetcher {
    var appContext: Context? = null
        set(value) {
            field = value!!.applicationContext
        }

    override fun ReadArrivalTimesAll(stopID: String?, res: AtomicReference<Fetcher.Result>?): Palina {
        stopID!!
        val future = RequestFuture.newFuture<Palina>()
        val now = Calendar.getInstance().time;
        var numMinutes = 30;
        var palina = Palina(stopID)
        var numPassaggi = 0
        var trials = 0
        while (numPassaggi < 2 && trials < 4) {

            numMinutes += 15
            val request = MapiArrivalRequest(stopID, now, numMinutes * 60, 10, future, future)
            if (appContext == null || res == null) {
                Log.e("BusTO:MatoAPIFetcher", "ERROR: Given null context or null result ref")
                return Palina(stopID)
            }
            val requestQueue = NetworkVolleyManager.getInstance(appContext).requestQueue
            request.setTag(VOLLEY_TAG)
            requestQueue.add(request)


            try {
                val palinaResult =  future.get(5, TimeUnit.SECONDS)
                if (palinaResult!=null) {
                    palina = palinaResult
                    if (palina.totalNumberOfPassages > 0) {
                        res.set(Fetcher.Result.OK)
                    } else res.set(Fetcher.Result.EMPTY_RESULT_SET)
                    numPassaggi = palina.totalNumberOfPassages
                } else{
                    res.set(Fetcher.Result.EMPTY_RESULT_SET)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                res.set(Fetcher.Result.PARSER_ERROR)
            } catch (e: ExecutionException) {
                e.printStackTrace()
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

        fun makeRequest(type: QueryType?, variables: JSONObject) : String{
            type.let {
                val requestData = JSONObject()
                when (it){
                    QueryType.ARRIVALS ->{
                        requestData.put("operationName","AllStopsDirect")
                        requestData.put("variables", variables)
                        requestData.put("query", QUERY_ARRIVALS)
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
        fun parseStopJSON(jsonStop: JSONObject): Palina{
            val latitude = jsonStop.getDouble("lat")
            val longitude = jsonStop.getDouble("lon")
            val palina = Palina(
                jsonStop.getString("code"),
                jsonStop.getString("name"),
                null, null, latitude, longitude
            )
            palina.gtfsID = jsonStop.getString("gtfsId")

            val routesStoppingJSON = jsonStop.getJSONArray("routes")
            val baseRoutes = mutableListOf<Route>()
            for (i in 0 until routesStoppingJSON.length()){
                val routeBaseInfo = routesStoppingJSON.getJSONObject(i)
                val r = Route(routeBaseInfo.getString("shortName"), Route.Type.UNKNOWN,"")
                r.gtfsId = routeBaseInfo.getString("gtfsId").trim()
                baseRoutes.add(r)

            }

            val routesStopTimes = jsonStop.getJSONArray("stoptimesForPatterns")

            for (i in 0 until routesStopTimes.length()){
                val patternJSON = routesStopTimes.getJSONObject(i)
                val mRoute = parseRouteStoptimesJSON(patternJSON)

                //val directionId = patternJSON.getJSONObject("pattern").getInt("directionId")
                //TODO: use directionId
                palina.addRoute(mRoute)
                for (r in baseRoutes) {
                    if (palina.gtfsID != null && r.gtfsId.equals(palina.gtfsID)) {
                        baseRoutes.remove(r)
                        break
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
            val routeJSON = patternJSON.getJSONObject("route");

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
            route.gtfsId = gtfsId
            return route
        }

        const val QUERY_ARRIVALS="""query AllStopsDirect(
              ${'$'}name: String
              ${'$'}startTime: Long
              ${'$'}timeRange: Int
              ${'$'}numberOfDepartures: Int
            ) {
              stops(name: ${'$'}name) {
                __typename
                lat
                lon
                gtfsId
                code
                name
                desc
                wheelchairBoarding
                routes {
                  __typename
                  gtfsId
                  shortName
                }
                stoptimesForPatterns(
                        startTime: ${'$'}startTime
                        timeRange: ${'$'}timeRange
                        numberOfDepartures: ${'$'}numberOfDepartures
                      ) {
                        __typename
                        pattern {
                          __typename
                          headsign
                          directionId
                          route {
                            __typename
                            gtfsId
                            shortName
                            mode
                          }
                        }
                        stoptimes {
                          __typename
                          scheduledArrival
                          realtimeArrival
                          realtime
                          realtimeState
                        }
                      }
              }
            }
            """
    }


    enum class QueryType {
        ARRIVALS,
    }

}