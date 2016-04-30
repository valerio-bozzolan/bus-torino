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

import java.util.List;

/**
 * No reference to SQLite whatsoever, here.
 * Don't get StopsDB inside the backend, use this interface instead.
 */
public interface StopsDBInterface {
    /**
     * Given a stop ID, get which routes stop there (as strings, there's no sane way to determine their destination\terminus from the database)
     *
     * @param stopID stop ID
     * @return list of routes or null if none
     */
    List<String> getRoutesByStop(String stopID);

    /**
     * Stop ID goes in, stop name comes out.
     * GTT API doesn't return this useful piece of information, so here we go, get it from the database!
     *
     * @param stopID stop ID, in normalized form
     * @return stop name or null if not found
     */
    String getNameFromID(String stopID);
}