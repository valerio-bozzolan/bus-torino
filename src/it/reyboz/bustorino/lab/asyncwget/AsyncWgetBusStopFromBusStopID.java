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

import java.util.ArrayList;

import it.reyboz.bustorino.lab.GTTSiteSucker;

import static it.reyboz.bustorino.lab.GTTSiteSucker.grep;

import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;

/**
 * Asynchronous BusStop fetcher from a busStopID.
 *
 * @author Valerio Bozzolan
 */
public class AsyncWgetBusStopFromBusStopID extends AsyncWget {
    public final static int ERROR_NONE = 0;
    public final static int ERROR_EMPTY_DOM = 1;
    public final static int ERROR_DOM = 2;
    public final static int ERROR_NO_PASSAGES_OR_NO_BUS_STOP = 3;

    /**
     * Like Ajax
     *
     * @param busStopID
     */
    public AsyncWgetBusStopFromBusStopID(String busStopID) {
        super.execute(getURL(busStopID));
        ;
    }

    protected final String getURL(String busStopID) {
        return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName=" + getFilteredBusStopID(busStopID);
    }

    protected final String getEmergencyURL(String busStopID) {
        return "http://www.gtt.to.it/cms/percorari/arrivi?palina=" + getFilteredBusStopID(busStopID);
    }

    /**
     * I've sent many emails to the public email info@5t.torino.it to write down something like:
     * «YOUR SITE EXPLODE IF I USE **YOUR** BUS LINE IDs STARTING WITH ZERO!!!!!»
     * So, waiting for a response, I must purge the busStopID from "0"s  .__.
     * IN YOUR FACE 5T/GTT. IN YOUR FACE.
     *
     * @param busStopID
     * @return parseInt(busStopID)
     * @antifeatured yep
     * @notabug yep
     * @wontfix yep
     */
    protected final String getFilteredBusStopID(String busStopID) {
        /*
         * OK leds me ezplain why 'm dong this shot of shittt. OK zo swhy?
         * Bhumm thads because the GTT/5T site-"developer" ids obviusli drunk.
         */
        String enableGTTDeveloperSimulator = "on"; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        final char ZZZZZZZEEEEROOOOOO = '0'; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        char[] cinquettiBarraGtt = busStopID.toCharArray(); // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        int merda = 0; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        while (merda < cinquettiBarraGtt.length && cinquettiBarraGtt[merda] == ZZZZZZZEEEEROOOOOO) {
            // COMPLETELELELLELEEELY DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
            Log.i("AsyncWgetBusStop", "scimmie ubriache assunte per tirar su il sito 5T/GTT"); // DR
            merda++; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        } // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        String trenoDiMerda = ""; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        for (; merda < cinquettiBarraGtt.length; merda++) { // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
            trenoDiMerda += cinquettiBarraGtt[merda]; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        } // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
        enableGTTDeveloperSimulator = "off"; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK

        return trenoDiMerda;
    }

    protected void onPostExecute(String responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas) {
        if (responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas == null) {
            onReceivedBusStop(null, ERROR_EMPTY_DOM);
            return;
        }

        Document doc = Jsoup.parse(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas);
        Element error = doc.select("p.errore").first();
        if (error != null) {
            onReceivedBusStop(null, ERROR_NO_PASSAGES_OR_NO_BUS_STOP);
            return;
        }

        Element span = doc.select("span").first();
        String busStopID = grep("^(.+)&nbsp;", span.html());
        if (busStopID == null) {
            Log.e("BusStop", "Empty busStopID from " + span.html());
            onReceivedBusStop(null, ERROR_DOM);
            return;
        }

        String busStopName = grep("^.+&nbsp;(.+)", span.html()); // The first "dot" is the single strange space character in the middle of "39{HERE→} {←HERE}PORTA NUOVA"
        if (busStopName == null) {
            Log.e("BusStop", "Empty busStopName from " + span.html());
            onReceivedBusStop(null, ERROR_DOM);
            return;
        }
        busStopName.trim();

        ArrayList<GTTSiteSucker.BusLine> busLines = new ArrayList<GTTSiteSucker.BusLine>();

        // Every table row is a busLine
        Elements trs = doc.select("table tr");
        for (Element tr : trs) {
            Element line = tr.select("td.line a").first();
            if (!line.hasText()) {
                onReceivedBusStop(null, ERROR_DOM);
                return;
            }

            String busLineName = line.text();
            Integer busLineID = Integer.parseInt(
                    grep(
                            "([0-9]+)$",
                            line.attr("href")
                    )
            );
            if (busLineName == null || busLineID == null) {
                onReceivedBusStop(null, ERROR_DOM);
                return;
            }

            GTTSiteSucker.BusLine busLine = new GTTSiteSucker.BusLine(busLineID, busLineName);

            // Every busLine have passages
            Elements tds = tr.select("td:not(.line)");
            for (Element td : tds) {
                boolean isInRealTime = td.select("i").size() > 0;

                td.select("i").remove(); // Stripping "*"
                String time = td.text().trim();
                if (time.equals("")) {
                    // Yes... Sometimes there is an EMPTY td ._.
                    continue;
                }
                busLine.addTimePassage(time, isInRealTime);
            }

            busLines.add(busLine);
        }
        onReceivedBusStop(
                new BusStop(
                        busStopID,
                        busStopName,
                        busLines.toArray(
                                new GTTSiteSucker.BusLine[busLines.size()]
                        )
                ),
                ERROR_NONE
        );
    }

    /**
     * Overload this!
     *
     * @param busStop The busStop
     * @param status  Error status if any
     */
    public void onReceivedBusStop(GTTSiteSucker.BusStop busStop, int status) {
        // Do something here
    }
}
