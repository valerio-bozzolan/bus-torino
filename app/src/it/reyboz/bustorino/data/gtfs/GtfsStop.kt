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
import androidx.room.PrimaryKey

@Entity(tableName = GtfsStop.DB_TABLE)
data class GtfsStop(
    @PrimaryKey
    @ColumnInfo(name= COL_STOP_ID)
    val internalID: Int,
    @ColumnInfo(name= COL_STOP_CODE)
    val gttStopID: String,
    @ColumnInfo(name= COL_STOP_NAME)
    val stopName: String,
    @ColumnInfo(name= COL_GTT_PLACE)
    val gttPlaceName: String,
    @ColumnInfo(name= COL_LATITUDE)
    val latitude: Double,
    @ColumnInfo(name= COL_LONGITUDE)
    val longitude: Double,
    //@ColumnInfo(name="zone_id")
    //val zoneID: Int,
    @ColumnInfo(name= COL_WHEELCHAIR)
    val wheelchair: WheelchairAccess,
): GtfsTable {

    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_STOP_ID]?.toIntOrNull()!!,
        valuesByColumn[COL_STOP_CODE]!!,
        valuesByColumn[COL_STOP_NAME]!!,
        valuesByColumn[COL_GTT_PLACE]!!,
        valuesByColumn[COL_LATITUDE]?.toDoubleOrNull()!!,
        valuesByColumn[COL_LONGITUDE]?.toDoubleOrNull()!!,
        //valuesByColumn["zone_id"]?.toIntOrNull()!!,
        Converters.wheelchairFromString(valuesByColumn[COL_WHEELCHAIR])
    )
    companion object{
        const val DB_TABLE="stops_gtfs"
        const val COL_STOP_CODE="stop_code"
        const val COL_STOP_ID = "stop_id"
        const val COL_GTT_PLACE="stop_desc"
        const val COL_STOP_NAME="stop_name"
        const val COL_LATITUDE="stop_lat"
        const val COL_LONGITUDE="stop_lon"
        const val COL_WHEELCHAIR="wheelchair_boarding"
        val COLUMNS = arrayOf(
            COL_STOP_CODE,
            COL_STOP_ID,
            COL_GTT_PLACE,
            COL_STOP_NAME,
            COL_LATITUDE,
            COL_LONGITUDE,
            //"zone_id",
            COL_WHEELCHAIR
        )
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }

}
