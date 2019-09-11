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

public interface Fetcher {
    /**
     * Status codes.<br>
     *<br>
     * OK: got a response, parsed correctly, obtained some data<br>
     * CLIENT_OFFLINE: can't connect to the internet<br>
     * SERVER_ERROR: the server replied anything other than HTTP 200, basically<br>
     *              for 404 special constant (see @FiveTAPIFetcher)
     * PARSER_ERROR: the server replied something that can't be parsed, probably it's not the data we're looking for (e.g. "PHP: Fatal Error")<br>
     * EMPTY_RESULT_SET: the response is valid and indicates there are no stops\routes\"passaggi"\results for your query<br>
     * QUERY_TOO_SHORT: input more characters and retry.
     */
    enum result {
        OK, CLIENT_OFFLINE, SERVER_ERROR, SETUP_ERROR,PARSER_ERROR, EMPTY_RESULT_SET, QUERY_TOO_SHORT,SERVER_ERROR_404
    }
}
