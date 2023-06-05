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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetcher interface to describe ways to get information on arrival times
 */
public interface ArrivalsFetcher extends Fetcher {
    /**
     * Reads arrival times from a (hopefully) real-time source, e.g. the GTT website.
     * Don't call this in UI thread!
     *
     * @param stopID stop ID, in normalized form.
     * @param res result code (will be set by this method)
     * @return arrival times
     * @see Result
     * @see FiveTNormalizer
     */
    Palina ReadArrivalTimesAll(String stopID, AtomicReference<Result> res);

    /**
     * Get the determined source for the Fetcher
     * @return the source of the arrival times
     */
    Passaggio.Source getSourceForFetcher();
}
