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
import android.support.annotation.Nullable;

import java.util.List;

public class Stop implements Comparable<Stop> {
    // remove "final" in case you need to set these from outside the parser\scrapers\fetchers
    public final @NonNull String ID;
    public @NonNull String name;
    public final @Nullable String location;
    public final @Nullable Route.Type type;
    private final @Nullable List<String> routesThatStopHere;

    // leave this non-final
    private @Nullable String routesThatStopHereString = null;

    /**
     * Hey, look, method overloading!
     */
    public Stop(final @NonNull String name, final @NonNull String ID, @Nullable final String location, @Nullable final Route.Type type, @Nullable final List<String> routesThatStopHere) {
        this.ID = ID;
        this.name = name;
        this.location = (location != null && location.length() == 0) ? null : location;
        this.type = type;
        this.routesThatStopHere = routesThatStopHere;
    }

    /**
     * Hey, look, method overloading!
     */
    public Stop(final @NonNull String ID) {
        this.ID = ID;
        this.name = "";
        this.location = null;
        this.type = null;
        this.routesThatStopHere = null;
    }

    public @Nullable String routesThatStopHereToString() {
        // M E M O I Z A T I O N
        if(this.routesThatStopHereString != null) {
            return this.routesThatStopHereString;
        }

        // no string yet? build it!
        return buildString();
    }
    private @Nullable String buildString() {
        // no routes => no string
        if(this.routesThatStopHere == null || this.routesThatStopHere.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int i, lenMinusOne = routesThatStopHere.size() - 1;

        for (i = 0; i < lenMinusOne; i++) {
            sb.append(routesThatStopHere.get(i)).append(", ");
        }

        // last one:
        sb.append(routesThatStopHere.get(i));

        this.routesThatStopHereString = sb.toString();

        return this.routesThatStopHereString;
    }

    @Override
    public int compareTo(@NonNull Stop other) {
        int res;
        int thisAsInt = networkTools.failsafeParseInt(this.ID);
        int otherAsInt = networkTools.failsafeParseInt(other.ID);

        // numeric stop IDs
        if(thisAsInt != 0 && otherAsInt != 0) {
            return thisAsInt - otherAsInt;
        } else {
            // non-numeric
            res = this.ID.compareTo(other.ID);
            if (res != 0) {
                return res;
            }
        }

        // try with name, then

        res = this.name.compareTo(other.name);

        // and give up

        return res;
    }
}
