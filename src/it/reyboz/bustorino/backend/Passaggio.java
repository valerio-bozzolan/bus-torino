/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi

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

package it.reyboz.bustorino.backend;

import androidx.annotation.NonNull;
import android.util.Log;

public final class Passaggio implements Comparable<Passaggio> {

    private static final int UNKNOWN_TIME = -3;
    private static final String DEBUG_TAG = "BusTO-Passaggio";

    private final String passaggioGTT;
    public final int hh,mm;
    public final boolean isInRealTime;
    public final Source source;


    /**
     * Useless constructor.
     *
     * //@param TimeGTT time in GTT format (e.g. "11:22*"), already trimmed from whitespace.
     */
//    public Passaggio(@NonNull String TimeGTT) {
//        this.passaggio = TimeGTT;
//    }

    @Override
    public String toString() {
        return this.passaggioGTT;
    }


    /**
     * Constructs a time (passaggio) for the timetable.
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*"), already trimmed from whitespace.
     * @throws IllegalArgumentException if nothing reasonable can be extracted from the string
    */
    public Passaggio(@NonNull String TimeGTT, @NonNull Source sorgente) {
        passaggioGTT = TimeGTT;
        source = sorgente;
        String[] parts = TimeGTT.split(":");
        String hh,mm;
        boolean realtime;
        if(parts.length != 2) {
            //throw new IllegalArgumentException("The string " + TimeGTT + " doesn't follow the sacred format of time according to GTT!");
            Log.w(DEBUG_TAG,"The string " + TimeGTT + " doesn't follow the sacred format of time according to GTT!");
            this.hh = UNKNOWN_TIME;
            this.mm = UNKNOWN_TIME;
            this.isInRealTime = false;
            return;
        }
        hh = parts[0];
        if(parts[1].endsWith("*")) {
            mm = parts[1].substring(0, parts[1].length() - 1);
            realtime = true;
        } else {
            mm = parts[1];
            realtime = false;
        }
        int hour=-3,min=-3;
        try {
            hour = Integer.parseInt(hh);
            min = Integer.parseInt(mm);
        } catch (NumberFormatException ex){
            Log.w(DEBUG_TAG,"Cannot convert passaggio into hour and minutes");
            hour = UNKNOWN_TIME;
            min = UNKNOWN_TIME;
            realtime = false;
        } finally {
            this.hh = hour;
            this.mm = min;
            this.isInRealTime = realtime;
        }
    }

    public Passaggio(int hour, int minutes, boolean realtime, Source sorgente){
        this.hh = hour;
        this.mm = minutes;
        this.isInRealTime = realtime;
        this.source = sorgente;
        //Build the passaggio string
        StringBuilder sb = new StringBuilder();
        sb.append(hour).append(":").append(minutes);
        if(realtime) sb.append("*");
        this.passaggioGTT = sb.toString();
    }

    public static String createPassaggioGTT(String timeInput, boolean realtime){
        final String time = timeInput.trim();
        if(time.contains("*")){
            if(realtime) return time;
            else return time.substring(0,time.length()-1);
        } else{
            if(realtime) return time.concat("*");
            else return time;
        }
    }

    @Override
    public int compareTo(@NonNull Passaggio other) {
        if(this.hh ==  UNKNOWN_TIME || other.hh == UNKNOWN_TIME)
            return 0;
        else {
            int diff = this.hh - other.hh;
            // an attempt to correctly sort arrival times around midnight (e.g. 23.59 should come before 00.01)
            if (diff > 12) { // untested
                diff -= 24;
            } else if (diff < -12) {
                diff += 24;
            }

            diff *= 60;

            diff += this.mm - other.mm;

            // we should take into account if one is in real time and the other isn't, shouldn't we?
            if (other.isInRealTime) {
                ++diff;
            }
            if (this.isInRealTime) {
                --diff;
            }
            //TODO: separate Realtime and Non-Realtime, especially for the GTTJSONFetcher

            return diff;
        }
    }
//
//    @Override
//    public String toString() {
//        String resultString = (this.hh).concat(":").concat(this.mm);
//        if(this.isInRealTime) {
//            return resultString.concat("*");
//        } else {
//            return resultString;
//        }
//    }
    public enum Source{
        FiveTAPI,GTTJSON,FiveTScraper, UNDETERMINED
    }
}