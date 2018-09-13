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
package it.reyboz.bustorino.adapters;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopSorterByDistance;
import it.reyboz.bustorino.fragments.FragmentListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SquareStopAdapter extends RecyclerView.Adapter<SquareStopAdapter.SquareViewHolder> {
    private static int layoutRes = R.layout.square_stop_element;
    //private List<Stop> stops;
    private Context  context;
    private @Nullable Location userPosition;
    private FragmentListener listener;
    private List<Stop> stops;

    public SquareStopAdapter(List<Stop> objects, Context con,FragmentListener fragmentListener,@Nullable Location pos) {
        context = con;
        listener  = fragmentListener;
        userPosition = pos;
        stops = objects;
    }



    @Override
    public SquareViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.square_stop_element, parent, false);
        //sort the stops by distance
        Collections.sort(stops,new StopSorterByDistance(userPosition));
        return new SquareViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SquareViewHolder holder, int position) {
            //DO THE ACTUAL WORK TO PUT THE DATA
        final Stop stop = stops.get(position);
        if(stop!=null){
            if(stop.getDistanceFromLocation(userPosition)!=Double.POSITIVE_INFINITY){
                Double distance = stop.getDistanceFromLocation(userPosition);
                holder.distancetextView.setText(distance.intValue()+" m");
            } else {
                holder.distancetextView.setVisibility(View.GONE);
            }
            holder.stopNameView.setText(stop.getStopDisplayName());
            holder.stopIDView.setText(stop.ID);
            String whatStopsHere = stop.routesThatStopHereToString();
            if(whatStopsHere == null) {
                holder.routesView.setVisibility(View.GONE);
            } else {
                holder.routesView.setText(whatStopsHere);
                holder.routesView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
            }
            holder.stopID =stop.ID;
        } else {
            Log.w("SquareStopAdapter","!! The selected stop is null !!");
        }
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    class SquareViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener  {
        TextView stopIDView;
        TextView stopNameView;
        TextView routesView;
        TextView distancetextView;
        String stopID;

        SquareViewHolder(View holdView){
            super(holdView);
            holdView.setOnClickListener(this);
            stopIDView = (TextView) holdView.findViewById(R.id.busStopIDView);
            stopNameView = (TextView) holdView.findViewById(R.id.stopNameView);
            routesView = (TextView) holdView.findViewById(R.id.routesStoppingTextView);
            distancetextView = (TextView) holdView.findViewById(R.id.distanceTextView);
        }

        @Override
        public void onClick(View v) {
            listener.createFragmentForStop(stopID);
        }

    }

    /*
    @Override
    public Stop getItem(int position) {
        return stops.get(position);
    }
    */
}
