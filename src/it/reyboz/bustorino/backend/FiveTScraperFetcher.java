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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
//import android.util.Log;

/**
 * Contains large chunks of code taken from the old GTTSiteSucker, AsyncWget and AsyncWgetBusStopFromBusStopID classes.<br>
 * <hr>
 * «BusTO, because sucking happens»<br>
 * <br>
 * @author Valerio Bozzolan
 */
public class FiveTScraperFetcher extends FiveTNormalizer implements ArrivalsFetcher {
    /**
     * Execute regexes.
     *
     * @param needle Regex
     * @param haystack Entire string
     * @return Matched string
     */
    public static String grep(String needle, String haystack) {
        String matched = null;
        Matcher matcher = Pattern.compile(
                needle).matcher(haystack);
        if (matcher.find()) {
            matched = matcher.group(1);
        }
        return matched;
    }

    /**
     * Javammerda! Lasciami null senza suicidarti!
     */
    public static Integer string2Integer(String str) {
        try {
            return Integer.parseInt(str);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private String getDOM(final String routeID, final AtomicReference<result> res) {
        //Log.d("asyncwget", "Catching URL in background: " + uri[0]);
        HttpURLConnection urlConnection;
        StringBuilder result = null;
        try {
            URL url = new URL("http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName=" + routeID);
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

    @Override
    public Palina ReadArrivalTimesAll(final String routeID, final AtomicReference<result> res) {
        Palina p = new Palina();
        int routeIndex;

        String responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas = getDOM(routeID, res);
        if(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas == null) {
            return p;
        }

        Document doc = Jsoup.parse(responseInDOMFormatBecause5THaveAbsolutelyNoIdeaWhatJSONWas);

        Element span = doc.select("span").first();
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

            if(busLineName.equals("METRO")) {
                routeIndex = p.addRoute(busLineName, "", Route.Type.METRO);
            } else {
                routeIndex = p.addRoute(busLineName, "", Route.Type.BUS);
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
                p.addPassaggio(time, routeIndex);
            }
        }

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
