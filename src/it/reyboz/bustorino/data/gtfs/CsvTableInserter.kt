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

import android.content.Context
import android.util.Log
import java.util.ArrayList

class CsvTableInserter(
    val tableName: String, context: Context
) {
    private val database: GtfsDatabase = GtfsDatabase.getGtfsDatabase(context)
    private val dao: StaticGtfsDao = database.gtfsDao()

    private val elementsList: MutableList< in GtfsTable> = mutableListOf()

    fun addElement(csvLineElements: Map<String,String>) {

        when(tableName){
            "stops" ->
                elementsList.add(GtfsStop(csvLineElements))
            "routes" ->
                elementsList.add(GtfsRoute(csvLineElements))
            "calendar" ->
                elementsList.add(GtfsService(csvLineElements))
            "calendar_dates" ->
                elementsList.add(GtfsServiceDate(csvLineElements))
            "trips" ->
                elementsList.add(GtfsTrip(csvLineElements))
            "shapes" ->
                elementsList.add(GtfsShape(csvLineElements))
            "stop_times" ->
                elementsList.add(GtfsStopTime(csvLineElements))


        }
        if(elementsList.size >= MAX_ELEMENTS){
            //have to insert
            Log.d(DEBUG_TAG, "Inserting first batch of elements now, list size: "+elementsList.size)
            if (tableName == "routes")
                dao.insertRoutes(elementsList.filterIsInstance<GtfsRoute>())
            else
                insertDataInDatabase()

            elementsList.clear()

        }
    }
    fun insertDataInDatabase(){
        when(tableName){
            "stops" -> dao.updateStops(elementsList.filterIsInstance<GtfsStop>())
            "routes" -> dao.clearAndInsertRoutes(elementsList.filterIsInstance<GtfsRoute>())
            "calendar" -> dao.insertServices(elementsList.filterIsInstance<GtfsService>())
            "calendar_dates" -> dao.insertDates(elementsList.filterIsInstance<GtfsServiceDate>())
            "trips" -> dao.insertTrips(elementsList.filterIsInstance<GtfsTrip>())
            "stop_times"-> dao.insertStopTimes(elementsList.filterIsInstance<GtfsStopTime>())
            "shapes" -> dao.insertShapes(elementsList.filterIsInstance<GtfsShape>())

        }
    }

    companion object{
        val MAX_ELEMENTS = 5000

        val DEBUG_TAG="BusTO - TableInserter"
    }
}