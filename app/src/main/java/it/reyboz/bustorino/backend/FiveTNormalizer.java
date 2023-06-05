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

import android.util.Log;

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

//    public static String FiveTNormalizeStop(String StopID) {
//        StopID = FiveTNormalizeRoute(StopID);
//        // is this faster than a regex?
//        if (StopID.length() == 5 && StopID.startsWith("ST") && Character.isLetter(StopID.charAt(2)) && Character.isLetter(StopID.charAt(3)) && Character.isLetter(StopID.charAt(4))) {
//            switch (StopID) {
//                case "STFER":
//                    return "8210";
//                case "STPAR":
//                    return "8211";
//                case "STMAR":
//                    return "8212";
//                case "STMAS":
//                    return "8213";
//                case "STPOS":
//                    return "8214";
//                case "STMGR":
//                    return "8215";
//                case "STRIV":
//                    return "8216";
//                case "STRAC":
//                    return "8217";
//                case "STBER":
//                    return "8218";
//                case "STPDA":
//                    return "8219";
//                case "STDOD":
//                    return "8220";
//                case "STPSU":
//                    return "8221";
//                case "STVIN":
//                    return "8222";
//                case "STREU":
//                    return "8223";
//                case "STPNU":
//                    return "8224";
//                case "STMCI":
//                    return "8225";
//                case "STNIZ":
//                    return "8226";
//                case "STDAN":
//                    return "8227";
//                case "STCAR":
//                    return "8228";
//                case "STSPE":
//                    return "8229";
//                case "STLGO":
//                    return "8230";
//            }
//        }
//        return StopID;
//    }
//
//    public static String NormalizedToFiveT(final String StopID) {
//        if(StopID.startsWith("82") && StopID.length() == 4) {
//            switch (StopID) {
//                case "8230":
//                    return "STLGO";
//                case "8229":
//                    return "STSPE";
//                case "8228":
//                    return "STCAR";
//                case "8227":
//                    return "STDAN";
//                case "8226":
//                    return "STNIZ";
//                case "8225":
//                    return "STMCI";
//                case "8224":
//                    return "STPNU";
//                case "8223":
//                    return "STREU";
//                case "8222":
//                    return "STVIN";
//                case "8221":
//                    return "STPSU";
//                case "8220":
//                    return "STDOD";
//                case "8219":
//                    return "STPDA";
//                case "8218":
//                    return "STBER";
//                case "8217":
//                    return "STRAC";
//                case "8216":
//                    return "STRIV";
//                case "8215":
//                    return "STMGR";
//                case "8214":
//                    return "STPOS";
//                case "8213":
//                    return "STMAS";
//                case "8212":
//                    return "STMAR";
//                case "8211":
//                    return "STPAR";
//                case "8210":
//                    return "STFER";
//            }
//        }
//
//        return StopID;
//    }

    public static Route.Type decodeType(final String routename, final String bacino) {
        if(routename.equals("METRO")) {
            return Route.Type.METRO;
        } else if(routename.equals("79")) {
            return Route.Type.RAILWAY;
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
    public static String routeInternalToDisplay(final String routeID) {
        if(routeID.length() == 3 && routeID.charAt(2) == 'B') {
            return routeID.substring(0,2).concat("/");
        }

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
            case "CO1":
                return "Circolare Collegno";
            case "SE1":
                // I wonder why GTT calls this "SE1" while other absurd names have a human readable name too.
                return "1 Settimo";
            case "16CD":
                return "16 Circolare Destra";
            case "16CS":
                return "16 Circolare Sinistra";
            case "79":
                return "Cremagliera Sassi-Superga";
            case "W01":
                return "Night Buster 1 Arancio";
            case "N10":
                return "Night Buster 10 Gialla";
            case "W15":
                return "Night Buster 15 Rosa";
            case "S18":
                return "Night Buster 18 Blu";
            case "S04":
                return "Night Buster 4 Azzurra";
            case "N4":
                return "Night Buster 4 Rossa";
            case "N57":
                return "Night Buster 57 Oro";
            case "W60":
                return "Night Buster 60 Argento";
            case "E68":
                return "Night Buster 68 Verde";
            case "S05":
                return "Night Buster 5 Viola";
            case "ST1":
                return "Star 1";
            case "ST2":
                return "Star 2";
            case "4N":
                return "4 Navetta";
            case "10N":
                return "10 Navetta";
            case "13N":
                return "13 Navetta";
            case "35N":
                return "35 Navetta";
            case "36N":
                return "36 Navetta";
            case "36S":
                return "36 Speciale";
            case "38S":
                return "38 Speciale";
            case "44S":
                return "44 Scolastico";
            case "46N":
                return "46 Navetta";
            default:
                return null;
        }
    }

    public static String routeDisplayToInternal(String displayName){
        String name = displayName.trim();
        if(name.charAt(displayName.length()-1)=='/'){
            return displayName.replace(" ","").replace("/","B");
        }
        switch (name.toLowerCase()){
            //DEFAULT CASES
            case "star 1":
                return "ST1";
            case "star 2":
                return "ST2";
            case "night buster 1 arancio":
                return "W01";
            case "night buster 10 gialla":
                return "N10";
            case "night buster 15 rosa":
                return "W15";
            case "night buster 18 blu":
                return "S18";
            case "night buster 4 azzurra":
                return "S04";
            case "night buster 4 rossa":
                return "N4";
            case "night buster 57 oro":
                return "N57";
            case "night buster 60 argento":
                return "W60";
            case "night buster 68 verde":
                return "E68";
            case "night buster 5 viola":
                return "S05";
            case "1 nichelino":
                return "1N";
            case "1 chieri":
                return "1C";
            case "1 orbassano":
                return  "OB1";
            case "2 chieri":
                return "2C";
            case "2 rivalta":
                return "RV2";

            default:
               // return displayName.trim();
        }
        String[] arr = name.toLowerCase().split("\\s+");
        try {
            if (arr.length == 2 && arr[1].trim().equals("navetta") && Integer.decode(arr[0]) > 0)
                return arr[0].trim().concat("N");

        } catch (NumberFormatException e){
            //It's not "# navetta"
            Log.w("FivetNorm","checking number when it's not");
        }
        if(name.toLowerCase().contains("night buster")){
            if(name.toLowerCase().contains("viola"))
                return "S05";
            else if(name.toLowerCase().contains("verde"))
                return "E68";
        }
        //Everything failed, let's at least compact the the (probable) code
        return name.replace(" ","");
    }

}
