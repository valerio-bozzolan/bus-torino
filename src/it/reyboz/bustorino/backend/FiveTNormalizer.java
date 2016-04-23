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

/**
 * Converts some weird stop IDs found on the 5T website to the form used everywhere else (including GTT website).
 * <p/>
 * A stop ID in normalized form is:<br>
 * - a string containing a number without leading zeros<br>
 * - a string beginning with "ST" and then a number<br>
 * - whatever the GTT website uses.<br>
 * <p/>
 * A bus route ID in normalized form is:<br>
 * - a string containing a number and optionally: "B", "CS", "CD", "N", "S", "C" at the end<br>
 * - the string "METRO"<br>
 * - "ST1" or "ST2" (Star 1 and Star 2)<br>
 * - a string beginning with E, N, S or W, followed by two digits (with a leading zero)<br>
 * - "RV2", "OB1"<br>
 * - "CAN" or "FTC" (railway lines)<br>
 * - ...screw it, let's just hope all the websites and APIs return something sane as route IDs.<br>
 * <p/>
 * This class exists because Java doesn't support traits.<br>
 * <br>
 * Note: this class also just became useless, as 5T now uses the same format as GTT website.
 */
public abstract class FiveTNormalizer {
    public static String FiveTNormalizeRoute(String RouteID) {
        while (RouteID.startsWith("0")) {
            RouteID = RouteID.substring(1);
        }
        return RouteID;
    }

    public static String FiveTNormalizeStop(String StopID) {
        StopID = FiveTNormalizeRoute(StopID);
        // is this faster than a regex?
        if (StopID.length() == 5 && StopID.startsWith("ST") && Character.isLetter(StopID.charAt(2)) && Character.isLetter(StopID.charAt(3)) && Character.isLetter(StopID.charAt(4))) {
            switch (StopID) {
                case "STFER":
                    return "8210";
                case "STPAR":
                    return "8211";
                case "STMAR":
                    return "8212";
                case "STMAS":
                    return "8213";
                case "STPOS":
                    return "8214";
                case "STMGR":
                    return "8215";
                case "STRIV":
                    return "8216";
                case "STRAC":
                    return "8217";
                case "STBER":
                    return "8218";
                case "STPDA":
                    return "8219";
                case "STDOD":
                    return "8220";
                case "STPSU":
                    return "8221";
                case "STVIN":
                    return "8222";
                case "STREU":
                    return "8223";
                case "STPNU":
                    return "8224";
                case "STMCI":
                    return "8225";
                case "STNIZ":
                    return "8226";
                case "STDAN":
                    return "8227";
                case "STCAR":
                    return "8228";
                case "STSPE":
                    return "8229";
                case "STLGO":
                    return "8230";
            }
        }
        return StopID;
    }

    public static String NormalizedToFiveT(final String StopID) {
        if(StopID.startsWith("82") && StopID.length() == 4) {
            switch (StopID) {
                case "8230":
                    return "STLGO";
                case "8229":
                    return "STSPE";
                case "8228":
                    return "STCAR";
                case "8227":
                    return "STDAN";
                case "8226":
                    return "STNIZ";
                case "8225":
                    return "STMCI";
                case "8224":
                    return "STPNU";
                case "8223":
                    return "STREU";
                case "8222":
                    return "STVIN";
                case "8221":
                    return "STPSU";
                case "8220":
                    return "STDOD";
                case "8219":
                    return "STPDA";
                case "8218":
                    return "STBER";
                case "8217":
                    return "STRAC";
                case "8216":
                    return "STRIV";
                case "8215":
                    return "STMGR";
                case "8214":
                    return "STPOS";
                case "8213":
                    return "STMAS";
                case "8212":
                    return "STMAR";
                case "8211":
                    return "STPAR";
                case "8210":
                    return "STFER";
            }
        }

        return StopID;
    }

}
