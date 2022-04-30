/*
	BusTO  - Adapter components
    Copyright (C) 2021 Fabio Mazza

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

import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;

public class StopRecyclerAdapter extends RecyclerView.Adapter<StopRecyclerAdapter.ViewHolder> {
    private List<Stop> stops;
    private static final int ITEM_LAYOUT_FAVORITES = R.layout.entry_bus_stop;
    private static final int ITEM_LAYOUT_LINES = R.layout.bus_stop_line_elmt;
    private static final int busIcon = R.drawable.bus;
    private static final int trainIcon = R.drawable.subway;
    private static final int tramIcon = R.drawable.tram;
    private static final int cityIcon = R.drawable.city;

    private NameCapitalize capitalizeLocation = NameCapitalize.DO_NOTHING;
    private final Use usedFor;

    private final StopAdapterListener listener;
    private int position;



    protected static class ViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener{
        TextView busStopIDTextView;
        TextView busStopNameTextView;
        //TextView busLineVehicleIcon;
        TextView busStopLinesTextView;
        TextView busStopLocaLityTextView;

        View topStub, bottomStub;
        Stop mStop;

        int menuResID=R.menu.menu_favourites_entry;

        public ViewHolder(@NonNull View itemView, StopAdapterListener listener, Use usedFor) {
            super(itemView);
            busStopIDTextView = itemView.findViewById(R.id.busStopID);
            busStopNameTextView = itemView.findViewById(R.id.busStopName);
            busStopLinesTextView = itemView.findViewById(R.id.routesThatStopHere);
            busStopLocaLityTextView = itemView.findViewById(R.id.busStopLocality);
            switch (usedFor){
                case LINES:
                    topStub = itemView.findViewById(R.id.topStub);
                    bottomStub = itemView.findViewById(R.id.bottomStub);
                    menuResID = R.menu.menu_line_item;
                    break;
                case FAVORITES:
                default:
                    topStub = null;
                    bottomStub = null;
            }

            mStop = new Stop("");

            itemView.setOnClickListener(view -> {
                listener.onTappedStop(mStop);
            });
        }
        //many thanks to https://stackoverflow.com/questions/26466877/how-to-create-context-menu-for-recyclerview
        @Override
        public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
            MenuInflater inflater = new MenuInflater(view.getContext());
            inflater.inflate(menuResID, contextMenu);
        }
    }

    public StopRecyclerAdapter(List<Stop> stops, StopAdapterListener listener, Use usedFor) {
        this.stops = stops;
        this.listener = listener;
        this.usedFor = usedFor;
    }
    public StopRecyclerAdapter(List<Stop> stops, StopAdapterListener listener, Use usedFor, NameCapitalize locationCapit) {
        this.stops = stops;
        this.listener = listener;
        this.usedFor = usedFor;
        this.capitalizeLocation = locationCapit;
    }

    public NameCapitalize getCapitalizeLocation() {
        return capitalizeLocation;
    }

    public void setCapitalizeLocation(NameCapitalize capitalizeLocation) {
        this.capitalizeLocation = capitalizeLocation;
        notifyDataSetChanged();
    }

    public void setStops(List<Stop> stops){
        this.stops = stops;
        notifyDataSetChanged();
    }

    public List<Stop> getStops() {
        return stops;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @NonNull
    @Override
    public StopRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final int layoutID;
        switch (usedFor){
            case LINES:
                layoutID = ITEM_LAYOUT_LINES;
                break;
            case FAVORITES:
            default:
                layoutID = ITEM_LAYOUT_FAVORITES;

        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutID, parent, false);

        return  new StopRecyclerAdapter.ViewHolder(view, listener, this.usedFor);
    }

    @Override
    public void onViewRecycled(@NonNull StopRecyclerAdapter.ViewHolder holder) {
        holder.itemView.setOnLongClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public void onBindViewHolder(@NonNull StopRecyclerAdapter.ViewHolder vh, int position) {
        //Log.d("StopRecyclerAdapter", "Called for position "+position);
        Stop stop = stops.get(position);
        vh.busStopIDTextView.setText(stop.ID);
        vh.mStop = stop;
        //Log.d("StopRecyclerAdapter", "Stop: "+stop.ID);

        // NOTE: intentionally ignoring stop username in search results: if it's in the favorites, why are you searching for it?
        vh.busStopNameTextView.setText(stop.getStopDisplayName());
        String whatStopsHere = stop.routesThatStopHereToString();
        if(whatStopsHere == null) {
            vh.busStopLinesTextView.setVisibility(View.GONE);
        } else {
            vh.busStopLinesTextView.setText(whatStopsHere);
            vh.busStopLinesTextView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
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
            vh.busStopLocaLityTextView.setText(NameCapitalize.capitalizePass(stop.location, capitalizeLocation));
            vh.busStopLocaLityTextView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
        }
        //trick to set the position
        vh.itemView.setOnLongClickListener(view -> {
            setPosition(vh.getAdapterPosition());
            return false;
        });
        if(this.usedFor == Use.LINES){

            //vh.menuResID;
            vh.bottomStub.setVisibility(View.VISIBLE);
            vh.topStub.setVisibility(View.VISIBLE);
            if(position == 0) {
                vh.topStub.setVisibility(View.GONE);
            }
            else if (position == stops.size()-1) {
                vh.bottomStub.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    public enum  Use{
        FAVORITES, LINES
    }
}
