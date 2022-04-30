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

class CsvTableInserter(
    val tableName: String, context: Context
) {
    private val database: GtfsDatabase = GtfsDatabase.getGtfsDatabase(context)
    private val databaseDao: GtfsDBDao = database.gtfsDao()

    private val elementsList: MutableList< in GtfsTable> = mutableListOf()

    private var stopsIDsPresent: HashSet<Int>? = null
    private var tripsIDsPresent: HashSet<String>? = null

    private var countInsert = 0
    init {
        if(tableName == "stop_times") {
            stopsIDsPresent = databaseDao.getAllStopsIDs().toHashSet()
            tripsIDsPresent = databaseDao.getAllTripsIDs().toHashSet()
            Log.d(DEBUG_TAG, "num stop IDs present: "+ stopsIDsPresent!!.size)
            Log.d(DEBUG_TAG, "num trips IDs present: "+ tripsIDsPresent!!.size)
        } else if(tableName == "routes"){
            databaseDao.deleteAllRoutes()
        }
    }

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
            "stop_times" -> {
                //filter stop
                val stopTime = GtfsStopTime(csvLineElements)
                /*
                val stopOk = //tripsIDsPresent?.contains(stopTime.tripID) == true
                    (stopsIDsPresent?.contains(stopTime.stopID) == true)// &&
                       // tripsIDsPresent?.contains(stopTime.tripID) == true)
                if (stopOk)
                  */
                    elementsList.add(stopTime)
            }


        }
        if(elementsList.size >= MAX_ELEMENTS){
            //have to insert

            if (tableName == "routes")
                databaseDao.insertRoutes(elementsList.filterIsInstance<GtfsRoute>())
            else
                insertDataInDatabase()

            elementsList.clear()

        }
    }
    private fun insertDataInDatabase(){
        //Log.d(DEBUG_TAG, "Inserting batch of elements now, list size: "+elementsList.size)
        countInsert += elementsList.size
        when(tableName){
            "stops" -> {
                databaseDao.insertStops(elementsList.filterIsInstance<GtfsStop>())
            }
            "routes" -> databaseDao.insertRoutes(elementsList.filterIsInstance<GtfsRoute>())
            "calendar" -> databaseDao.insertServices(elementsList.filterIsInstance<GtfsService>())
            "calendar_dates" -> databaseDao.insertDates(elementsList.filterIsInstance<GtfsServiceDate>())
            "trips" -> databaseDao.insertTrips(elementsList.filterIsInstance<GtfsTrip>())
            "stop_times"-> databaseDao.insertStopTimes(elementsList.filterIsInstance<GtfsStopTime>())
            "shapes" -> databaseDao.insertShapes(elementsList.filterIsInstance<GtfsShape>())

        }
        ///if(elementsList.size < MAX_ELEMENTS)
    }
    fun finishInsert(){
        insertDataInDatabase()
        Log.d(DEBUG_TAG, "Inserted $countInsert elements from $tableName")
    }

    companion object{
        const val MAX_ELEMENTS = 5000

        const val DEBUG_TAG="BusTO - TableInserter"
    }
}