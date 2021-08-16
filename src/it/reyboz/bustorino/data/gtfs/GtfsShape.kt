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

@Entity(tableName = GtfsShape.DB_TABLE,
primaryKeys = [GtfsShape.COL_SHAPE_ID, GtfsShape.COL_POINT_SEQ])
data class GtfsShape(
    @ColumnInfo(name = COL_SHAPE_ID)
    val shapeID: String,
    @ColumnInfo(name = COL_POINT_LAT)
    val pointLat: Double,
    @ColumnInfo(name = COL_POINT_LON)
    val pointLon: Double,
    @ColumnInfo(name = COL_POINT_SEQ)
    val pointSequence: Int,
): GtfsTable {

    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_SHAPE_ID]!!,
        valuesByColumn[COL_POINT_LAT]?.toDoubleOrNull()!!,
        valuesByColumn[COL_POINT_LON]?.toDoubleOrNull()!!,
        valuesByColumn[COL_POINT_SEQ]?.toIntOrNull()!!
    )

    companion object{
        const val DB_TABLE="gtfs_shapes"
        const val COL_SHAPE_ID = "shape_id"
        const val COL_POINT_LAT="shape_pt_lat"
        const val COL_POINT_LON="shape_pt_lon"
        const val COL_POINT_SEQ="shape_pt_sequence"

        val COLUMNS= arrayOf(
            COL_SHAPE_ID,
            COL_POINT_LAT,
            COL_POINT_LON,
            COL_POINT_SEQ
        )
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
