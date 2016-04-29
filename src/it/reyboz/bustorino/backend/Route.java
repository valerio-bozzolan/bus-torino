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

import java.util.List;

public class Route implements Comparable<Route> {
    public final String name;
    public final String destinazione;
    public final List<Passaggio> passaggi;
    public final Type type;

    public enum Type { // "long distance" sono gli extraurbani.
        BUS, LONG_DISTANCE_BUS, METRO, RAILWAY, TRAM
    }

    /**
     * Constructor.
     *
     * @param name route ID
     * @param destinazione terminus\end of line
     * @param type bus, long distance bus, underground, and so on
     * @param passaggi timetable, a good choice is an ArrayList of size 6
     * @see Palina Palina.addRoute() method
     */
    public Route(String name, String destinazione, Type type, List<Passaggio> passaggi) {
        this.name = name;
        this.destinazione = destinazione;
        this.passaggi = passaggi;
        this.type = type;
    }

    /**
     * Exactly what it says on the tin.
     *
     * @return times from the timetable
     */
    public List<Passaggio> getPassaggi() {
        return this.passaggi;
    }

    /**
     * Adds a time (passaggio) to the timetable for this route
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*")
     */
     public void addPassaggio(String TimeGTT) {
         this.passaggi.add(new Passaggio(TimeGTT));
     }

    @Override
    public int compareTo(@NonNull Route other) {
        int res;
        int thisAsInt, otherAsInt;

        // sorting by numbers alone yields a far more "natural" result (36N goes before 2024, 95B next to 95, and the like)

        thisAsInt = networkTools.failsafeParseInt(this.name.replaceAll("[^0-9]", ""));
        otherAsInt = networkTools.failsafeParseInt(other.name.replaceAll("[^0-9]", ""));

        // compare.

        // numeric route IDs
        if(thisAsInt != 0 && otherAsInt != 0) {
            res = thisAsInt - otherAsInt;
            if(res != 0) {
                return res;
            }
        } else {
            // non-numeric
            res = this.name.compareTo(other.name);
            if (res != 0) {
                return res;
            }
        }

        // try comparing their destination

        res = this.destinazione.compareTo(other.destinazione);
        if(res != 0) {
            return res;
        }

        // probably useless, but... last attempt.

        if(this.type != other.type) {
            // ordinal() is evil or whatever, who cares.
            return this.type.ordinal() - other.type.ordinal();
        }

        return 0;
    }
}
