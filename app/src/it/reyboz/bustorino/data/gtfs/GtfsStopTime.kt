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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = GtfsStopTime.DB_TABLE,
    primaryKeys = [GtfsStopTime.COL_TRIP_ID, GtfsStopTime.COL_STOP_ID],
    foreignKeys = [
        ForeignKey(entity = GtfsStop::class,
            parentColumns = [GtfsStop.COL_STOP_ID],
            childColumns = [GtfsStopTime.COL_STOP_ID],
            onDelete = GtfsDatabase.FOREIGNKEY_ONDELETE),
        ForeignKey(entity = GtfsTrip::class,
            parentColumns = [GtfsTrip.COL_TRIP_ID],
            childColumns = [GtfsStopTime.COL_TRIP_ID],
            onDelete = GtfsDatabase.FOREIGNKEY_ONDELETE),
    ],
    indices = [Index(GtfsStopTime.COL_STOP_ID)]
)
data class GtfsStopTime(
    @ColumnInfo(name= COL_TRIP_ID)
    val tripID: String,
    @ColumnInfo(name= COL_ARRIVAL_TIME)
    val arrivalTime: String,
    @ColumnInfo(name= COL_DEPARTURE_TIME)
    val departureTime:String,
    @ColumnInfo(name= COL_STOP_ID)
    val stopID: Int,
    @ColumnInfo(name= COL_STOP_SEQUENCE)
    val stopSequence: Int,
): GtfsTable {
    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_TRIP_ID]!!,
        valuesByColumn[COL_ARRIVAL_TIME]!!,
        valuesByColumn[COL_DEPARTURE_TIME]!!,
        valuesByColumn[COL_STOP_ID]?.toIntOrNull()!!,
        valuesByColumn[COL_STOP_SEQUENCE]?.toIntOrNull()!!
    )
    companion object{
        const val DB_TABLE="gtfs_stop_times"
        const val COL_TRIP_ID="trip_id"
        const val COL_ARRIVAL_TIME="arrival_time"
        const val COL_DEPARTURE_TIME="departure_time"
        const val COL_STOP_ID="stop_id"
        const val COL_STOP_SEQUENCE="stop_sequence"

        val COLUMNS = arrayOf(
            COL_TRIP_ID,
            COL_ARRIVAL_TIME,
            COL_DEPARTURE_TIME,
            COL_STOP_ID,
            COL_STOP_SEQUENCE
        )
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
