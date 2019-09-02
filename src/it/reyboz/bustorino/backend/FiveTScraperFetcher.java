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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//import android.util.Log;

/**
 * Contains large chunks of code taken from the old GTTSiteSucker, AsyncWget and AsyncWgetBusStopFromBusStopID classes.<br>
 * <hr>
 * «BusTO, because sucking happens»<br>
 * <br>
 * @author Valerio Bozzolan
 */
public class FiveTScraperFetcher implements ArrivalsFetcher {
    /**
     * Execute regexes.
     *
     * @param needle Regex
     * @param haystack Entire string
     * @return Matched string
     */
    private static String grep(String needle, String haystack) {
        String matched = null;
        Matcher matcher = Pattern.compile(
                needle).matcher(haystack);
        if (matcher.find()) {
            matched = matcher.group(1);
        }
        return matched;
    }

    @Override
    public Palina ReadArrivalTimesAll(final String stopID, final AtomicReference<result> res) {
        Palina p = new Palina(stopID);
        int routeIndex;

        String responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas = null;
        try {
            responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas = networkTools.getDOM(new URL("http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName=" + URLEncoder.encode(stopID, "utf-8")), res);
        } catch (Exception e) {
            res.set(result.PARSER_ERROR);
        }
        if(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas == null) {
            // result already set in getDOM()
            return p;
        }

        Document doc = Jsoup.parse(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas);

        // Tried in rete Edisu (it does Man In The Middle... asd)
        Element span = doc.select("span").first();
        if(span == null) {
            res.set(result.SERVER_ERROR);
            return p;
        }

        String busStopID = grep("^(.+)&nbsp;", span.html());
        if (busStopID == null) {
            //Log.e("BusStop", "Empty busStopID from " + span.html());
            res.set(result.EMPTY_RESULT_SET);
            return p;
        }

        // this also appears when no stops are found, but that case has already been handled above
        Element error = doc.select("p.errore").first();
        if (error != null) {
            res.set(result.SERVER_ERROR);
            return p;
        }

        String busStopName = grep("^.+&nbsp;(.+)", span.html()); // The first "dot" is the single strange space character in the middle of "39{HERE→} {←HERE}PORTA NUOVA"
        if (busStopName == null) {
            //Log.e("BusStop", "Empty busStopName from " + span.html());
            res.set(result.SERVER_ERROR);
            return p;
        }
        p.setStopName(busStopName.trim());

        // Every table row is a busLine
        Elements trs = doc.select("table tr");
        for (Element tr : trs) {
            Element line = tr.select("td.line a").first();
            if (!line.hasText()) {
                res.set(result.SERVER_ERROR);
                return p;
            }

            String busLineName = line.text();
            // this is yet another ID, that has no known use so we can safely ignore it
//            Integer busLineID = string2Integer(
//                    grep(
//                            "([0-9]+)$",
//                            line.attr("href")
//                    )
//            );

            if (busLineName == null) {
                res.set(result.SERVER_ERROR);
                return p;
            }

            // this fetcher doesn't support railways and probably they've removed METRO too, but anyway...
            if(busLineName.equals("METRO")) {
                routeIndex = p.addRoute(busLineName, "", Route.Type.METRO);
            } else {
                if(busLineName.length() >= 4) {
                    boolean isExtraurbano = true;
                    for(int ch = 0; ch < busLineName.length(); ch++) {
                        if(!Character.isDigit(busLineName.charAt(ch))) {
                            isExtraurbano = false;
                            break;
                        }
                    }

                    if(isExtraurbano) {
                        routeIndex = p.addRoute(busLineName, "", Route.Type.LONG_DISTANCE_BUS);
                    } else {
                        routeIndex = p.addRoute(busLineName, "", Route.Type.BUS);
                    }
                } else {
                    routeIndex = p.addRoute(busLineName, "", Route.Type.BUS);
                }
            }

            // Every busLine have passages
            Elements tds = tr.select("td:not(.line)");
            for (Element td : tds) {
                //boolean isInRealTime = td.select("i").size() > 0;

                //td.select("i").remove(); // Stripping "*"
                String time = td.text().trim();
                if (time.equals("")) {
                    // Yes... Sometimes there is an EMPTY td ._.
                    continue;
                }
                p.addPassaggio(time, Passaggio.Source.FiveTScraper,routeIndex);
            }
        }

        p.sortRoutes();
        res.set(result.OK);
        return p;
    }

    // preserved for future generations:
//    /*
//     * I've sent many emails to the public email info@5t.torino.it to write down something like:
//     * «YOUR SITE EXPLODE IF I USE **YOUR** BUS LINE IDs STARTING WITH ZERO!!!!!»
//     * So, waiting for a response, I must purge the busStopID from "0"s  .__.
//     * IN YOUR FACE 5T/GTT. IN YOUR FACE.
//     *
//     * @param busStopID
//     * @return parseInt(busStopID)
//     * @antifeatured yep
//     * @notabug yep
//     * @wontfix yep
//     */
//    protected final String getFilteredBusStopID(String busStopID) {
//        /*
//         * OK leds me ezplain why 'm dong this shot of shittt. OK zo swhy?
//         * Bhumm thads because the GTT/5T site-"developer" ids obviusli drunk.
//         */
//        String enableGTTDeveloperSimulator = "on"; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        final char ZZZZZZZEEEEROOOOOO = '0'; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        char[] cinquettiBarraGtt = busStopID.toCharArray(); // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        int merda = 0; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        while (merda < cinquettiBarraGtt.length && cinquettiBarraGtt[merda] == ZZZZZZZEEEEROOOOOO) {
//            // COMPLETELELELLELEEELY DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//            Log.i("AsyncWgetBusStop", "scimmie ubriache assunte per tirar su il sito 5T/GTT"); // DR
//            merda++; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        } // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        String trenoDiMerda = ""; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        for (; merda < cinquettiBarraGtt.length; merda++) { // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//            trenoDiMerda += cinquettiBarraGtt[merda]; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        } // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//        enableGTTDeveloperSimulator = "off"; // DRUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUNK
//
//        return trenoDiMerda;
//    }
}
