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
package it.reyboz.bustorino.lab.asyncwget;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;

/**
 * Asynchronous BusStop[] fetcher from a query.
 *
 * @author Valerio Bozzolan
 */
public class AsyncWgetBusStopSuggestions extends AsyncWget {
    public final static int ERROR_NONE = 0;
    public final static int ERROR_EMPTY_DOM = 1;
    public final static int ERROR_DOM = 2;

    /**
     * Like Ajax
     *
     * @param query Part of the busStopName
     * @throws UnsupportedEncodingException
     */
    public AsyncWgetBusStopSuggestions(String query) throws UnsupportedEncodingException {
        super.execute(getURL(query));
    }

    protected final String getURL(String busStopName) throws UnsupportedEncodingException {
        return "http://www.5t.torino.it/5t/trasporto/stop-lookup.jsp?action=search&stopShortName=" + URLEncoder.encode(busStopName, "utf-8");
    }

    /**
     * See http://www.5t.torino.it/5t/trasporto/stop-lookup.jsp?action=search&stopShortName=carducci
     *
     * @param responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas
     * @Override
     */
    protected void onPostExecute(String responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas) {
        ArrayList<BusStop> busStops = new ArrayList<BusStop>();

        if (responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas == null) {
            onReceivedBusStopNames(null, ERROR_EMPTY_DOM);
            return;
        }

        Document doc = Jsoup.parse(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas);

        // Find bus stops
        Elements lis = doc.getElementsByTag("li");
        for(Element li : lis) {
            Elements spans = li.getElementsByTag("span");

            BusStop busStop;

            // busStopID
            try {
                busStop = new BusStop(spans.eq(0).text());
            } catch(Exception e) {
                Log.e("Suggestions", "Empty busStopID");
                onReceivedBusStopNames(null, ERROR_DOM);
                return;
            }

            // busStopName
            try {
                busStop.setBusStopName(spans.eq(1).text());
            } catch(Exception e) {
                Log.e("Suggestions", "Empty busStopName");
                onReceivedBusStopNames(null, ERROR_DOM);
                return;
            }

            // busStopLocation
            try {
                busStop.setBusStopLocality(spans.eq(2).text());
            } catch(Exception e) {
                Log.e("Suggestions", "Empty busStopLocation");
                onReceivedBusStopNames(null, ERROR_DOM);
                return;
            }

            busStops.add( busStop );
        }

        onReceivedBusStopNames(
            busStops.toArray(new BusStop[busStops.size()]),
            ERROR_NONE
        );
    }

    /**
     * Overload this!
     *
     * @param busStops Suggestions
     * @param status Error status if any
     */
    public void onReceivedBusStopNames(BusStop[] busStops, int status) {
        // Do something here
    }
}