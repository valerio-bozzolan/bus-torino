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

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GTTStopsFetcher implements StopsFinderByName  {
    @Override @NonNull
    public List<Stop> FindByName(String name, AtomicReference<result> res) {
        URL url;
        // sorting an ArrayList should be faster than a LinkedList and the API is limited to 15 results
        List<Stop> s = new ArrayList<>(15);
        List<Stop> s2 = new ArrayList<>(15);
        String fullname;
        String content;
        String bacino;
        String localita;
        Route.Type type;
        JSONArray json;
        int howManyStops, i;
        JSONObject thisstop;

        if(name.length() < 3) {
            res.set(result.QUERY_TOO_SHORT);
            return s;
        }

        try {
            url = new URL("http://www.gtt.to.it/cms/components/com_gtt/views/palinejson/view.html.php?term=" + URLEncoder.encode(name, "utf-8"));
        } catch (Exception e) {
            res.set(result.PARSER_ERROR);
            return s;
        }

        content = networkTools.queryURL(url, res);
        if(content == null) {
            return s;
        }

        try {
            json = new JSONArray(content);
        } catch(JSONException e) {
            if(content.contains("[]")) {
                // when no results are found, server returns a PHP Warning and an empty array. In case they fix the warning, we're looking for the array.
                res.set(result.EMPTY_RESULT_SET);
            } else {
                res.set(result.PARSER_ERROR);
            }
            return s;
        }

        howManyStops = json.length();
        if(howManyStops == 0) {
            res.set(result.EMPTY_RESULT_SET);
            return s;
        }

        try {
            for(i = 0; i < howManyStops; i++) {
                thisstop = json.getJSONObject(i);
                fullname = thisstop.getString("data");
                String ID = thisstop.getString("value");

                try {
                    localita = thisstop.getString("localita");
                    if(localita.equals("[MISSING]")) {
                        localita = null;
                    }
                } catch(JSONException e) {
                    localita = null;
                }
                /*
                if(localita == null || localita.length() == 0) {
                    localita = db.getLocationFromID(ID);
                }
                //TODO: find località by ContentProvider
                */

                try {
                    bacino = thisstop.getString("bacino");
                } catch (JSONException ignored) {
                    bacino = "U";
                }

                if(fullname.startsWith("Metro ")) {
                    type = Route.Type.METRO;
                } else if(fullname.length() >= 6 && fullname.startsWith("S00")) {
                    type = Route.Type.RAILWAY;
                } else if(fullname.startsWith("ST")) {
                    type = Route.Type.RAILWAY;
                } else {
                    type = FiveTNormalizer.decodeType("", bacino);
                }
                //TODO: refactor using content provider
                s.add(new Stop(fullname, ID, localita, type,null));

            }
        } catch (JSONException e) {
            res.set(result.PARSER_ERROR);
            return s;
        }

        if(s.size() < 1) {
            // shouldn't happen but prevents the next part from catching fire
            res.set(result.EMPTY_RESULT_SET);
            return s;
        }

        Collections.sort(s);

        // the next loop won't work with less than 2 items
        if(s.size() < 2) {
            res.set(result.OK);
            return s;
        }

        /* There are some duplicate stops returned by this API.
         * Long distance buses have stop IDs with 5 digits. Always. They are zero-padded if there
         * aren't enough. E.g. stop 631 becomes 00631.
         *
         * Unfortunately you can't use padded stops to query any API.
         * Fortunately, unpadded stops return both normal and long distance bus timetables.
         * FiveTNormalizer is already removing padding (there may be some padded stops for which the
         * API doesn't return an unpadded equivalent), here we'll remove duplicates by skipping
         * padded stops, which also never have a location.
         *
         * I had to draw a finite state machine on a piece of paper to understand how to implement
         * this loop.
         */
        for(i = 1; i < howManyStops; ) {
            Stop current = s.get(i);
            Stop previous = s.get(i-1);

            // same stop: let's see which one to keep...
            if(current.ID.equals(previous.ID)) {
                if(previous.location == null) {
                    // previous one is useless: discard it, increment
                    i++;
                } else if(current.location == null) {
                    // this one is useless: add previous and skip one
                    s2.add(previous);
                    i += 2;
                } else {
                    // they aren't really identical: to err on the side of caution, keep them both.
                    s2.add(previous);
                    i++;
                }
            } else {
                // different: add previous, increment
                s2.add(previous);
                i++;
            }
        }

        // unless the last one was garbage (i would be howManyStops+1 in that case), add it
        if(i == howManyStops) {
            s2.add(s.get(i-1));
        }

        res.set(result.OK);
        return s2;
    }

}
