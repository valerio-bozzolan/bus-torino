/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi
    Copyright (C) 2026 Fabio Mazza

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
package it.reyboz.bustorino.backend

import android.os.Parcel
import android.os.Parcelable
import android.util.Log

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

data class Passaggio(
    val arrivalTime: ZonedDateTime,
    val isInRealTime: Boolean,
    @JvmField
    val source: Source,
    val realtimeDifference: Int? = null,
) : Comparable<Passaggio>, Parcelable {
    private val passaggioGTT: String = arrivalTime.format(DATEFORMATTER) + (if (isInRealTime) "*" else "")

    override fun toString(): String {
        return this.passaggioGTT
    }


    /*override fun compareTo(other: Passaggio?): Int {
        if (this.hh == UNKNOWN_TIME || other.hh == UNKNOWN_TIME) return 0
        else {
            var diff = getMinutesDiff(other)

            // we should take into account if one is in real time and the other isn't, shouldn't we?
            if (other.isInRealTime) {
                diff += 2
            }
            if (this.isInRealTime) {
                diff -= 2
            }

            return diff
        }
    }

     */
    override fun compareTo(other: Passaggio): Int {
        //DO NOT PUT REAL TIME FIRST (PassaggiSorter exists for this reason)
        /*if (isInRealTime != other.isInRealTime) {
            return if (isInRealTime) -1 else 1
        }

         */
        return arrivalTime.compareTo(other.arrivalTime)
    }


    /*fun getMinutesDiff(other: Passaggio): Int {
        var diff = this.hh - other.hh
        // an attempt to correctly sort arrival times around midnight (e.g. 23.59 should come before 00.01)
        if (diff > 12) { // untested
            diff -= 24
        } else if (diff < -12) {
            diff += 24
        }

        diff *= 60

        diff += this.mm - other.mm
        return diff
    }

     */
    /**
     * Calculate difference in minutes, positive is this arrives after the other one, negative if it arrives before
     */
    fun getDifferenceMinutes(other: Passaggio): Long {
        val res = ChronoUnit.MINUTES.between(other.arrivalTime, this.arrivalTime)
        return res
    }


    enum class Source {
        FiveTAPI, GTTJSON, FiveTScraper, MatoAPI, UNDETERMINED
    }

    constructor(parcel: Parcel) : this(
        arrivalTime = ZonedDateTime.parse(
            parcel.readString(),
            DateTimeFormatter.ISO_ZONED_DATE_TIME
        ),
        isInRealTime = parcel.readByte() != 0.toByte(),
        source = Source.valueOf(parcel.readString()?: Source.UNDETERMINED.name) ?: Source.FiveTAPI,
        realtimeDifference = parcel.readValue(Int::class.java.classLoader) as? Int
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(arrivalTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
        parcel.writeByte(if (isInRealTime) 1 else 0)
        parcel.writeString(source.name)
        parcel.writeValue(realtimeDifference)
    }

    override fun describeContents(): Int = 0

    companion object {
        private val UNKNOWN_TIME = -3
        private const val DEBUG_TAG = "BusTO-Passaggio"

        @JvmField
        val CREATOR = object: Parcelable.Creator<Passaggio> {
            override fun createFromParcel(parcel: Parcel): Passaggio = Passaggio(parcel)
            override fun newArray(size: Int): Array<Passaggio?> = arrayOfNulls(size)
        }

        @JvmStatic
        private fun parseHourMin(hour: Int, minutes: Int, slackMin: Long = 30): ZonedDateTime {
            val zona = ZoneId.of("Europe/Rome")
            val timeNow = ZonedDateTime.now(zona)
            val newTime = LocalTime.of(hour, minutes)

            var possibleTime = ZonedDateTime.of(LocalDate.now(zona), newTime, zona)

            // Se è già passato (o è esattamente adesso e vuoi escluderlo), vado al giorno dopo
            if (possibleTime.isBefore(timeNow.minusMinutes(slackMin))) {
                possibleTime = possibleTime.plusDays(1)
            }

            return possibleTime
        }

        @JvmStatic
        private val DATEFORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        /**
         * Constructs a time (passaggio) for the timetable.
         *
         * @param TimeGTT time in GTT format (e.g. "11:22*"), already trimmed from whitespace.
         * @throws IllegalArgumentException if nothing reasonable can be extracted from the string
         */
        @JvmStatic
        fun newInstance(TimeGTT: String, sorgente: Source) : Passaggio? {
            val parts = TimeGTT.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val hh: String
            val mm: String
            var realtime: Boolean
            if (parts.size != 2) {
                //throw new IllegalArgumentException("The string " + TimeGTT + " doesn't follow the sacred format of time according to GTT!");
                Log.w(DEBUG_TAG, "The string $TimeGTT doesn't follow the sacred format of time according to GTT!")
                return null;
            }
            hh = parts[0]
            if (parts[1].endsWith("*")) {
                mm = parts[1].substring(0, parts[1].length - 1)
                realtime = true
            } else {
                mm = parts[1]
                realtime = false
            }
            var time: ZonedDateTime? = null
            try {
                val hour = hh.toInt()
                val min = mm.toInt()
                time = parseHourMin(hour, min)
            } catch (ex: Exception) {
                Log.w(DEBUG_TAG, "Cannot convert passaggio into hour and minutes:\n$ex")
                return null
            }
            return Passaggio(time, realtime, sorgente)
        }


        /**
         * General constructor for the case hour & minutes
         */
        @JvmStatic
        fun newInstance(hour: Int, minutes: Int, realtime: Boolean, sorgente: Source, realtimeDifference: Int?): Passaggio? {
            /*this.hh = hour
            this.mm = minutes
            this.isInRealTime = realtime
            if (!realtime) realtimeDifference = 0
            this.source = sorgente
            //Build the passaggio string
            val sb = StringBuilder()
            sb.append(hour).append(":").append(minutes)
            if (realtime) sb.append("*")
            this.passaggioGTT = sb.toString()

             */
            var time: ZonedDateTime? = null
            try{
                time = parseHourMin(hour, minutes)
            } catch (ex: Exception) {
                Log.e(DEBUG_TAG, "Cannot parse hour $hour and minutes:$minutes into time:\n$ex")
                return null
            }
            return Passaggio(time, realtime, sorgente, realtimeDifference)
        }

        @JvmStatic
        fun newInstance(numSeconds: Int, realtime: Boolean, timeDiffSeconds: Int, source: Source) : Passaggio? {
            var minutes: Int = numSeconds / 60
            var hours :Int = minutes / 60
            //this.hh = hours;
            minutes -= hours * 60
            hours %= 24
            var timeDiffMins:Int = timeDiffSeconds / 60
            /*
            this.
            this.isInRealTime = realtime
            this.source = source
            this.passaggioGTT = makePassaggioGTT(this.hh, this.mm, this.isInRealTime)

             */
            return newInstance(hours, minutes,realtime, source, timeDiffMins)
        }

        @JvmStatic
        fun createPassaggioGTTString(timeInput: String, realtime: Boolean): String {
            val time = timeInput.trim { it <= ' ' }
            if (time.contains("*")) {
                if (realtime) return time
                else return time.substring(0, time.length - 1)
            } else {
                if (realtime) return time + "*"
                else return time
            }
        }

        private fun makePassaggioGTT(hour: Int, minutes: Int, realtime: Boolean): String {
            val sb = StringBuilder()
            sb.append(String.format(Locale.ITALIAN, "%02d", hour)).append(":")
                .append(String.format(Locale.ITALIAN, "%02d", minutes))
            if (realtime) sb.append("*")
            return sb.toString()
        }


    }
}