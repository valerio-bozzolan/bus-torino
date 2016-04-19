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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class GTTJSONFetcher implements ArrivalsFetcher  {
    @Override
    public Palina ReadArrivalTimesRoute(String stopID, String routeID, AtomicInteger res) {
        return this.ReadArrivalTimesAll(routeID, res); // TODO: finish implementation
    }

    @Override
    public Palina ReadArrivalTimesAll(String routeID, AtomicInteger res) {
        HttpURLConnection urlConnection;
        URL url;
        Palina p = new Palina();
        JSONArray json;
        int howManyRoutes, howManyPassaggi, i, j, pos; // il misto inglese-italiano è un po' ridicolo ma tanto vale...
        JSONObject thisroute;
        JSONArray passaggi;

        try {
            url = new URL("http://www.gtt.to.it/cms/index.php?option=com_gtt&task=palina.getTransiti&palina=" + routeID + "&realtime=true");
        } catch (MalformedURLException e) {
            res.set(resultCodes.PARSER_ERROR.ordinal());
            return p;
        }

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(resultCodes.SERVER_OFFLINE.ordinal()); // TODO: can we assume this is CLIENT_OFFLINE?
            return p;
        }

        try {
            InputStream in = urlConnection.getInputStream();
            json = new JSONArray(streamToString(in));
        } catch (Exception e) {
            res.set(resultCodes.PARSER_ERROR.ordinal());
            return p;
        } finally {
            urlConnection.disconnect();
        }

        howManyRoutes = json.length();
        if(howManyRoutes == 0) {
            res.set(resultCodes.EMPTY_RESULT_SET.ordinal());
            return p;
        }

        try {
            // returns [{"PassaggiRT":[],"Passaggi":[]}] for non existing stops
            json.getJSONObject(0).getString("Linea"); // if we can get this, then there's something useful in the array.
        } catch(JSONException e) {
            res.set(resultCodes.EMPTY_RESULT_SET.ordinal()); // could also use NOT_FOUND
            return p;
        }

        try { // TODO: split this to reuse other part in ReadArrivalTimesRoute
            for(i = 0; i < howManyRoutes; i++) {
                thisroute = json.getJSONObject(i);
                pos = p.addRoute(thisroute.getString("Linea"), thisroute.getString("Direzione"));

                passaggi = thisroute.getJSONArray("PassaggiRT");
                howManyPassaggi = passaggi.length();
                for(j = 0; j < howManyPassaggi; i++) {
                    p.addPassaggio(passaggi.getString(j).concat("*"), pos);
                }

                passaggi = thisroute.getJSONArray("Passaggi"); // now the non-real-time ones
                howManyPassaggi = passaggi.length();
                for(j = 0; j < howManyPassaggi; i++) {
                    p.addPassaggio(passaggi.getString(j), pos);
                }
            }
        } catch (JSONException e) {
            res.set(resultCodes.PARSER_ERROR.ordinal());
            return p;
        }

        res.set(resultCodes.OK.ordinal());
        return p;
    }

    // https://stackoverflow.com/a/14585883
    private static String streamToString(InputStream is) {
        Scanner s = new Scanner(is); // TODO: test this
        return s.hasNext() ? s.next() : "";
    }

}
