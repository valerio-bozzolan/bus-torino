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

import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

public class Stop implements Comparable<Stop> {
    // remove "final" in case you need to set these from outside the parser\scrapers\fetchers
    public final @NonNull String ID;
    private @Nullable String name;
    public final @Nullable String location;
    public final @Nullable Route.Type type;
    private final @Nullable List<String> routesThatStopHere;
    private final @Nullable Double lat;
    private final @Nullable Double lon;

    // leave this non-final
    private @Nullable String routesThatStopHereString = null;

    /**
     * Hey, look, method overloading!
     */
    public Stop(final @Nullable String name, final @NonNull String ID, @Nullable final String location, @Nullable final Route.Type type, @Nullable final List<String> routesThatStopHere) {
        this.ID = ID;
        this.name = name;
        this.location = (location != null && location.length() == 0) ? null : location;
        this.type = type;
        this.routesThatStopHere = routesThatStopHere;
        this.lat = null;
        this.lon = null;
    }

    /**
     * Hey, look, method overloading!
     */
    public Stop(final @NonNull String ID) {
        this.ID = ID;
        this.name = null;
        this.location = null;
        this.type = null;
        this.routesThatStopHere = null;
        this.lat = null;
        this.lon = null;
    }

    /**
     * Constructor that sets EVERYTHING.
     */
    public Stop(@NonNull String ID, @Nullable String name, @Nullable String location, @Nullable Route.Type type, @Nullable List<String> routesThatStopHere, @Nullable Double lat, @Nullable Double lon) {
        this.ID = ID;
        this.name = name;
        this.location = location;
        this.type = type;
        this.routesThatStopHere = routesThatStopHere;
        this.lat = lat;
        this.lon = lon;
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

        if(this.name != null && other.name != null) {
            res = this.name.compareTo(other.name);
        }

        // and give up

        return res;
    }

    /**
     * Sets a name.
     *
     * @param name stop name as string (not null)
     */
    public final void setStopName(@NonNull String name) {
        this.name = name;
    }

    /**
     * Returns stop name.<br>
     * - empty string means "already searched everywhere, can't find it"<br>
     * - null means "didn't search, yet. Maybe you should try."<br>
     * - string means "here's the name.", obviously.<br>
     *
     * @return string if known, null if still unknown
     */
    public final @Nullable String getStopName() {
        return this.name;
    }

    public final @Nullable String getGeoURL() {
        if(this.lat == null || this.lon == null) {
            return null;
        }

        // Android documentation suggests US for machine readable output (use dot as decimal separator)
        return String.format(Locale.US, "geo:%f,%f", this.lon, this.lat);
    }

    public final @Nullable String getGeoURLWithAddress() {
        String url = getGeoURL();

        if(url == null) {
            return null;
        }

        if(this.location != null) {
            try {
                String addThis = "?q=".concat(URLEncoder.encode(this.location, "utf-8"));
                return url.concat(addThis);
            } catch (Exception ignored) {}
        }

        return url;
    }
}
