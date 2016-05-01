/*
	BusTO ("backend" components)
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

package it.reyboz.bustorino.middleware;

/**
 * Holds some global data used by recursive methods to try every fetcher until you get some data.<br>
 * Well, those methods are now iterative but the name stuck...
 *
 * @param <FetcherKind> ArrivalsFetcher, StopsFinder, ...
 */
public class RecursionHelper<FetcherKind> {
    private int pos = 0;
    private final int len;
    private final FetcherKind[] fetchers;

    public RecursionHelper(FetcherKind[] fetchers) {
        this.fetchers = fetchers;
        this.len = fetchers.length;
    }

    /**
     * Go back to square one. Or zero since arrays are zero-indexed.
     */
    public void reset() {
        this.pos = 0;
    }

    /**
     * Can you give me a valid Fetcher without throwing exceptions?
     *
     * @return boolean
     */
    public boolean valid() {
        return this.pos < len;
    }

    /**
     * Give me a fetcher (use valid() to check that it exists BEFORE requesting it), move to next
     *
     * @return the fetcher\finder\whatever
     */
    public FetcherKind getAndMoveForward() {
        return fetchers[this.pos++];
    }
}