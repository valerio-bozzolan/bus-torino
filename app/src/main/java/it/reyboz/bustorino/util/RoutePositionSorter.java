/*
	BusTO (util)
    Copyright (C) 2019 Fabio Mazza

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
package it.reyboz.bustorino.util;

import androidx.core.util.Pair;
import android.util.Log;

import it.reyboz.bustorino.backend.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RoutePositionSorter implements Comparator<RouteWithStop> {
    private final double latPos, longPos;
    public final double MINUTI_PER_METRO = 6.0/100; //v = 5km/h
    public final double DISTANCE_MULTIPLIER = 2./3;
    public RoutePositionSorter(double latitude, double longitude){
        latPos = latitude;
        longPos = longitude;
    }
    public RoutePositionSorter(GPSPoint position){
        this(position.getLatitude(), position.getLongitude());
    }

    @Override
    public int compare(RouteWithStop pair1, RouteWithStop pair2) throws NullPointerException{
        int delta = 0;
        final Stop stop1 = pair1.getStop(), stop2 = pair2.getStop();
        double dist1 = utils.measuredistanceBetween(latPos,longPos,
                stop1.getLatitude(),stop1.getLongitude());
        double dist2 = utils.measuredistanceBetween(latPos,longPos,
                stop2.getLatitude(),stop2.getLongitude());
        final List<Passaggio> passaggi1 = pair1.getRoute().passaggi,
                passaggi2 = pair2.getRoute().passaggi;
        if(passaggi1.isEmpty() || passaggi2.isEmpty()){
            Log.e("ArrivalsStopAdapter","Cannot compare: No arrivals in one of the stops");
        } else {
            Collections.sort(passaggi1);
            Collections.sort(passaggi2);
            /*int deltaOre = passaggi1.get(0).hh-passaggi2.get(0).hh;
            if(deltaOre>12)
                deltaOre -= 24;
            else if (deltaOre<-12)
                deltaOre  += 24;
            delta+=deltaOre*60 + passaggi1.get(0).mm-passaggi2.get(0).mm;

             */

            delta = (int) passaggi1.get(0).getDifferenceMinutes(passaggi2.get(0));
        }
        delta += (int)((dist1 -dist2)* MINUTI_PER_METRO * DISTANCE_MULTIPLIER);
        return delta;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RoutePositionSorter;
    }
}
