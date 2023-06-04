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

data class GtfsPositionUpdate(
    val tripID: String,
    val startTime: String,
    val startDate: String,
    val routeID: String,

    val latitude: Float,
    val longitude: Float,
    val bearing: Float,

    val timestamp: Long,

    val vehicleInfo: VehicleInfo,
    val occupancyStatus: OccupancyStatus?,
    val scheduleRelationship: ScheduleRelationship?
){
    constructor(position: VehiclePosition) : this(
        position.trip.tripId,
        position.trip.startTime,
        position.trip.startDate,
        position.trip.routeId,
        position.position.latitude,
        position.position.longitude,
        position.position.bearing,
        position.timestamp,
        VehicleInfo(position.vehicle.id, position.vehicle.label),
        position.occupancyStatus,
        null
    )
 data class VehicleInfo(
     val id: String,
     val label:String
 )
}


