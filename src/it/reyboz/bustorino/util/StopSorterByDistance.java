/*
	BusTO (util)
    Copyright (C) 2019 Fabio Mazza

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
package it.reyboz.bustorino.util;

import android.location.Location;
import it.reyboz.bustorino.backend.Stop;

import java.util.Comparator;

public class StopSorterByDistance implements Comparator<Stop> {
    private final Location locToCompare;

    public StopSorterByDistance(Location locToCompare) {
        this.locToCompare = locToCompare;
    }

    @Override
    public int compare(Stop o1, Stop o2) {
        return (int) (o1.getDistanceFromLocation(locToCompare)-o2.getDistanceFromLocation(locToCompare));
    }
}
