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

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Class to convert values for objects into
 * the needed columns
 *
 * handled automatically by Room with TypeConverter
 */
class Converters {


    @TypeConverter
    fun fromString(value: String?): Date? {
        return dateFromFmtString(value)
    }

    @TypeConverter
    fun dateToString(date: Date?): String? {
        return date?.let { stringFormat.format(it)}
    }

    @TypeConverter
    fun exceptionToInt(type: GtfsServiceDate.ExceptionType?): Int? {
        return type?.value
    }
    @TypeConverter
    fun fromInt(value: Int?): GtfsServiceDate.ExceptionType? {
        return value?.let { GtfsServiceDate.ExceptionType.getByValue(it) }
    }

    companion object{
        const val DATE_FMT_STRING = "yyyyMMdd"
        val stringFormat = SimpleDateFormat(DATE_FMT_STRING, Locale.US)

        fun fromStringNum(string:  String?): Boolean?{
            string?.let { if (it.trim() == "1")
                return true
            else if(it.trim() == "0")
                return false
            else throw Exception("Cannot convert $string to numeric value") }
            return null

        }
        fun fromStringNum(string:  String?, defaultVal: Boolean): Boolean{
            string?.let { if (it.trim() == "1")
                return true
            else if(it.trim() == "0")
                return false
            else return defaultVal }
            return defaultVal

        }
        fun dateFromFmtString(value: String?): Date?{
            return value?.let {

                stringFormat.parse(it)
            }
        }

        fun wheelchairFromString(string:  String?): WheelchairAccess{
            string?.let { if (it.trim() == "1")
                return WheelchairAccess.SOMETIMES
            else if(it.trim() == "0")
                return WheelchairAccess.UNKNOWN
            else if(it.trim() == "2")
                return WheelchairAccess.IMPOSSIBLE
            else //throw Exception("Cannot convert $string to wheelchair access") }
            return WheelchairAccess.UNKNOWN

        }
            return WheelchairAccess.UNKNOWN
        }
        @TypeConverter
        fun wheelchairToInt(access: WheelchairAccess): Int{
           return  access.value;
        }
        @TypeConverter
        fun wheelchairFromInt(value: Int): WheelchairAccess {
            return  WheelchairAccess.getByValue(value)?: WheelchairAccess.UNKNOWN
        }
    }
}