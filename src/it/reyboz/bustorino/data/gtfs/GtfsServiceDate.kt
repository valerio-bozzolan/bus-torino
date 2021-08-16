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
import java.util.Date


@Entity(
    tableName = GtfsServiceDate.DB_TABLE,
    primaryKeys = [GtfsServiceDate.COL_SERVICE_ID, GtfsServiceDate.COL_DATE],
    foreignKeys =  [ForeignKey(entity = GtfsService::class,
        parentColumns = [GtfsService.COL_SERVICE_ID],
        childColumns = [GtfsServiceDate.COL_SERVICE_ID],
        onDelete = GtfsDatabase.FOREIGNKEY_ONDELETE)]
)
data class GtfsServiceDate(
    @ColumnInfo(name= COL_SERVICE_ID)
    val serviceID: String,
    @ColumnInfo(name=COL_DATE)
    val date: Date,
    @ColumnInfo(name=COL_EXCEPTION)
    val exceptionType: ExceptionType,
): GtfsTable {
    companion object{
        const val DB_TABLE="gtfs_calendar_dates"
        const val COL_SERVICE_ID="service_id"
        const val COL_DATE="date"
        const val COL_EXCEPTION="exception_type"

        val COLUMNS = arrayOf(COL_SERVICE_ID, COL_DATE, COL_SERVICE_ID)

        val converter = Converters()

    }
    constructor(valuesByColumn: Map<String,String>) : this(
        valuesByColumn[COL_SERVICE_ID]!!,
        converter.fromString(valuesByColumn[COL_DATE])!!,
        valuesByColumn[COL_EXCEPTION]?.let { ExceptionType.getByValue(it.toInt()) }!!
    )
    enum class ExceptionType(val value: Int){
        ADDED(1),
        REMOVED(2);

        companion object {
            private val VALUES = values()
            fun getByValue(value: Int) = VALUES.firstOrNull { it.value == value }
        }
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}

