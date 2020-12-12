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
import java.util.concurrent.atomic.AtomicReference;

public class GTTJSONFetcher implements ArrivalsFetcher  {
    @Override @NonNull
    public Palina ReadArrivalTimesAll(String stopID, AtomicReference<result> res) {
        URL url;
        Palina p = new Palina(stopID);
        String routename;
        String bacino;
        String content;
        JSONArray json;
        int howManyRoutes, howManyPassaggi, i, j, pos; // il misto inglese-italiano è un po' ridicolo ma tanto vale...
        JSONObject thisroute;
        JSONArray passaggi;

        try {
            url = new URL("http://www.gtt.to.it/cms/index.php?option=com_gtt&task=palina.getTransitiOld&palina=" + URLEncoder.encode(stopID, "utf-8") + "&realtime=true");
        } catch (Exception e) {
            res.set(result.PARSER_ERROR);
            return p;
        }

        content = networkTools.queryURL(url, res);
        if(content == null) {
            return p;
        }

        try {
            json = new JSONArray(content);
        } catch(JSONException e) {
            res.set(result.PARSER_ERROR);
            return p;
        }

        try {
            // returns [{"PassaggiRT":[],"Passaggi":[]}] for non existing stops!
            json.getJSONObject(0).getString("Linea"); // if we can get this, then there's something useful in the array.
        } catch(JSONException e) {
            res.set(result.EMPTY_RESULT_SET);
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

                pos = p.addRoute(routename, thisroute.getString("Direzione"), FiveTNormalizer.decodeType(routename, bacino));

                passaggi = thisroute.getJSONArray("PassaggiRT");
                howManyPassaggi = passaggi.length();
                for(j = 0; j < howManyPassaggi; j++) {
                    String mPassaggio = passaggi.getString(j);
                    if (mPassaggio.contains("__")){
                        mPassaggio = mPassaggio.replace("_", "");
                    }
                    p.addPassaggio(mPassaggio.concat("*"), Passaggio.Source.GTTJSON, pos);
                }

                passaggi = thisroute.getJSONArray("PassaggiPR"); // now the non-real-time ones
                howManyPassaggi = passaggi.length();
                for(j = 0; j < howManyPassaggi; j++) {
                    p.addPassaggio(passaggi.getString(j), Passaggio.Source.GTTJSON, pos);
                }
            }
        } catch (JSONException e) {
            res.set(result.PARSER_ERROR);
            return p;
        }

        p.sortRoutes();
        res.set(result.OK);
        return p;
    }

    @Override
    public Passaggio.Source getSourceForFetcher() {
        return Passaggio.Source.GTTJSON;
    }

}
