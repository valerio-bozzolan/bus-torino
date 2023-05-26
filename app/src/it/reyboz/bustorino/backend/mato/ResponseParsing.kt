/*
	BusTO  - Backend components
    Copyright (C) 2022 Fabio Mazza

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

import it.reyboz.bustorino.data.gtfs.*
import org.json.JSONObject

abstract class ResponseParsing{

    companion object{
        fun parseAgencyJSON(jsonObject: JSONObject): GtfsAgency {
            return GtfsAgency(
                jsonObject.getString("gtfsId"),
                jsonObject.getString("name"),
                jsonObject.getString("url"),
                jsonObject.getString("fareUrl"),
                jsonObject.getString("phone"),
                null
            )
        }

        /**
         * Parse a feed request json, containing the GTFS agencies it is served by
         */
        fun parseFeedJSON(jsonObject: JSONObject): Pair<GtfsFeed, ArrayList<GtfsAgency>> {

            val agencies = ArrayList<GtfsAgency>()
            val feed = GtfsFeed(jsonObject.getString("feedId"))
            val oo = jsonObject.getJSONArray("agencies")
            agencies.ensureCapacity(oo.length())
            for (i in 0 until oo.length()){
                val agObj = oo.getJSONObject(i)

                agencies.add(
                    GtfsAgency(
                        agObj.getString("gtfsId"),
                        agObj.getString("name"),
                        agObj.getString("url"),
                        agObj.getString("fareUrl"),
                        agObj.getString("phone"),
                        feed
                    )
                )
            }
            return Pair(feed, agencies)
        }

        fun parseRouteJSON(jsonObject: JSONObject): GtfsRoute {

            val agencyJSON = jsonObject.getJSONObject("agency")
            val agencyId = agencyJSON.getString("gtfsId")


            return GtfsRoute(
                jsonObject.getString("gtfsId"),
                agencyId,
                jsonObject.getString("shortName"),
                jsonObject.getString("longName"),
                jsonObject.getString("desc"),
                GtfsMode.getByValue(jsonObject.getInt("type"))!!,
                jsonObject.getString("color"),
                jsonObject.getString("textColor")

            )
        }

        /**
         * Parse a route pattern from the JSON response of the MaTO server
         */
        fun parseRoutePatternsStopsJSON(jsonObject: JSONObject) : ArrayList<MatoPattern>{
            val routeGtfsId = jsonObject.getString("gtfsId")

            val patternsJSON = jsonObject.getJSONArray("patterns")
            val patternsOut = ArrayList<MatoPattern>(patternsJSON.length())
            var mPatternJSON: JSONObject
            for(i in 0 until patternsJSON.length()){
                mPatternJSON = patternsJSON.getJSONObject(i)

                val stopsJSON = mPatternJSON.getJSONArray("stops")

                val stopsCodes = ArrayList<String>(stopsJSON.length())
                for(k in 0 until stopsJSON.length()){
                    stopsCodes.add(
                        stopsJSON.getJSONObject(k).getString("gtfsId")
                    )
                }

                val geometry = mPatternJSON.getJSONObject("patternGeometry")
                val numGeo = geometry.getInt("length")
                val polyline = geometry.getString("points")

                patternsOut.add(
                    MatoPattern(
                        mPatternJSON.getString("name"), mPatternJSON.getString("code"),
                        mPatternJSON.getString("semanticHash"), mPatternJSON.getInt("directionId"),
                        routeGtfsId,mPatternJSON.getString("headsign"), polyline, numGeo, stopsCodes
                    )
                )
            }
            return patternsOut
        }


    }
}