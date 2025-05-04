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

import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import it.reyboz.bustorino.util.LinesNameSorter;
import org.osmdroid.api.IGeoPoint;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Stop implements Comparable<Stop> {


    // remove "final" in case you need to set these from outside the parser\scrapers\fetchers
    public final @NonNull String ID;
    private @Nullable String name;
    private @Nullable String username;
    public @Nullable String location;
    public @Nullable Route.Type type;
    private @Nullable List<String> routesThatStopHere;
    private final @Nullable Double lat;
    private final @Nullable Double lon;

    // leave this non-final
    private @Nullable String routesThatStopHereString = null;
    private @Nullable String absurdGTTPlaceName = null;

    //
    public @Nullable String gtfsID = null;

    /**
     * Hey, look, method overloading!
     */
    public Stop(final @Nullable String name, final @NonNull String ID, @Nullable final String location, @Nullable final Route.Type type, @Nullable final List<String> routesThatStopHere) {
        this.ID = ID;
        this.name = name;
        this.username = null;
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
        this.username = null;
        this.location = null;
        this.type = null;
        this.routesThatStopHere = null;
        this.lat = null;
        this.lon = null;
    }

    /**
     * Constructor that sets EVERYTHING.
     */
    public Stop(@NonNull String ID, @Nullable String name, @Nullable String userName,
                @Nullable String location, @Nullable Route.Type type, @Nullable List<String> routesThatStopHere,
                @Nullable Double lat, @Nullable Double lon, @Nullable String gtfsID) {
        this.ID = ID;
        this.name = name;
        this.username = userName;
        this.location = location;
        this.type = type;
        this.routesThatStopHere = routesThatStopHere;
        this.lat = lat;
        this.lon = lon;
        this.gtfsID = gtfsID;
    }


    public @Nullable String routesThatStopHereToString() {
        // M E M O I Z A T I O N
        if(this.routesThatStopHereString != null) {
            return this.routesThatStopHereString;
        }

        // no string yet? build it!
        return buildRoutesString();
    }

    @Nullable
    public String getAbsurdGTTPlaceName() {
        return absurdGTTPlaceName;
    }

    public void setAbsurdGTTPlaceName(@NonNull String absurdGTTPlaceName) {
        this.absurdGTTPlaceName = absurdGTTPlaceName;
    }

    public void setRoutesThatStopHere(@Nullable List<String> routesThatStopHere) {
        this.routesThatStopHere = routesThatStopHere;
    }

    protected void setRoutesThatStopHereString(String routesStopping){
        this.routesThatStopHereString = routesStopping;
    }

    public int getNumRoutesStopping(){
        if(this.routesThatStopHere == null) {
            return 0;
        } else {
            return this.routesThatStopHere.size();
        }
    }

    @Nullable
    protected List<String> getRoutesThatStopHere(){
        return routesThatStopHere;
    }

    protected @Nullable String buildRoutesString() {
        // no routes => no string
        if(this.routesThatStopHere == null || this.routesThatStopHere.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        Collections.sort(routesThatStopHere,new LinesNameSorter());
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
     * Sets user name. Empty string is converted to null.
     *
     * @param name a string of non-zero length, or null
     */
    public final void setStopUserName(@Nullable String name) {
        if(name == null) {
            this.username = null;
        } else if(name.length() == 0) {
            this.username = null;
        } else {
            this.username = name;
        }
    }

    /**
     * Returns stop name or username (if set).<br>
     * - empty string means "already searched everywhere, can't find it"<br>
     * - null means "didn't search, yet. Maybe you should try."<br>
     * - string means "here's the name.", obviously.<br>
     *
     * @return string if known, null if still unknown
     */
    public final @Nullable String getStopDisplayName() {
        if(this.username == null) {
            return this.name;
        } else {
            return this.username;
        }
    }

    /**
     * Same as getStopDisplayName, only returns default name.<br>
     * I'd use an @see tag, but Android Studio is incapable of understanding that getStopDefaultName
     * refers to the method exactly above this one and not some arcane and esoteric unknown symbol.
     */
    public final @Nullable String getStopDefaultName() {
        return this.name;
    }

    /**
     * Same as getStopDisplayName, only returns user name.<br>
     * Also, never an empty string.
     */
    public final @Nullable String getStopUserName() {
        return this.username;
    }

    /**
     * Gets username and name from other stop if they exist, sets itself accordingly.
     *
     * @param other another Stop
     * @return did we actually set/change anything?
     */
    public final boolean mergeNameFrom(Stop other) {
        boolean ret = false;

        if(other.name != null) {
            if(this.name == null || !this.name.equals(other.name)) {
                this.name = other.name;
                ret = true;
            }
        }

        if(other.username != null) {
            if(this.username == null || !this.username.equals(other.username)) {
                this.username = other.username;
                ret = true;
            }
        }

        return ret;
    }

    public final @Nullable String getGeoURL() {
        if(this.lat == null || this.lon == null) {
            return null;
        }

        // Android documentation suggests US for machine readable output (use dot as decimal separator)
        return String.format(Locale.US, "geo:%f,%f", this.lat, this.lon);
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

    @Override
    public String toString(){
        return "id:"+ID+" { name: "+this.name+", lines: "+this.routesThatStopHereToString()+"}";
    }

    @Nullable
    public Double getLatitude() {
        return lat;
    }

    @Nullable
    public Double getLongitude() {
        return lon;
    }


    public Double getDistanceFromLocation(IGeoPoint loc){
        return getDistanceFromLocation(loc.getLatitude(), loc.getLongitude());
    }
    public Double getDistanceFromLocation(double latitude, double longitude){
        if(this.lat!=null && this.lon !=null)
            return utils.measuredistanceBetween(this.lat,this.lon,latitude, longitude);
        else return Double.POSITIVE_INFINITY;
    }

    public Bundle toBundle(Bundle bundle) {
        //Bundle bundle = new Bundle();
        if(bundle==null) return null;
        bundle.putString("ID", ID);
        bundle.putString("name", name);
        bundle.putString("username", username);
        bundle.putString("location", location);
        bundle.putString("type", (type != null) ? type.name() : null);
        bundle.putStringArrayList("routesThatStopHere", (routesThatStopHere != null) ? new ArrayList<>(routesThatStopHere) : null);
        if (lat != null) bundle.putDouble("lat", lat);
        if (lon != null) bundle.putDouble("lon", lon);
        if (gtfsID !=null) bundle.putString("gtfsID", gtfsID);
        return bundle;
    }

    public Bundle toBundle(){
        return toBundle(new Bundle());
    }

    @Nullable
    public static Stop fromBundle(Bundle bundle) {
        String ID = bundle.getString("ID");
        if (ID == null) return null; //throw new IllegalArgumentException("ID cannot be null");
        String name = bundle.getString("name");
        String username = bundle.getString("username");
        String location = bundle.getString("location");
        String typeStr = bundle.getString("type");
        Route.Type type = (typeStr != null) ? Route.Type.valueOf(typeStr) : null;
        List<String> routesThatStopHere = bundle.getStringArrayList("routesThatStopHere");
        Double lat = bundle.containsKey("lat") ? bundle.getDouble("lat") : null;
        Double lon = bundle.containsKey("lon") ? bundle.getDouble("lon") : null;
        String gtfsId = bundle.getString("gtfsID");

        return new Stop(ID, name, username, location, type, routesThatStopHere, lat, lon, gtfsId);
    }
}
