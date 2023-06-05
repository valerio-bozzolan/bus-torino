/*
	BusTO  - Backend components
    Copyright (C) 2023 Fabio Mazza

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
package it.reyboz.bustorino.backend.gtfs;

import androidx.core.util.Pair;
import it.reyboz.bustorino.backend.ServiceType;

abstract public class GtfsUtils {
    public static Pair<ServiceType, String> getRouteInfoFromGTFS(String routeID){
        String[] explo = routeID.split(":");
        //default is
        String toParse = routeID;
        if(explo.length>1) {
            toParse = explo[1];
        }
        ServiceType serviceType=ServiceType.UNKNOWN;
        final int length = toParse.length();
        final char v =toParse.charAt(length-1);
        switch (v){
            case 'E':
                serviceType = ServiceType.EXTRAURBANO;
                break;
            case 'F':
                serviceType = ServiceType.FERROVIA;
                break;
            case 'T':
                serviceType = ServiceType.TURISTICO;
                break;
            case 'U':
                serviceType=ServiceType.URBANO;
        }
        //boolean barrato=false;
        String num = toParse.substring(0, length-1);
        /*if(toParse.charAt(length-2)=='B'){
            //is barrato
            barrato = true;
            num = toParse.substring(0,length-2)+" /";
        }else {
            num = toParse.substring(0,length-1);
        }*/
        return new Pair<>(serviceType,num);
    }

    public static String getLineNameFromGtfsID(String routeID){
        return getRouteInfoFromGTFS(routeID).second;
    }
}
