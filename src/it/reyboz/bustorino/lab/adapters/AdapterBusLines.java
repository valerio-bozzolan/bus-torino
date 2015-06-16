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

/**
 * ListView Adapter for BusLine[].
 *
 * Thanks to Framentos developers for the guide:
 * http://www.framentos.com/en/android-tutorial/2012/07/16/listview-in-android-using-custom-listadapter-and-viewcache/#
 *
 * @author Valerio Bozzolan
 */
public class AdapterBusLines extends ArrayAdapter<BusLine> {

    private LayoutInflater layoutInflater;
    private Context context;
    private int resource;

    public AdapterBusLines(Context context, int resource, BusLine busLines[]) {
        super(context, resource, busLines);
        layoutInflater = LayoutInflater.from(context);
        this.context = context;
        this.resource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Create a new view of my layout and inflate it in the row
        convertView = layoutInflater.inflate(resource, null);

        // Extract the busLine object to show
        BusLine busLine = getItem( position );

        // Take the TextView from layout and set the busLine name
        TextView busLineIconTextView = (TextView) convertView.findViewById(R.id.busLineIcon);
        busLineIconTextView.setText(busLine.getBusLineName());

        // Vehicle icon
        TextView busLineVehicleIcon = (TextView) convertView.findViewById(R.id.vehicleIcon);

        // Take the TextView from layout and set the BusLine's passages
        TextView busLinePassagesTextView = (TextView) convertView.findViewById(R.id.busLineNames);
        String timePassages = busLine.getTimePassagesString();
        if(timePassages != null) {
            busLinePassagesTextView.setText(timePassages);
        } else {
            busLinePassagesTextView.setText(R.string.no_passages);
            busLineVehicleIcon.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }
}
