/*
	BusTO  - Backend components
    Copyright (C) 2023 Fabio Mazza

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
package it.reyboz.bustorino.backend.gtfs

import com.google.transit.realtime.GtfsRealtime.VehiclePosition

data class LivePositionUpdate(
    val tripID: String, //tripID WITHOUT THE "gtt:" prefix
    val startTime: String?,
    val startDate: String?,
    val routeID: String,
    val vehicle: String,

    var latitude: Double,
    var longitude: Double,
    var bearing: Float?,
    //the timestamp IN SECONDS
    val timestamp: Long,

    val nextStop: String?,

    /*val vehicleInfo: VehicleInfo,
    val occupancyStatus: OccupancyStatus?,
    val scheduleRelationship: ScheduleRelationship?
     */
    //var tripInfo: TripAndPatternWithStops?,
){
    constructor(position: VehiclePosition) : this(
        position.trip.tripId,
        position.trip.startTime,
        position.trip.startDate,
        position.trip.routeId,
        position.vehicle.label,

        position.position.latitude.toDouble(),
        position.position.longitude.toDouble(),
        position.position.bearing,
        position.timestamp,
        null
    )
 /*data class VehicleInfo(
     val id: String,
     val label:String
 )

  */
    /*fun withNewPositionAndBearing(latitude: Double, longitude: Double, bearing: Float) =
        LivePositionUpdate(this.tripID, this.startTime, this.startTime,
            this.routeID, this.vehicle, latitude, longitude, bearing,
            this.timestamp,this.nextStop)

    fun withNewPosition(latitude: Double, longitude: Double) =
        LivePositionUpdate(this.tripID, this.startTime, this.startTime,
            this.routeID, this.vehicle, latitude, longitude, this.bearing,
            this.timestamp,this.nextStop)

     */
}


