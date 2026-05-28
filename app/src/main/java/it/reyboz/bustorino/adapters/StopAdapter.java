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
package it.reyboz.bustorino.adapters;

import android.content.Context;
import android.widget.ImageView;
import androidx.annotation.NonNull;
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
    private static final int row_layout = R.layout.entry_bus_stop;
    private static final int busIcon = R.drawable.ic_bus;
    private static final int trainIcon = R.drawable.ic_subway_filled;
    private static final int tramIcon = R.drawable.ic_tram_filled_24;
    private static final int cityIcon = R.drawable.city;


    private static class ViewHolder {
        final TextView busStopIDTextView;
        final TextView busStopNameTextView;
        //TextView busLineVehicleIcon;
        final TextView busStopLinesTextView;
        final TextView busStopLocaLityTextView;

        ViewHolder(View view) {
            busStopIDTextView = view.findViewById(R.id.busStopID);
            busStopNameTextView = view.findViewById(R.id.busStopName);
            busStopLinesTextView = view.findViewById(R.id.routesThatStopHere);
            busStopLocaLityTextView = view.findViewById(R.id.busStopLocality);
        }
    }

    public StopAdapter(Context context, List<Stop> stops) {
        super(context, row_layout, stops);
        li = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder vh;

        if(convertView == null) {
            convertView = li.inflate(row_layout, null);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        Stop stop = getItem(position);
        vh.busStopIDTextView.setText(stop.ID);

        // NOTE: intentionally ignoring stop username in search results: if it's in the favorites, why are you searching for it?
        vh.busStopNameTextView.setText(stop.getStopDisplayName());
        String whatStopsHere = stop.routesThatStopHereToString();
        if(whatStopsHere == null) {
            vh.busStopLinesTextView.setVisibility(View.GONE);
        } else {
            vh.busStopLinesTextView.setText(whatStopsHere);
            vh.busStopLinesTextView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
        }

        /*
        // DEPRECATED CODE: ALWAYS USE BUS STOP ICON
        if(stop.type == null) {
                //vh.busStopLinesTextView.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
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

         */

        if (stop.location == null) {
            vh.busStopLocaLityTextView.setVisibility(View.GONE);
        } else {
            vh.busStopLocaLityTextView.setText(stop.location);
            vh.busStopLocaLityTextView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
        }

        return convertView;
    }
}
