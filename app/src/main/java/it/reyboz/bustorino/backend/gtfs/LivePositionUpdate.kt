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

import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import com.google.transit.realtime.GtfsRealtime.VehiclePosition.OccupancyStatus

data class LivePositionUpdate(
    val tripID: String, //tripID WITHOUT THE "gtt:" prefix
    val startTime: String?,
    val startDate: String?,
    val routeID: String,
    val vehicle: String,

    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,

    val timestamp: Long,

    val nextStop: String?,

    /*val vehicleInfo: VehicleInfo,
    val occupancyStatus: OccupancyStatus?,
    val scheduleRelationship: ScheduleRelationship?
     */
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
}


