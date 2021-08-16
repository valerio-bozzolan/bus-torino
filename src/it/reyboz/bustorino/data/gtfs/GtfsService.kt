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
import java.util.Date

@Entity(tableName = GtfsService.DB_TABLE)
data class GtfsService(
    @PrimaryKey
    @ColumnInfo(name = COL_SERVICE_ID)
    val serviceID: String,
    @ColumnInfo(name = COL_MONDAY)
    val onMonday: Boolean,
    @ColumnInfo(name = COL_TUESDAY)
    val onTuesday: Boolean,
    @ColumnInfo(name = COL_WEDNESDAY)
    val onWednesday: Boolean,
    @ColumnInfo(name = COL_THURSDAY)
    val onThursday: Boolean,
    @ColumnInfo(name = COL_FRIDAY)
    val onFriday: Boolean,
    @ColumnInfo(name = COL_SATURDAY)
    val onSaturday: Boolean,
    @ColumnInfo(name = COL_SUNDAY)
    val onSunday: Boolean,
    @ColumnInfo(name = COL_START_DATE)
    val startDate: Date,
    @ColumnInfo(name = COL_END_DATE)
    val endDate: Date,
): GtfsTable {

    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_SERVICE_ID]!!,
        Converters.fromStringNum(valuesByColumn[COL_MONDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_TUESDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_WEDNESDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_THURSDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_FRIDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_SATURDAY])!!,
        Converters.fromStringNum(valuesByColumn[COL_SUNDAY])!!,
        Converters.dateFromFmtString(valuesByColumn[COL_START_DATE])!!,
        Converters.dateFromFmtString(valuesByColumn[COL_END_DATE])!!
    )
    companion object{
        const val DB_TABLE="gtfs_calendar"
        const val COL_SERVICE_ID="service_id"
        const val COL_MONDAY="monday"
        const val COL_TUESDAY="tuesday"
        const val COL_WEDNESDAY="wednesday"
        const val COL_THURSDAY="thursday"
        const val COL_FRIDAY="friday"
        const val COL_SATURDAY="saturday"
        const val COL_SUNDAY="sunday"
        const val COL_START_DATE="start_date"

        const val COL_END_DATE="end_date"

        val COLUMNS = arrayOf(
            COL_SERVICE_ID,
            COL_MONDAY,
            COL_TUESDAY,
            COL_WEDNESDAY,
            COL_THURSDAY,
            COL_FRIDAY,
            COL_SATURDAY,
            COL_SUNDAY,
            COL_START_DATE
        )
    }



    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
