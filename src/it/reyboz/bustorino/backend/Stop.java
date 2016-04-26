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

public class Stop implements Comparable<Stop> {
    public final @NonNull String ID;
    public final @NonNull String name;
    public final @Nullable String location;
    public final @Nullable Route.Type type;

    public Stop(final @NonNull String name, final @NonNull String ID, @Nullable final String location, @Nullable final Route.Type type) {
        this.ID = ID;
        this.name = name;
        this.location = (location != null && location.length() == 0) ? null : location;
        this.type = type;
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
