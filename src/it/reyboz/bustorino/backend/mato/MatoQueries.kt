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

class MatoQueries  {

    companion object{
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

        const val ALL_STOPS_BY_FEEDS="""
        query AllStops(${'$'}feeds: [String!]){
          stops(feeds: ${'$'}feeds) {
            
            lat
            lon
            gtfsId
            code
            name
            desc
            routes {
              gtfsId
              shortName
            }
          }
        }
        """
    }
}