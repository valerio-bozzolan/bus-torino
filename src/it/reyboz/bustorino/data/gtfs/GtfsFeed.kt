/*
	BusTO - Data components
    Copyright (C) 2022 Fabio Mazza

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

@Entity(tableName = GtfsFeed.TABLE_NAME)
data class GtfsFeed(
        @PrimaryKey
        @ColumnInfo(name = COL_GTFS_ID)
        val gtfsId: String,
): GtfsTable{
    constructor(valuesByColumn: Map<String,String>) : this(
            valuesByColumn[COL_GTFS_ID]!!,
    )

    companion object{
        const val TABLE_NAME="gtfs_feeds"

        const val COL_GTFS_ID="feed_id"


        val COLUMNS = arrayOf(
                COL_GTFS_ID,
        )
        const val CREATE_SQL =
                "CREATE TABLE $TABLE_NAME ( $COL_GTFS_ID  )"
    }

    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}
