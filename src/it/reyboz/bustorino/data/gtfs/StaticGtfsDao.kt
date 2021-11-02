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

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StaticGtfsDao {
    @Query("SELECT * FROM "+GtfsRoute.DB_TABLE+" ORDER BY "+GtfsRoute.COL_SORT_ORDER)
    fun getAllRoutes() : LiveData<List<GtfsRoute>>

    @Query("SELECT "+GtfsTrip.COL_TRIP_ID+" FROM "+GtfsTrip.DB_TABLE)
    fun getAllTripsIDs() : List<String>

    @Query("SELECT "+GtfsStop.COL_STOP_ID+" FROM "+GtfsStop.DB_TABLE)
    fun getAllStopsIDs() : List<Int>

    @Query("SELECT * FROM "+GtfsStop.DB_TABLE+" WHERE "+GtfsStop.COL_STOP_CODE+" LIKE :queryID")
    fun getStopByStopID(queryID: String): LiveData<List<GtfsStop>>

    @Query("SELECT * FROM "+GtfsShape.DB_TABLE+
            " WHERE "+GtfsShape.COL_SHAPE_ID+" LIKE :shapeID"+
            " ORDER BY "+GtfsShape.COL_POINT_SEQ+ " ASC"
    )
    fun getShapeByID(shapeID: String) : LiveData<List<GtfsShape>>

    @Transaction
    fun clearAndInsertRoutes(routes: List<GtfsRoute>){
        deleteAllRoutes()
        insertRoutes(routes)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRoutes(users: List<GtfsRoute>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStops(stops: List<GtfsStop>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCalendarServices(services: List<GtfsService>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertShapes(shapes: List<GtfsShape>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDates(dates: List<GtfsServiceDate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertServices(services: List<GtfsService>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrips(trips: List<GtfsTrip>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStopTimes(stopTimes: List<GtfsStopTime>)

    @Query("DELETE FROM "+GtfsRoute.DB_TABLE)
    fun deleteAllRoutes()
    @Query("DELETE FROM "+GtfsStop.DB_TABLE)
    fun deleteAllStops()
    @Query("DELETE FROM "+GtfsTrip.DB_TABLE)
    fun deleteAllTrips()
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateShapes(shapes: List<GtfsShape>) : Int

    @Transaction
    fun updateAllStops(stops: List<GtfsStop>){
        deleteAllStops()
        insertStops(stops)
    }
    @Query("DELETE FROM "+GtfsStopTime.DB_TABLE)
    fun deleteAllStopTimes()
    @Query("DELETE FROM "+GtfsService.DB_TABLE)
    fun deleteAllServices()

}