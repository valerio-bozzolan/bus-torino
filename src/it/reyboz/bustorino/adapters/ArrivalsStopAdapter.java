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
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.fragments.FragmentListener;
import it.reyboz.bustorino.util.RoutePositionSorter;
import it.reyboz.bustorino.util.StopSorterByDistance;

import java.util.*;

public class ArrivalsStopAdapter extends RecyclerView.Adapter<ArrivalsStopAdapter.ViewHolder> {
    private final static int layoutRes = R.layout.arrivals_nearby_card;
    //private List<Stop> stops;
    private @Nullable Location userPosition;
    private FragmentListener listener;
    private List< Pair<Stop, Route> > routesPairList = new ArrayList<>();
    private Context context;
    //Maximum number of stops to keep
    private final int MAX_STOPS = 20; //TODO: make it programmable

    public ArrivalsStopAdapter(@Nullable List< Pair<Stop, Route> > routesPairList, FragmentListener fragmentListener, Context con, @Nullable Location pos) {
        listener  = fragmentListener;
        userPosition = pos;
        this.routesPairList = routesPairList;
        context = con.getApplicationContext();
        resetListAndPosition();
        // if(paline!=null)
        //resetRoutesPairList(paline);
    }



    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
            //DO THE ACTUAL WORK TO PUT THE DATA
        if(routesPairList==null || routesPairList.size() == 0) return; //NO STOPS
        final Pair<Stop,Route> stopRoutePair = routesPairList.get(position);
        if(stopRoutePair!=null){
            final Stop stop = stopRoutePair.first;
            final Route r = stopRoutePair.second;
            final Double distance = stop.getDistanceFromLocation(userPosition);
            if(distance!=Double.POSITIVE_INFINITY){
                holder.distancetextView.setText(distance.intValue()+" m");
            } else {
                holder.distancetextView.setVisibility(View.GONE);
            }
            final String stopText = String.format(context.getResources().getString(R.string.two_strings_format),stop.getStopDisplayName(),stop.ID);
            holder.stopNameView.setText(stopText);
            //final String routeName = String.format(context.getResources().getString(R.string.two_strings_format),r.getNameForDisplay(),r.destinazione);
            holder.lineNameTextView.setText(r.getNameForDisplay());
            /* EXPERIMENTS
            if(r.destinazione==null || r.destinazione.trim().isEmpty()){
                holder.lineDirectionTextView.setVisibility(View.GONE);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.arrivalsDescriptionTextView.getLayoutParams();
                params.addRule(RelativeLayout.RIGHT_OF,holder.lineNameTextView.getId());
                holder.arrivalsDescriptionTextView.setLayoutParams(params);
            } else {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.arrivalsDescriptionTextView.getLayoutParams();
                params.removeRule(RelativeLayout.RIGHT_OF);
                holder.arrivalsDescriptionTextView.setLayoutParams(params);
                holder.lineDirectionTextView.setVisibility(View.VISIBLE);

            }
             */
            holder.lineDirectionTextView.setText(r.destinazione);
            holder.arrivalsTextView.setText(r.getPassaggiToString(0,2,true));
            holder.stopID =stop.ID;
        } else {
            Log.w("SquareStopAdapter","!! The selected stop is null !!");
        }
    }

    @Override
    public int getItemCount() {
        return routesPairList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener  {
        TextView lineNameTextView;
        TextView lineDirectionTextView;
        TextView stopNameView;
        TextView arrivalsDescriptionTextView;
        TextView arrivalsTextView;
        TextView distancetextView;
        String stopID;

        ViewHolder(View holdView){
            super(holdView);
            holdView.setOnClickListener(this);
            lineNameTextView = (TextView) holdView.findViewById(R.id.lineNameTextView);
            lineDirectionTextView = (TextView) holdView.findViewById(R.id.lineDirectionTextView);
            stopNameView = (TextView) holdView.findViewById(R.id.arrivalStopName);
            arrivalsTextView = (TextView) holdView.findViewById(R.id.arrivalsTimeTextView);
            arrivalsDescriptionTextView = (TextView) holdView.findViewById(R.id.arrivalsDescriptionTextView);
            distancetextView = (TextView) holdView.findViewById(R.id.arrivalsDistanceTextView);
        }

        @Override
        public void onClick(View v) {
            listener.createFragmentForStop(stopID);
        }

    }

    public void resetRoutesPairList(List<Palina> stopList){
        Collections.sort(stopList,new StopSorterByDistance(userPosition));

        this.routesPairList = new ArrayList<>(stopList.size());
        int maxNum = Math.min(MAX_STOPS, stopList.size());
        for(Palina p: stopList.subList(0,maxNum)){
            //if there are no routes available, skip stop
            if(p.queryAllRoutes().size() == 0) continue;
            for(Route r: p.queryAllRoutes()){
                //if there are no routes, should not do anything
                routesPairList.add(new Pair<>(p,r));
            }
        }
    }

    public void setUserPosition(@Nullable Location userPosition) {
        this.userPosition = userPosition;
    }

    public void setRoutesPairListAndPosition(List<Pair<Stop, Route>> routesPairList, @Nullable Location pos) {
        if(routesPairList!=null)
            this.routesPairList = routesPairList;
        if(pos!=null){
            this.userPosition = pos;
        }
        resetListAndPosition();

    }
    private void resetListAndPosition(){
        Collections.sort(this.routesPairList,new RoutePositionSorter(userPosition));
        //All of this to get only the first occurrences of a line (name & direction)
        ListIterator<Pair<Stop,Route>> iterator = routesPairList.listIterator();
        Set<Pair<String,String>> allRoutesDirections = new HashSet<>();
        while(iterator.hasNext()){
            final Pair<Stop,Route> stopRoutePair = iterator.next();
            final Pair<String,String> routeNameDirection = new Pair<>(stopRoutePair.second.getName(),stopRoutePair.second.destinazione);
            if(allRoutesDirections.contains(routeNameDirection)){
                iterator.remove();
            } else {
                allRoutesDirections.add(routeNameDirection);
            }
        }
    }



}
