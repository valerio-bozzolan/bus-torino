/*
	BusTO - Data components
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
package it.reyboz.bustorino.data.gtfs

import androidx.room.*

@Entity(tableName = GtfsTrip.DB_TABLE,
    foreignKeys=[
        ForeignKey(entity = GtfsRoute::class,
            parentColumns = [GtfsRoute.COL_ROUTE_ID],
            childColumns = [GtfsTrip.COL_ROUTE_ID],
            onDelete = GtfsDatabase.FOREIGNKEY_ONDELETE),
        // The service_id: ID referencing calendar.service_id or calendar_dates.service_id
        /*
        ForeignKey(entity = GtfsService::class,
            parentColumns = [GtfsService.COL_SERVICE_ID],
            childColumns = [GtfsTrips.COL_SERVICE_ID],
            onDelete = GtfsDatabase.FOREIGNKEY_ONDELETE),
         */
    ],
    indices = [Index(GtfsTrip.COL_ROUTE_ID)]
)
data class GtfsTrip(
    @ColumnInfo(name = COL_ROUTE_ID )
    val routeID: String,
    @ColumnInfo(name = COL_SERVICE_ID)
    val serviceID: String,
    @PrimaryKey
    @ColumnInfo(name = COL_TRIP_ID)
    val tripID: String,
    @ColumnInfo(name = COL_HEADSIGN)
    val tripHeadsign: String,
    @ColumnInfo(name = COL_DIRECTION_ID)
    val directionID: Int,
    @ColumnInfo(name = COL_BLOCK_ID)
    val blockID: String,
    @ColumnInfo(name = COL_SHAPE_ID)
    val shapeID: String,
    @ColumnInfo(name = COL_WHEELCHAIR)
    val isWheelchairAccess: Boolean,
    @ColumnInfo(name = COL_LIMITED_R)
    val isLimitedRoute: Boolean,

): GtfsTable {

    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_ROUTE_ID]!!,
        valuesByColumn[COL_SERVICE_ID]!!,
        valuesByColumn[COL_TRIP_ID]!!,
        valuesByColumn[COL_HEADSIGN]!!,
        valuesByColumn[COL_DIRECTION_ID]?.toIntOrNull()?: 0,
        valuesByColumn[COL_BLOCK_ID]!!,
        valuesByColumn[COL_SHAPE_ID]!!,
        Converters.fromStringNum(valuesByColumn[COL_WHEELCHAIR], false),
        Converters.fromStringNum(valuesByColumn[COL_LIMITED_R], false)
    )

    companion object{
        const val DB_TABLE="gtfs_trips"
        const val COL_ROUTE_ID="route_id"
        const val COL_SERVICE_ID="service_id"
        const val COL_TRIP_ID = "trip_id"
        const val COL_HEADSIGN="trip_headsign"
        //const val COL_SHORT_NAME="trip_short_name",
        const val COL_DIRECTION_ID="direction_id"
        const val COL_BLOCK_ID="block_id"
        const val COL_SHAPE_ID = "shape_id"
        const val COL_WHEELCHAIR="wheelchair_accessible"
        const val COL_LIMITED_R="limited_route"

        val COLUMNS= arrayOf(
            COL_ROUTE_ID,
            COL_SERVICE_ID,
            COL_TRIP_ID,
            COL_HEADSIGN,
            COL_DIRECTION_ID,
            COL_BLOCK_ID,
            COL_SHAPE_ID,
            COL_WHEELCHAIR,
            COL_LIMITED_R
        )
        /*
        open fun fromContentValues(values: ContentValues) {
            val tripItem = GtfsTrips();
        }
         */
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}