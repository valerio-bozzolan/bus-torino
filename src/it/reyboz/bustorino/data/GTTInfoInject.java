/*
	BusTO - Data components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino.data;

import java.util.Locale;

/**
 * We need this class as !!TEMPORARY!! fix
 * To supply data which is not available by the GTT APIs anymore
 */
public abstract class GTTInfoInject {

    public static String findIDWhenMissingByName(String stopName){
        String stringSwitch = stopName.toUpperCase(Locale.ROOT).trim();
        //if (stringSwitch.contains("METRO")){
        String finalID;
        switch (stringSwitch){
            case "METRO FERMI":
                finalID="8210";
                break;
            case "METRO PARADISO":
                finalID="8211";
                break;
            case "METRO MARCHE":
                finalID="8212";
                break;
            case "METRO MASSAUA":
                finalID="8213";
                break;
            case "METRO POZZO STRADA":
                finalID="8214";
                break;
            case "METRO MONTE GRAPPA":
                finalID="8215";
                break;
            case "METRO RIVOLI":
                finalID="8216";
                break;
            case "METRO RACCONIGI":
                finalID="8217";
                break;
            case "METRO BERNINI":
                finalID="8218";
                break;
            case "METRO PRINCIPI ACAJA":
                finalID="8219";
                break;
            case "METRO XVIII DICEMBRE":
                finalID="8220";
                break;
            case "METRO PORTA SUSA":
                finalID="8221";
                break;
            case "METRO VINZAGLIO":
                finalID="8222";
                break;
            case "METRO RE UMBERTO":
                finalID="8223";
                break;
            case "METRO PORTA NUOVA":
                finalID="8224";
                break;
            case "METRO MARCONI":
                finalID="8225";
                break;
            case "METRO NIZZA":
                finalID="8226";
                break;
            case "METRO DANTE":
                finalID="8227";
                break;
            case "METRO CARDUCCI":
                finalID="8228";
                break;
            case "METRO SPEZIA":
                finalID="8229";
                break;
            case "METRO LINGOTTO":
                finalID="8230";
                break;
            case "METRO ITALIA 61":
            case "METRO ITALIA61":
                finalID="8231";
                break;
            case "METRO BENGASI":
                finalID="8232";
                break;
            default:
                finalID="";
        }
        return finalID;
    }
}
