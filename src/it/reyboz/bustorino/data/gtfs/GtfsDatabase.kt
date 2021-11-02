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
import androidx.room.*

@Database(
    entities = [
        GtfsServiceDate::class,
        GtfsStop::class,
        GtfsService::class,
        GtfsRoute::class,
        GtfsStopTime::class,
        GtfsTrip::class,
        GtfsShape::class],
    version = GtfsDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(Converters::class)
public abstract class GtfsDatabase : RoomDatabase() {

    abstract fun gtfsDao() : StaticGtfsDao

    companion object{
        @Volatile
        private var INSTANCE: GtfsDatabase? =null

        fun getGtfsDatabase(context: Context): GtfsDatabase{
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(context.applicationContext,
                GtfsDatabase::class.java,
                "gtfs_database").build()
                INSTANCE = instance
                instance
            }
        }

        const val VERSION = 1
        const val FOREIGNKEY_ONDELETE = ForeignKey.CASCADE
    }
}