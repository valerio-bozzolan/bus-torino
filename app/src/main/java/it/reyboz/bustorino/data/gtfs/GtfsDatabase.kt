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
import androidx.room.*
import androidx.room.migration.Migration

@Database(
    entities = [
        GtfsFeed::class,
        GtfsAgency::class,
        GtfsServiceDate::class,
        GtfsStop::class,
        GtfsService::class,
        GtfsRoute::class,
        GtfsStopTime::class,
        GtfsTrip::class,
        GtfsShape::class,
        MatoPattern::class,
        PatternStop::class
               ],
    version = GtfsDatabase.VERSION,
    autoMigrations = [
        AutoMigration(from=2,to=3)
    ]
)
@TypeConverters(Converters::class)
abstract class GtfsDatabase : RoomDatabase() {

    abstract fun gtfsDao() : GtfsDBDao


    companion object{
        @Volatile
        private var INSTANCE: GtfsDatabase? =null

        const val DB_NAME="gtfs_database"

        fun getGtfsDatabase(context: Context): GtfsDatabase{
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(context.applicationContext,
                    GtfsDatabase::class.java,
                    DB_NAME)
                        .addMigrations(MIGRATION_1_2)
                        .build()
                INSTANCE = instance
                instance
            }
        }

        const val VERSION = 3
        const val FOREIGNKEY_ONDELETE = ForeignKey.CASCADE

        val MIGRATION_1_2 = Migration(1,2) {
            Log.d("BusTO-Database", "Upgrading from version 1 to version 2 the Room Database")
            //create table for feeds
            it.execSQL("CREATE TABLE IF NOT EXISTS `gtfs_feeds` (`feed_id` TEXT NOT NULL, PRIMARY KEY(`feed_id`))")
            //create table gtfs_agencies
            it.execSQL("CREATE TABLE IF NOT EXISTS `gtfs_agencies` (`gtfs_id` TEXT NOT NULL, `ag_name` TEXT NOT NULL, `ag_url` TEXT NOT NULL, `fare_url` TEXT, `phone` TEXT, `feed_id` TEXT, PRIMARY KEY(`gtfs_id`))")

            //recreate routes
            it.execSQL("DROP TABLE IF EXISTS `routes_table`")
            it.execSQL("CREATE TABLE IF NOT EXISTS `routes_table` (`route_id` TEXT NOT NULL, `agency_id` TEXT NOT NULL, `route_short_name` TEXT NOT NULL, `route_long_name` TEXT NOT NULL, `route_desc` TEXT NOT NULL, `route_mode` TEXT NOT NULL, `route_color` TEXT NOT NULL, `route_text_color` TEXT NOT NULL, PRIMARY KEY(`route_id`))")

            //create patterns and stops
            it.execSQL("CREATE TABLE IF NOT EXISTS `mato_patterns` (`pattern_name` TEXT NOT NULL, `pattern_code` TEXT NOT NULL, `pattern_hash` TEXT NOT NULL, `pattern_direction_id` INTEGER NOT NULL, `pattern_route_id` TEXT NOT NULL, `pattern_headsign` TEXT, `pattern_polyline` TEXT NOT NULL, `pattern_polylength` INTEGER NOT NULL, PRIMARY KEY(`pattern_code`), FOREIGN KEY(`pattern_route_id`) REFERENCES `routes_table`(`route_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            it.execSQL("CREATE TABLE IF NOT EXISTS `patterns_stops` (`pattern_gtfs_id` TEXT NOT NULL, `stop_gtfs_id` TEXT NOT NULL, `stop_order` INTEGER NOT NULL, PRIMARY KEY(`pattern_gtfs_id`, `stop_gtfs_id`, `stop_order`), FOREIGN KEY(`pattern_gtfs_id`) REFERENCES `mato_patterns`(`pattern_code`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        }


    }
}