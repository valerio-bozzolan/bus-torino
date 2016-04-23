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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class GTTJSONFetcher implements ArrivalsFetcher  {
//    @Override
//    public Palina ReadArrivalTimesRoute(String stopID, String routeID, AtomicReference<Fetcher.result> res) {
//        return this.ReadArrivalTimesAll(routeID, res); // TODO: implement this when it's needed (never?)
//    }

    @Override @NonNull
    public Palina ReadArrivalTimesAll(String routeID, AtomicReference<result> res) {
        URL url;
        Palina p = new Palina();
        String routename;
        String bacino;
        JSONArray json;
        int howManyRoutes, howManyPassaggi, i, j, pos; // il misto inglese-italiano è un po' ridicolo ma tanto vale...
        JSONObject thisroute;
        JSONArray passaggi;

        try {
            url = new URL("http://www.gtt.to.it/cms/index.php?option=com_gtt&task=palina.getTransiti&palina=" + routeID + "&realtime=true");
        } catch (MalformedURLException e) {
            res.set(result.PARSER_ERROR);
            return p;
        }

        json = queryURL(url, res);
        if(json == null) {
            return p;
        }

        howManyRoutes = json.length();
        if(howManyRoutes == 0) {
            res.set(result.EMPTY_RESULT_SET);
            return p;
        }

        try {
            for(i = 0; i < howManyRoutes; i++) {
                thisroute = json.getJSONObject(i);
                routename = thisroute.getString("Linea");
                try {
                    bacino = thisroute.getString("Bacino");
                } catch (JSONException ignored) { // if "Bacino" gets removed...
                    bacino = "U";
                }

                pos = p.addRoute(routename, thisroute.getString("Direzione"), decodeType(routename, bacino));

                /*
                 * Okay, this is just absurd.
                 * The underground always has 4 non-real-time timetable entries; however, the first
                 * two are old\stale\bogus, as they're in the past. The other two are exactly the
                 * same ones that appear on the screens in the stations.
                 */
                if(routename.equals("METRO")) {
                    j = 2;
                } else {
                    j = 0;
                }

                passaggi = thisroute.getJSONArray("PassaggiRT");
                howManyPassaggi = passaggi.length();
                for(; j < howManyPassaggi; j++) {
                    p.addPassaggio(passaggi.getString(j).concat("*"), pos);
                }

                passaggi = thisroute.getJSONArray("Passaggi"); // now the non-real-time ones
                howManyPassaggi = passaggi.length();
                for(; j < howManyPassaggi; j++) {
                    p.addPassaggio(passaggi.getString(j), pos);
                }
            }
        } catch (JSONException e) {
            res.set(result.PARSER_ERROR);
            return p;
        }

        res.set(result.OK);
        return p;
    }

    @Nullable
    private JSONArray queryURL(URL url, AtomicReference<result> res) {
        HttpURLConnection urlConnection;
        InputStream in = null;
        JSONArray json;

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(result.SERVER_ERROR); // TODO: can we assume this is CLIENT_OFFLINE?
            return null;
        }

        try {
            in = urlConnection.getInputStream();
            json = new JSONArray(streamToString(in));
        } catch (Exception e) {
            res.set(result.PARSER_ERROR);
            return null;
        } finally {
            try {
                if (in != null) {in.close();} // this is getting ureadable quite fast.
            } catch(Exception ignored) {}
            urlConnection.disconnect();
        }

        try {
            // returns [{"PassaggiRT":[],"Passaggi":[]}] for non existing stops!
            json.getJSONObject(0).getString("Linea"); // if we can get this, then there's something useful in the array.
        } catch(JSONException e) {
            res.set(result.EMPTY_RESULT_SET);
            return null;
        }

        return json;
    }

    // https://stackoverflow.com/a/5445161
    private static String streamToString(InputStream is) {
        Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static Route.Type decodeType(final String routename, final String bacino) {
        if(routename.equals("METRO")) {
            return Route.Type.METRO;
        }

        switch (bacino) {
            case "U":
                return Route.Type.BUS;
            case "F":
                return Route.Type.RAILWAY;
            case "E":
                return Route.Type.LONG_DISTANCE_BUS;
            default:
                return Route.Type.BUS;
        }


    }

}
