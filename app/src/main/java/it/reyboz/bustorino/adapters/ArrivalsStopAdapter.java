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
import android.content.SharedPreferences;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.fragments.FragmentListenerMain;

import java.util.*;

public class ArrivalsStopAdapter extends RecyclerView.Adapter<ArrivalsStopAdapter.ViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final static int layoutRes = R.layout.arrivals_nearby_card;
    //private List<Stop> stops;
    private @NonNull GPSPoint userPosition;
    private FragmentListenerMain listener;
    private List< RouteWithStop > routesPairList;
    private final Context context;
    //Maximum number of stops to keep
    private final int MAX_STOPS = 20; //TODO: make it programmable
    private final String KEY_CAPITALIZE;
    private NameCapitalize capit;


    public ArrivalsStopAdapter(@Nullable List< RouteWithStop > routesPairList, FragmentListenerMain fragmentListener, Context con, @NonNull GPSPoint pos) {
        listener  = fragmentListener;
        userPosition = pos;
        this.routesPairList = routesPairList;
        context = con.getApplicationContext();
        resetListAndPosition();
        // if(paline!=null)
        //resetRoutesPairList(paline);
        KEY_CAPITALIZE = context.getString(R.string.pref_arrival_times_capit);
        SharedPreferences defSharPref = PreferenceManager.getDefaultSharedPreferences(context);
        defSharPref.registerOnSharedPreferenceChangeListener(this);
        String capitalizeKey = defSharPref.getString(KEY_CAPITALIZE, "");
        this.capit = NameCapitalize.getCapitalize(capitalizeKey);
    }



    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            //DO THE ACTUAL WORK TO PUT THE DATA
        if(routesPairList==null || routesPairList.size() == 0) return; //NO STOPS
        final var stopRoutePair = routesPairList.get(position);
        if(stopRoutePair != null){
            final Stop stop = stopRoutePair.getStop();
            final Route r = stopRoutePair.getRoute();
            final Double distance = stop.getDistanceFromLocation(userPosition.getLatitude(), userPosition.longitude);
            if(distance!=Double.POSITIVE_INFINITY){
                holder.distancetextView.setText(distance.intValue()+" m");
            } else {
                holder.distancetextView.setVisibility(View.GONE);
            }
            final String stopText = String.format(context.getResources().getString(R.string.two_strings_format),stop.getStopDisplayName(),stop.ID);
            holder.stopNameView.setText(stopText);
            //final String routeName = String.format(context.getResources().getString(R.string.two_strings_format),r.getNameForDisplay(),r.destinazione);
            if (r!=null) {
                holder.lineNameTextView.setText(r.getDisplayCode());
                holder.lineDirectionTextView.setText(NameCapitalize.capitalizePass(r.destinazione, capit));
                holder.arrivalsTextView.setText(r.getPassaggiToString(0,2,true));
            } else {
                holder.lineNameTextView.setVisibility(View.INVISIBLE);
                holder.lineDirectionTextView.setVisibility(View.INVISIBLE);
                //holder.arrivalsTextView.setVisibility(View.INVISIBLE);
            }
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
            holder.stopID =stop.ID;
        } else {
            Log.w("SquareStopAdapter","!! The selected stop is null !!");
        }
    }

    @Override
    public int getItemCount() {
        return routesPairList.size();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(KEY_CAPITALIZE)){
            String k = sharedPreferences.getString(KEY_CAPITALIZE, "");
            capit = NameCapitalize.getCapitalize(k);

            notifyDataSetChanged();

        }
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
            listener.requestArrivalsForStopID(stopID);
        }

    }
    /*
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

     */

    public void updatePosition(@NonNull GPSPoint pos){
        userPosition = pos;
    }

    public void setRoutesPairListAndPosition(@NonNull List<RouteWithStop> newList, @Nullable GPSPoint pos) {
        if(pos!=null) updatePosition(pos);

        if (routesPairList == null) {
            routesPairList = new ArrayList<>(newList);
            notifyItemRangeInserted(0, newList.size());
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return routesPairList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {

                        RouteWithStop oldItem = routesPairList.get(oldItemPosition);
                        RouteWithStop newItem = newList.get(newItemPosition);

                        // usa un ID univoco
                        return oldItem.getId().equals(newItem.getId());
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {

                        RouteWithStop oldItem = routesPairList.get(oldItemPosition);
                        RouteWithStop newItem = newList.get(newItemPosition);

                        // confronta il contenuto
                        return oldItem.equals(newItem);
                    }
                }
        );

        routesPairList.clear();
        routesPairList.addAll(newList);

        diffResult.dispatchUpdatesTo(this);


        /*if (this.routesPairList == null || routesPairList.size() == 0){
            routesPairList = mRoutesPairList;
            notifyDataSetChanged();
        } else{

            final var indexMapIn = getRouteIndexMap(mRoutesPairList);
            final var indexMapExisting = getRouteIndexMap(routesPairList);
            //List<Pair<Stop,Route>> oldList = routesPairList;
            routesPairList = mRoutesPairList;
            /*
            for (Pair<String,String> pair: indexMapIn.keySet()){
                final Integer posIn = indexMapIn.get(pair);
                if (posIn == null) continue;
                if (indexMapExisting.containsKey(pair)){
                    final Integer posExisting = indexMapExisting.get(pair);
                    //THERE IS ALREADY
                    //routesPairList.remove(posExisting.intValue());
                    //routesPairList.add(posIn,mRoutesPairList.get(posIn));

                    notifyItemMoved(posExisting, posIn);
                    indexMapExisting.remove(pair);
                } else{
                    //INSERT IT
                    //routesPairList.add(posIn,mRoutesPairList.get(posIn));
                    notifyItemInserted(posIn);
                }
            }//
            //REMOVE OLD STOPS
            for (Pair<String,String> pair: indexMapExisting.keySet()) {
                final Integer posExisting = indexMapExisting.get(pair);
                if (posExisting == null) continue;
                //routesPairList.remove(posExisting.intValue());
                notifyItemRemoved(posExisting);
            }

        */

    }

    /**
     * Sort and remove the repetitions for the routesPairList
     */
    private void resetListAndPosition(){
        //Collections.sort(this.routesPairList,new RoutePositionSorter(userPosition));
        //All of this to get only the first occurrences of a line (name & direction)
        var iterator = routesPairList.listIterator();
        Set<Pair<String,String>> allRoutesDirections = new HashSet<>();
        while(iterator.hasNext()){
            final var stopRoutePair = iterator.next();
            stopRoutePair.getRoute();
            final Pair<String, String> routeNameDirection = new Pair<>(stopRoutePair.getRoute().getName(), stopRoutePair.getRoute().destinazione);
            if (allRoutesDirections.contains(routeNameDirection)) {
                iterator.remove();
            } else {
                allRoutesDirections.add(routeNameDirection);
            }
        }
    }


    private static HashMap<RouteWithStop, Integer> getRouteIndexMap(List<RouteWithStop> routesPairList){
        final HashMap<RouteWithStop, Integer> myMap = new HashMap<>();
        for (int i=0; i<routesPairList.size(); i++){
            myMap.put(routesPairList.get(i), i);
        }
        return myMap;
    }

}
