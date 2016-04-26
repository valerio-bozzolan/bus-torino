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

    public static Route.Type decodeType(final String routename, final String bacino) {
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

    /**
     * Converts a route ID from internal format to display format, returns null if it has the same name.
     *
     * @param routeID ID in "internal" and normalized format
     * @return string with display name, null if unchanged
     */
    public static String routeInternalToDisplay(String routeID) {
        switch(routeID) {
            case "1C":
                return "1 Chieri";
            case "1N":
                return "1 Nichelino";
            case "OB1":
                return "1 Orbassano";
            case "2C":
                return "2 Chieri";
            case "RV2":
                return "2 Rivalta";
            case "5B":
                return "5 /";
            case "CO1":
                return "Circolare Collegno";
            case "79":
                return "Cremagliera Sassi-Superga";
            case "W01":
                return "Night Buster 1 arancio";
            case "N10":
                return "Night Buster 10 gialla";
            case "W15":
                return "Night Buster 15 rosa";
            case "S18":
                return "Night Buster 18 blu";
            case "S04":
                return "Night Buster 4 azzurra";
            case "N4":
                return "Night Buster 4 rossa";
            case "N57":
                return "Night Buster 57 oro";
            case "W60":
                return "Night Buster 60 argento";
            case "E68":
                return "Night Buster verde 68";
            case "S05":
                return "Night Buster viola 5";
            case "ST1":
                return "Star 1";
            case "ST2":
                return "Star 2";
            case "10N":
                return "10 navetta";
            case "17B":
                return "17 /";
            case "35N":
                return "35 navetta";
            case "36N":
                return "36 navetta";
            case "45B":
                return "45 /";
            case "46N":
                return "46 navetta";
            case "58B":
                return "58 /";
            case "59B":
                return "59 /";
            case "63B":
                return "63 /";
            case "72B":
                return "72 /";
            case "79B":
                return "79 /";
            case "93B":
                return "93 /";
            case "101":
                return "101 Metrobus";
            case "95B":
                return "95 /";
            default:
                return null;
        }
    }

}
