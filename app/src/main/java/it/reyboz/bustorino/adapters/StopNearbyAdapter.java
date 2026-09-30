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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.GPSPoint;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.util.StopSorterByDistance;
import it.reyboz.bustorino.fragments.FragmentListenerMain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StopNearbyAdapter extends RecyclerView.Adapter<StopNearbyAdapter.SquareViewHolder> {
    private final static int layoutRes = R.layout.item_stop_nearby_card;
    //private List<Stop> stops;
    private @Nullable GPSPoint userPosition;
    private OnStopClickListener listener;
    private boolean showLocation;
    private ArrayList<Stop> stops;

    public StopNearbyAdapter(@NotNull ArrayList<Stop> stopList,
                             @Nullable GPSPoint pos, boolean showLocation, OnStopClickListener fragmentListener) {
        listener  = fragmentListener;
        userPosition = pos;
        stops = stopList;
        this.showLocation = showLocation;
    }



    @Override
    public SquareViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        //sort the stops by distance
        if (userPosition!=null) {
            if (stops != null && stops.size() > 0)
                stops.sort(new StopSorterByDistance(userPosition));
        }
        return new SquareViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SquareViewHolder holder, int position) {
            //DO THE ACTUAL WORK TO PUT THE DATA
        if(stops==null || stops.isEmpty()) return; //NO STOPS
        final Stop stop = stops.get(position);
        final Context context = holder.itemView.getContext();
        if(stop!=null){
            if(userPosition!=null && stop.getDistanceFromLocation(userPosition)!=Double.POSITIVE_INFINITY){
                Double distance = stop.getDistanceFromLocation(userPosition);
                holder.distancetextView.setText(distance.intValue()+" m");
                holder.distancetextView.setVisibility(View.VISIBLE);
            } else {
                holder.distancetextView.setVisibility(View.GONE);
            }
            holder.stopNameView.setText(context.getString(
                    R.string.two_strings_format,"", stop.getStopDisplayName()));
                    // stop.ID +" - "+ stop.getStopDisplayName());
            holder.stopIDView.setText(stop.ID);
            String whatStopsHere = stop.routesThatStopHereToString();
            if(whatStopsHere == null) {
                holder.routesView.setVisibility(View.GONE);
            } else {
                holder.routesView.setText(whatStopsHere);
                //context.getString(R.string.lines_fill, whatStopsHere));
                holder.routesView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
            }
            holder.itemView.setOnClickListener(view -> {listener.onStopClick(stop);});
            if (showLocation && !(
                    stop.location==null || stop.location.isEmpty() || stop.location.contains("null"))
            ){
                holder.locationTextView.setText(stop.location);
                holder.locationTextView.setVisibility(View.VISIBLE);
            } else {
                holder.locationTextView.setVisibility(View.GONE);
            }
        } else {
            Log.w("SquareStopAdapter","!! The selected stop is null !!");
        }
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    class SquareViewHolder extends RecyclerView.ViewHolder  {
        TextView stopIDView;
        TextView stopNameView;
        TextView locationTextView;
        TextView routesView;
        TextView distancetextView;
        //Stop stop;

        SquareViewHolder(View holdView){
            super(holdView);
            //holdView.setOnClickListener(this);
            stopIDView = (TextView) holdView.findViewById(R.id.stop_numberText);
            stopNameView = (TextView) holdView.findViewById(R.id.stop_nameText);
            routesView = (TextView) holdView.findViewById(R.id.stop_linesText);
            locationTextView = holdView.findViewById(R.id.stop_locationTextView);
            distancetextView = (TextView) holdView.findViewById(R.id.stop_distanceTextView);
        }

        /*@Override
        public void onClick(View v) {
            listener.onStopClick(stop);
        }

         */

    }

    public void setStops(List<Stop> newStops) {
       // this.stops = stops;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return stops.size();
            }
            @Override
            public int getNewListSize() {
                return newStops.size();
            }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                var oldItem = stops.get(oldItemPosition);
                var newItem = newStops.get(newItemPosition);

                // usa un ID univoco
                return oldItem.ID.equals(newItem.ID);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                var oldItem = stops.get(oldItemPosition);
                var newItem = newStops.get(newItemPosition);
                return oldItem.equals(newItem);
            }
        });
        this.stops.clear();
        this.stops.addAll(newStops);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setUserPosition(@Nullable GPSPoint userPosition) {
        this.userPosition = userPosition;
    }
    /*
    @Override
    public Stop getItem(int position) {
        return stops.get(position);
    }
    */
}
