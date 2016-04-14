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

// "arrivals" è più usato di "transit" o simili, e chi sono io per mettermi a dibattere con gli inglesi?

public interface ArrivalsFetcher {
    /**
     * Reads arrival times from a (hopefully) real-time source, e.g. the GTT website.
     *
     * @param stopID stop ID, in normalized form.
     * @param routeID route ID, in normalized form.
     * @return arrival times
     * @see FiveTNormalizer
     */
    Palina ReadArrivalTimesLine(String stopID, String routeID);

    /**
     * Reads arrival times from a (hopefully) real-time source, e.g. the GTT website.
     *
     * @param routeID route ID, in normalized form.
     * @return arrival times
     * @see FiveTNormalizer
     */
    Palina ReadArrivalTimesAll(String routeID);
}
