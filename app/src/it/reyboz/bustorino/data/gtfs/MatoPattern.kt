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

import androidx.room.*
import it.reyboz.bustorino.backend.Stop

@Entity(tableName = MatoPattern.TABLE_NAME,
        foreignKeys = [
            ForeignKey(entity = GtfsRoute::class,
                    parentColumns = [GtfsRoute.COL_ROUTE_ID],
                    childColumns = [MatoPattern.COL_ROUTE_ID],
                    onDelete = ForeignKey.CASCADE,
            )
        ]
)
data class MatoPattern(
    @ColumnInfo(name= COL_NAME)
    val name: String,
    @ColumnInfo(name= COL_CODE)
    @PrimaryKey
    val code: String,
    @ColumnInfo(name= COL_SEMANTIC_HASH)
    val semanticHash: String,
    @ColumnInfo(name= COL_DIRECTION_ID)
    val directionId: Int,
    @ColumnInfo(name= COL_ROUTE_ID)
    val routeGtfsId: String,
    @ColumnInfo(name= COL_HEADSIGN)
    var headsign: String?,
    @ColumnInfo(name= COL_GEOMETRY_POLY)
    val patternGeometryPoly: String,
    @ColumnInfo(name= COL_GEOMETRY_LENGTH)
    val patternGeometryLength: Int,
    @Ignore
    val stopsGtfsIDs: ArrayList<String>

):GtfsTable{

    @Ignore
    val servingStops= ArrayList<Stop>(4)
    constructor(
        name: String, code:String,
        semanticHash: String, directionId: Int,
        routeGtfsId: String, headsign: String?,
        patternGeometryPoly: String, patternGeometryLength: Int
    ): this(name, code, semanticHash, directionId, routeGtfsId, headsign, patternGeometryPoly, patternGeometryLength, ArrayList<String>(4))

    companion object{
        const val TABLE_NAME="mato_patterns"

        const val COL_NAME="pattern_name"
        const val COL_CODE="pattern_code"
        const val COL_ROUTE_ID="pattern_route_id"
        const val COL_SEMANTIC_HASH="pattern_hash"
        const val COL_DIRECTION_ID="pattern_direction_id"
        const val COL_HEADSIGN="pattern_headsign"
        const val COL_GEOMETRY_POLY="pattern_polyline"
        const val COL_GEOMETRY_LENGTH="pattern_polylength"

        val COLUMNS = arrayOf(
            COL_NAME,
            COL_CODE,
            COL_ROUTE_ID,
            COL_SEMANTIC_HASH,
            COL_DIRECTION_ID,
            COL_HEADSIGN,
            COL_GEOMETRY_POLY,
            COL_GEOMETRY_LENGTH
        )
    }
    override fun getColumns(): Array<String> {
        return COLUMNS
    }
}

//DO NOT USE EMBEDDED!!! -> copies all data

@Entity(tableName=PatternStop.TABLE_NAME,
    primaryKeys = [
        PatternStop.COL_PATTERN_ID,
        PatternStop.COL_STOP_GTFS,
        PatternStop.COL_ORDER
    ],
    foreignKeys = [
        ForeignKey(entity = MatoPattern::class,
                parentColumns = [MatoPattern.COL_CODE],
                childColumns = [PatternStop.COL_PATTERN_ID],
                onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PatternStop(
    @ColumnInfo(name= COL_PATTERN_ID)
    val patternId: String,
    @ColumnInfo(name=COL_STOP_GTFS)
    val stopGtfsId: String,
    @ColumnInfo(name=COL_ORDER)
    val order: Int,
){
    companion object{
        const val TABLE_NAME="patterns_stops"

        const val COL_PATTERN_ID="pattern_gtfs_id"
        const val COL_STOP_GTFS="stop_gtfs_id"
        const val COL_ORDER="stop_order"
    }
}

data class MatoPatternWithStops(
    @Embedded val pattern: MatoPattern,
    @Relation(
        parentColumn = MatoPattern.COL_CODE,
        entityColumn = PatternStop.COL_PATTERN_ID,

        )
    var stopsIndices: List<PatternStop>)
    {


    init {
        stopsIndices = stopsIndices.sortedBy { p-> p.order }

    }
}