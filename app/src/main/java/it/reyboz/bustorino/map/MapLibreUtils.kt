package it.reyboz.bustorino.map

import android.animation.TypeEvaluator
import android.content.Context
import android.util.Log
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.backend.Stop
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfMeasurement


class MapLibreUtils {
    companion object{
        const val STYLE_BRIGHT_DEFAULT_JSON = "map_style_good_noshops.json"
        const val STYLE_VERSATILES_COLORFUL_JSON = "versatiles_colorful_light.json"
        const val STYLE_OSM_RASTER="openstreetmap_raster.json"
        private const val DEBUG_TAG ="BusTO-MapLibreUtils"

        @JvmStatic
        fun getDefaultStyleJson() = STYLE_VERSATILES_COLORFUL_JSON

        @JvmStatic
        fun shortestRotation(from: Float, to: Float): Float {
            var delta = (to - from) % 360
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            return from + delta
        }
        @JvmStatic
        fun buildLocationComponentActivationOptions(
            style: Style,
            locationComponentOptions: LocationComponentOptions,
            context: Context
        ): LocationComponentActivationOptions {
            return LocationComponentActivationOptions
                .builder(context, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .locationEngineRequest(
                    LocationEngineRequest.Builder(750)
                        .setFastestInterval(750)
                        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                        .build()
                )
                .build()
        }
        @JvmStatic
        fun calcDistanceInSegment(points: List<LatLng>, from: Int, to:Int): Double{
            var d=0.0
            var prev = points[from]
            for(i in from+1..to){
                d += prev.distanceTo(points[i])
                prev = points[i]
            }
            return d
        }
        @JvmStatic
        fun findIndexMidPoint(points: List<LatLng>, from: Int, to: Int, distThresh:Double): Int{
            var totdist=0.0
            var idx = -2
            var prev = points[from]
            for(i in from+1..to){
                totdist += prev.distanceTo(points[i])
                prev = points[i]
                if (totdist >= distThresh){
                    idx = i
                    break
                }
            }
            if(idx==-2) throw Error("Distance out of bounds, total distance is $totdist")
            return idx
        }

        @JvmStatic
        fun findPointsToPutDirectionMarkers(polyPoints: List<LatLng>, stops: List<Stop>, distanceIcon: Double): List<Int>{
            //output value
            val pointsOutput = mutableListOf<Int>()
            val closestIndices = findIndicesClosestPointsForStops(polyPoints, stops)
            Log.d(DEBUG_TAG, "idcs: $closestIndices")
            if(closestIndices.size==0)
                return pointsOutput

            val distancesSec = mutableListOf<Double>()
            var pi = closestIndices[0]
            val cumulativeDist = mutableListOf<Double>()
            var sum = 0.0

            var nPoints = 0
            var distFromLastPoint = 0.0
            for(i in 1..<stops.size){
                val newi = closestIndices[i]
                val dd = calcDistanceInSegment(polyPoints, pi, newi)
                distancesSec.add(dd)
                sum += dd
                cumulativeDist.add(sum)
                distFromLastPoint += dd


                //check if sum is above dist
                if (distFromLastPoint >= distanceIcon){
                    if(BuildConfig.DEBUG)
                        Log.d(DEBUG_TAG, "Add between stop ${stops[i-1]} and stop ${stops[i]}, distance between: $dd")
                    if(dd>100) {
                        val imid = findIndexMidPoint(polyPoints, pi, newi, dd / 2)
                        pointsOutput.add(imid)
                        nPoints += 1
                        distFromLastPoint=0.0
                    }
                } else{
                    //add the last distance
                    //distFromLastPoint+=dd/2
                }
                pi= newi
            }
            return pointsOutput
        }
        /*
            VERSION WITH TOTAL DISTANCE
            val distancesSec = mutableListOf<Double>()
            var prevk = 0
            val cumulativeDist = mutableListOf<Double>()
            var sum = 0.0

            val pointsOutput = mutableListOf<Int>()
            var nPoints = 0
            //for(i in 1..<stops.size){
            var lastStopidx = 0
            var distFromLast = 0.0
            for(k in 1..<polyPoints.size){
                //val newi = closestIndices[i]
                val dd = calcDistanceInSegment(polyPoints, prevk, k)
                distancesSec.add(dd)
                sum += dd
                cumulativeDist.add(sum)
                distFromLast += dd


                //check if sum is above dist
                if (distFromLast >= distanceIcon ){

                    //Log.d(DEBUG_TAG, "Add between stop ${stops[lastStopidx]} and stop ${stops[lastStopidx+1]}")
                    //find closest stops
                    var stopDist =
                        Math.min(polyPoints[prevk].distanceTo(polyPoints[lastStopidx]),polyPoints[k].distanceTo(polyPoints[lastStopidx]))

                    if(lastStopidx+1 < stops.size){
                        stopDist = Math.min(stopDist,
                            Math.min(polyPoints[prevk].distanceTo(polyPoints[lastStopidx+1]),polyPoints[k].distanceTo(polyPoints[lastStopidx+1]))
                        )
                    }
                    if(stopDist>100) {
                        val imid = findIndexMidPoint(polyPoints, prevk, k, dd / 2)
                        pointsOutput.add(imid)
                        nPoints += 1
                        distFromLast = 0.0
                    }
                }
                prevk = k
                if (k>closestIndices[lastStopidx])
                    lastStopidx +=1
            }
            return pointsOutput
         */
        @JvmStatic
        fun splitPolyWhenDistanceTooBig(points: List<LatLng>, distMax: Double): List<LatLng>{
            val outList = mutableListOf(points[0])
            var oldP = points[0]
            for(i in 1..<points.size){
                val newP = points[i]
                val d = oldP.distanceTo(points[i])
                if(d > distMax){
                    val newLat = (oldP.latitude+newP.latitude)/2
                    val newLong = (oldP.longitude+newP.longitude)/2
                    val extraP = LatLng(newLat,newLong)
                    outList.add(extraP)
                }
                outList.add(newP)

                oldP=newP
            }

            return outList
        }

        @JvmStatic
        fun findIndicesClosestPointsForStops(points:List<LatLng>, stops:List<Stop>): List<Int> {

            val closestIndices = stops.map { s->
                val p = LatLng(s.latitude!!, s.longitude!!)
                var dist = 10_000_000.0 // in meters
                var id = -1
                for (j in points.indices){
                    val newd = p.distanceTo(points[j])
                    if (newd<dist) {
                        id = j
                        dist = newd
                    }
                }
                if(id==-1) throw Error("The distance is bigger than 10_000_000")
                id
            }

            return closestIndices
        }

        @JvmStatic
        fun getBearing(from: LatLng, to: LatLng): Float {
            return TurfMeasurement.bearing(
                Point.fromLngLat(from.longitude, from.latitude),
                Point.fromLngLat(to.longitude, to.latitude)
            ).toFloat()
        }

    }



    //TODO: Do the same for LatLng and bearing, if possible
    class LatLngEvaluator : TypeEvaluator<LatLng> {
        private val latLng = LatLng()
        override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
            latLng.latitude = startValue.latitude + (endValue.latitude - startValue.latitude) * fraction
            latLng.longitude = startValue.longitude + (endValue.longitude - startValue.longitude) * fraction
            return latLng
        }
    }


}