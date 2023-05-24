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

@Entity(tableName=GtfsRoute.DB_TABLE)
data class GtfsRoute(
        @PrimaryKey @ColumnInfo(name = COL_ROUTE_ID)
    val gtfsId: String,
        @ColumnInfo(name = COL_AGENCY_ID)
    val agencyID: String,
        @ColumnInfo(name = "route_short_name")
    val shortName: String,
        @ColumnInfo(name = "route_long_name")
    val longName: String,
        @ColumnInfo(name = "route_desc")
    val description: String,
        @ColumnInfo(name = COL_MODE)
    val mode: GtfsMode,
    //@ColumnInfo(name ="route_url")
    //val url: String,
        @ColumnInfo(name = COL_COLOR)
    val color: String,
        @ColumnInfo(name = COL_TEXT_COLOR)
    val textColor: String,

): GtfsTable {

    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_ROUTE_ID]!!,
        valuesByColumn["agency_id"]!!,
        valuesByColumn["route_short_name"]!!,
        valuesByColumn["route_long_name"]!!,
        valuesByColumn["route_desc"]!!,
        valuesByColumn["route_type"]?.toInt()?.let { GtfsMode.getByValue(it) }!!,
        valuesByColumn[COL_COLOR]!!,
        valuesByColumn[COL_TEXT_COLOR]!!,
    )
    companion object {
        const val DB_TABLE: String="routes_table"
        const val COL_SORT_ORDER: String="route_sort_order"
        const val COL_AGENCY_ID = "agency_id"
        const val COL_ROUTE_ID = "route_id"
        const val COL_MODE ="route_mode"
        const val COL_COLOR="route_color"
        const val COL_TEXT_COLOR="route_text_color"

        val COLUMNS = arrayOf(COL_ROUTE_ID,
            COL_AGENCY_ID,
            "route_short_name",
            "route_long_name",
            "route_desc",
            "route_type",
            "route_color",
            "route_text_color",
            COL_SORT_ORDER
        )

        //const val CREATE_SQL = ""
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
