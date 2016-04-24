/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

public abstract class networkTools {
    static String getDOM(final URL url, final AtomicReference<Fetcher.result> res) {
        //Log.d("asyncwget", "Catching URL in background: " + uri[0]);
        HttpURLConnection urlConnection;
        StringBuilder result = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(Fetcher.result.CLIENT_OFFLINE);
            return null;
        }

        try {
            InputStream in = new BufferedInputStream(
                    urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in));
            result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            //Log.e("asyncwget", e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        if (result == null) {
            res.set(Fetcher.result.PARSER_ERROR);
            return null;
        }

        res.set(Fetcher.result.SERVER_ERROR); // will be set to "OK" later, this is a safety net in case StringBuilder returns null, the website returns an HTTP 204 or something like that.
        return result.toString();
    }
}
