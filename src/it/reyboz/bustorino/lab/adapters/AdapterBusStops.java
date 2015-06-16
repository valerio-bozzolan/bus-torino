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
package it.reyboz.bustorino.lab.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusLine;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;

/**
 * ListView Adapter for BusStop[].
 *
 * @author Valerio Bozzolan
 */
public class AdapterBusStops extends ArrayAdapter<BusStop> {

    private LayoutInflater layoutInflater;
    private Context context;
    private int resource;

    public AdapterBusStops(Context context, int resource, BusStop busStops[]) {
        super(context, resource, busStops);
        layoutInflater = LayoutInflater.from(context);
        this.context = context;
        this.resource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Create a new view of my layout and inflate it in the row
        convertView = layoutInflater.inflate(resource, null);

        // Extract the busStop object to show
        BusStop busStop = getItem( position );

        // Take the TextView from layout and set the BusStop ID
        TextView busStopIDTextView = (TextView) convertView.findViewById(R.id.busStopID);
        busStopIDTextView.setText(busStop.getBusStopID());

        // Take the TextView from layout and set the busStop name
        TextView busStopNameTextView = (TextView) convertView.findViewById(R.id.busStopName);
        busStopNameTextView.setText(busStop.getBusStopName());

        // Vehicle icon
        TextView busLineVehicleIcon = (TextView) convertView.findViewById(R.id.vehicleIcon);

        // Take the TextView from layout and set the busStop locality
        TextView busStopLinesTextView = (TextView) convertView.findViewById(R.id.busLineNames);
        if(busStop.getBusLines() != null) {
            String busLines = "";
            for(BusLine busLine: busStop.getBusLines()) {
                if(busLines.length() > 0) {
                    busLines += ", ";
                }
                busLines += busLine.getBusLineName();
            }
            busStopLinesTextView.setText(busLines);
        } else {
            busStopLinesTextView.setVisibility(View.GONE);
            busLineVehicleIcon.setVisibility(View.INVISIBLE);
        }

        TextView busStopLocaLityTextView = (TextView) convertView.findViewById(R.id.busStopLocality);
        if (busStop.getBusStopLocality() != null) {
            busStopLocaLityTextView.setText(busStop.getBusStopLocality());
        } else {
            busStopLocaLityTextView.setVisibility(View.GONE);
        }

        return convertView;
    }
}
