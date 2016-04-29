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
package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;

/**
 * @see PalinaAdapter
 */
public class StopAdapter extends ArrayAdapter<Stop> {
    private LayoutInflater li;
    private static int row_layout = R.layout.entry_bus_stop;
    private static final int busIcon = R.drawable.bus;
    private static final int trainIcon = R.drawable.subway;
    private static final int tramIcon = R.drawable.tram;
    private static final int cityIcon = R.drawable.city;

    static class ViewHolder {
        TextView busStopIDTextView;
        TextView busStopNameTextView;
        //TextView busLineVehicleIcon;
        TextView busStopLinesTextView;
        TextView busStopLocaLityTextView;
    }

    public StopAdapter(Context context, List<Stop> stops) {
        super(context, row_layout, stops);
        li = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;

        if(convertView == null) {
            convertView = li.inflate(row_layout, null);
            vh = new ViewHolder();
            vh.busStopIDTextView = (TextView) convertView.findViewById(R.id.busStopID);
            vh.busStopNameTextView = (TextView) convertView.findViewById(R.id.busStopName);
            vh.busStopLinesTextView = (TextView) convertView.findViewById(R.id.routesThatStopHere);
            vh.busStopLocaLityTextView = (TextView) convertView.findViewById(R.id.busStopLocality);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        Stop stop = getItem(position);
        vh.busStopIDTextView.setText(stop.ID);

        // NOTE: intentionally ignoring stop username: if it's in the favorites, why are you searching for it?
        vh.busStopNameTextView.setText(stop.name);

        String whatStopsHere = stop.routesThatStopHereToString();
        if(whatStopsHere == null) {
            vh.busStopLinesTextView.setVisibility(View.GONE);
        } else {
            vh.busStopLinesTextView.setText(whatStopsHere);
        }

        if(stop.type == null) {
            vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
        } else {
            switch(stop.type) {
                case BUS:
                default:
                    vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
                    break;
                case METRO:
                case RAILWAY:
                    vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(trainIcon, 0, 0, 0);
                    break;
                case TRAM:
                    vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(tramIcon, 0, 0, 0);
                    break;
                case LONG_DISTANCE_BUS:
                    // è l'opposto della città ma va beh, dettagli.
                    vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(cityIcon, 0, 0, 0);
            }
        }

        if (stop.location == null) {
            vh.busStopLocaLityTextView.setVisibility(View.GONE);
        } else {
            vh.busStopLocaLityTextView.setText(stop.location);
        }

        return convertView;
    }
}
