/*
	BusTO  - UI components
    Copyright (C) 2017 Fabio Mazza

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
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.fragments.FragmentListener;

import java.util.Comparator;
import java.util.List;

public class SquareStopAdapter extends ArrayAdapter<Stop> {
    private static int layoutRes = R.layout.square_stop_element;
    //private List<Stop> stops;
    private Context  context;
    private @Nullable Location userPosition;
    private FragmentListener listener;

    public SquareStopAdapter(List<Stop> objects, Context con,FragmentListener fragmentListener,@Nullable Location pos) {
        super(con,layoutRes,objects);//stops = objects;
        context = con;
        listener  = fragmentListener;
        userPosition = pos;
    }

    @Override
    public void sort(@NonNull Comparator<? super Stop> comparator) {
        super.sort(comparator);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        SquareViewHolder vh;
        if(convertView ==  null) {
            //expand the view
            convertView = inflater.inflate(layoutRes, parent,false);
            vh = new SquareViewHolder();
            vh.stopIDView = (TextView) convertView.findViewById(R.id.busStopIDView);
            vh.stopNameView = (TextView) convertView.findViewById(R.id.stopNameView);
            vh.routesView = (TextView) convertView.findViewById(R.id.routesStoppingTextView);
            vh.distancetextView = (TextView) convertView.findViewById(R.id.distanceTextView);

        } else {
            vh = (SquareViewHolder) convertView.getTag();
        }


        final Stop stop = getItem(position);
        if(stop!=null){
            if(stop.getDistanceFromLocation(userPosition)!=Double.POSITIVE_INFINITY){
                Double distance = stop.getDistanceFromLocation(userPosition);
                vh.distancetextView.setText(distance.intValue()+" m");
            } else {
                vh.distancetextView.setVisibility(View.GONE);
            }
            vh.stopNameView.setText(stop.getStopDisplayName());
            vh.stopIDView.setText(stop.ID);
            String whatStopsHere = stop.routesThatStopHereToString();
            if(whatStopsHere == null) {
                vh.routesView.setVisibility(View.GONE);
            } else {
                vh.routesView.setText(whatStopsHere);
                vh.routesView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
            }
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.createFragmentForStop(stop.ID);
                }
            });
        } else {
            Log.w("SquareStopAdapter","!! The selected stop is null !!");
        }


        //Log.d("SquareStopAdapter","Stop: "+ vh.stopIDView.getText()+" "+ vh.stopNameView.getText());
        convertView.setTag(vh);
        return convertView;

    }
    private static class SquareViewHolder{
        TextView stopIDView;
        TextView stopNameView;
        TextView routesView;
        TextView distancetextView;
    }
    /*
    @Override
    public Stop getItem(int position) {
        return stops.get(position);
    }
    */
}
