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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GTTStopsFetcher implements StopsFinderByName  {
    @Override @NonNull
    public List<Stop> FindByName(String name, AtomicReference<result> res) {
        URL url;
        List<Stop> s = new LinkedList<>();
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

                try {
                    localita = thisstop.getString("localita");
                    if(localita.equals("[MISSING]")) {
                        localita = null;
                    }
                } catch(JSONException e) {
                    localita = null;
                }

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

                s.add(new Stop(fullname, thisstop.getString("value"), localita, type));

            }
        } catch (JSONException e) {
            res.set(result.PARSER_ERROR);
            return s;
        }

        res.set(result.OK);
        return s;
    }

}
