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

import java.util.concurrent.atomic.AtomicInteger;

public interface Fetcher {
    /**
     * Status codes, convert to int with resultCodes.OK.ordinal() and the like.
     *
     * NULL: still haven't started
     * OK: got a response, parsed correctly, obtained some data
     * CLIENT_OFFLINE: can't connect to the internet
     * SERVER_OFFLINE: the server is down for maintenance (apparently sometimes happens to the 5T website and they put a message on the home page, that can be parsed to set this state)
     * SERVER_ERROR: the server replied anything other than HTTP 200, basically
     * PARSER_ERROR: the server replied something that can't be parsed, probably it's not the data we're looking for (e.g. "PHP: Fatal Error")
     * NO_RESULTS: like OK, but there's an empty array (no stops\routes\"passaggi" found)
     */
    enum resultCodes {
        NULL, OK, CLIENT_OFFLINE, SERVER_OFFLINE, SERVER_ERROR, PARSER_ERROR, NOT_FOUND, EMPTY_RESULT_SET
    }

    // moved to AsyncFetcher since shouldn't be reused on different runs
    /*
     * Reading this before doing anything is a bad idea, probably.
     */
    //AtomicInteger result = new AtomicInteger(resultCodes.NULL.ordinal()); // how does this even work in an interface is a mystery to me.
}
