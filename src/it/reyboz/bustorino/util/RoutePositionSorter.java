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

import android.location.Location;
import android.support.v4.util.Pair;
import android.util.Log;
import it.reyboz.bustorino.adapters.ArrivalsStopAdapter;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RoutePositionSorter implements Comparator<Pair<Stop, Route>> {
    private final Location loc;
    private final double minutialmetro = 6.0/100; //v = 5km/h
    private final double distancemultiplier = 2./3;
    public RoutePositionSorter(Location loc) {
        this.loc = loc;
    }

    @Override
    public int compare(Pair<Stop, Route> pair1, Pair<Stop, Route> pair2) throws NullPointerException{
        int delta = 0;
        final Stop stop1 = pair1.first, stop2 = pair2.first;
        double dist1 = utils.measuredistanceBetween(loc.getLatitude(),loc.getLongitude(),
                stop1.getLatitude(),stop1.getLongitude());
        double dist2 = utils.measuredistanceBetween(loc.getLatitude(),loc.getLongitude(),
                stop2.getLatitude(),stop2.getLongitude());
        final List<Passaggio> passaggi1 = pair1.second.passaggi,
                passaggi2 = pair2.second.passaggi;
        if(passaggi1.size()<=0 || passaggi2.size()<=0){
            Log.e("ArrivalsStopAdapter","Cannot compare: No arrivals in one of the stops");
        } else {
            Collections.sort(passaggi1);
            Collections.sort(passaggi2);
            int deltaOre = passaggi1.get(0).hh-passaggi2.get(0).hh;
            if(deltaOre>12)
                deltaOre -= 24;
            else if (deltaOre<-12)
                deltaOre  += 24;
            delta+=deltaOre*60 + passaggi1.get(0).mm-passaggi2.get(0).mm;
        }
        delta += (int)((dist1 -dist2)*minutialmetro*distancemultiplier);
        return delta;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RoutePositionSorter;
    }
}
