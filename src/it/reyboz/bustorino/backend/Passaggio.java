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

import android.support.annotation.NonNull;

public final class Passaggio implements Comparable<Passaggio> {
    public final String hh;
    public final String mm;
    public final boolean isInRealTime;

    /**
     * Constructs a time (passaggio) for the timetable.
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*"), already trimmed from whitespace.
     * @throws IllegalArgumentException if nothing reasonable can be extracted from the string
     */
    public Passaggio(@NonNull String TimeGTT) {
        String[] parts = TimeGTT.split(":");
        if(parts.length != 2) {
            //throw new IllegalArgumentException("The string " + TimeGTT + " doesn't follow the sacred format of time according to GTT!");
            this.hh = "??";
            this.mm = "??";
            this.isInRealTime = false;
            return;
        }
        this.hh = parts[0];
        if(parts[1].endsWith("*")) {
            this.mm = parts[1].substring(0, parts[1].length() - 1);
            this.isInRealTime = true;
        } else {
            this.mm = parts[1];
            this.isInRealTime = false;
        }
    }

    @Override public int compareTo(@NonNull Passaggio altro) {
        int diff;

        diff = failsafeParseInt(altro.hh) - failsafeParseInt(this.hh);

        // an attempt to correctly sortRoutes times around midnight (e.g. 23.59 should come before 00.01)
        if(diff > 12) { // TODO: see if this works in practice
            diff = -(24 - diff);
        } else if(diff < -12) {
            diff = -(diff + 24);
        }

        diff *= 60;

        diff += failsafeParseInt(altro.mm) - failsafeParseInt(this.mm);

        // we should take into account if one is in real time and the other isn't, shouldn't we?
        if(altro.isInRealTime) {
            ++diff;
        }
        if(this.isInRealTime) {
            --diff;
        }

        return diff;
    }

    @Override
    public String toString() {
        String resultString = (this.hh).concat(":").concat(this.mm);
        if(this.isInRealTime) {
            return resultString.concat("*");
        } else {
            return resultString;
        }
    }

    /**
     * Parses without blowing up.
     *
     * @param str the number as a string
     * @return the number as an integer
     */
    private static int failsafeParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch(NumberFormatException e) {
            return 0;
        }
    }
}