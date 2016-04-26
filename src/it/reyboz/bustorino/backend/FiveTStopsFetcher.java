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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Once was asynchronous BusStop[] fetcher from a query, code mostly taken from
 * AsyncWgetBusStopSuggestions (by Valerio Bozzolan)
 *
 * @see FiveTScraperFetcher
 */
public class FiveTStopsFetcher implements StopsFinderByName {

    @Override
    public List<Stop> FindByName(String name, AtomicReference<result> res) {
        // TODO: limit?
        ArrayList<Stop> busStops = new ArrayList<>(15);
        String stopID;
        String stopName;
        String stopLocation;
        //Stop busStop;

        if(name.length() < 3) {
            res.set(result.QUERY_TOO_SHORT);
            return busStops;
        }

        String responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas;
        URL u;
        try {
            u = new URL("http://www.5t.torino.it/5t/trasporto/stop-lookup.jsp?action=search&stopShortName=" + URLEncoder.encode(name, "utf-8"));
        } catch(Exception e) {
            res.set(Fetcher.result.PARSER_ERROR);
            return busStops;
        }

        responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas = networkTools.getDOM(u, res);
        if (responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas == null) {
            // result already set in getDOM()
            return busStops;
        }

        Document doc = Jsoup.parse(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas);

        // Find bus stops
        Elements lis = doc.getElementsByTag("li");
        for(Element li : lis) {
            Elements spans = li.getElementsByTag("span");

            // busStopID
            try {
                stopID = FiveTNormalizer.FiveTNormalizeRoute(spans.eq(0).text());
            } catch(Exception e) {
                //Log.e("Suggestions", "Empty busStopID");
                stopID = "";
            }

            // busStopName
            try {
                stopName = spans.eq(1).text();
            } catch(Exception e) {
                //Log.e("Suggestions", "Empty busStopName");
                stopName = "";
            }

            // busStopLocation
            try {
                stopLocation = (spans.eq(2).text());
            } catch(Exception e) {
                //Log.e("Suggestions", "Empty busStopLocation");
                stopLocation = null;
            }

            busStops.add(new Stop(stopName, stopID, stopLocation, null));
        }

        if(busStops.size() == 0) {
            res.set(result.EMPTY_RESULT_SET);
        } else {
            res.set(result.OK);
        }

        Collections.sort(busStops);

        return busStops;
    }
}
