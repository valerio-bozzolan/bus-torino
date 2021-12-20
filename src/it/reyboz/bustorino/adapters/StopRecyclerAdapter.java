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
    private static final int row_layout = R.layout.entry_bus_stop;
    private static final int busIcon = R.drawable.bus;
    private static final int trainIcon = R.drawable.subway;
    private static final int tramIcon = R.drawable.tram;
    private static final int cityIcon = R.drawable.city;

    private AdapterListener listener;
    private int position;



    protected static class ViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener{
        TextView busStopIDTextView;
        TextView busStopNameTextView;
        //TextView busLineVehicleIcon;
        TextView busStopLinesTextView;
        TextView busStopLocaLityTextView;
        Stop mStop;

        public ViewHolder(@NonNull View itemView, AdapterListener listener) {
            super(itemView);
            busStopIDTextView = (TextView) itemView.findViewById(R.id.busStopID);
            busStopNameTextView = (TextView) itemView.findViewById(R.id.busStopName);
            busStopLinesTextView = (TextView) itemView.findViewById(R.id.routesThatStopHere);
            busStopLocaLityTextView = (TextView) itemView.findViewById(R.id.busStopLocality);

            mStop = new Stop("");

            itemView.setOnClickListener(view -> {
                listener.onTappedStop(mStop);
            });
        }
        //many thanks to https://stackoverflow.com/questions/26466877/how-to-create-context-menu-for-recyclerview
        @Override
        public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
            MenuInflater inflater = new MenuInflater(view.getContext());
            inflater.inflate(R.menu.menu_favourites_entry, contextMenu);
        }
    }

    public StopRecyclerAdapter(List<Stop> stops,AdapterListener listener) {
        this.stops = stops;
        this.listener = listener;
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(row_layout, parent, false);

        return  new StopRecyclerAdapter.ViewHolder(view, listener);
    }

    @Override
    public void onViewRecycled(@NonNull StopRecyclerAdapter.ViewHolder holder) {
        holder.itemView.setOnLongClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public void onBindViewHolder(@NonNull StopRecyclerAdapter.ViewHolder vh, int position) {
        Log.d("StopRecyclerAdapter", "Called for position "+position);
        Stop stop = stops.get(position);
        vh.busStopIDTextView.setText(stop.ID);
        vh.mStop = stop;
        Log.d("StopRecyclerAdapter", "Stop: "+stop.ID);

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
            vh.busStopLocaLityTextView.setText(stop.location);
            vh.busStopLocaLityTextView.setVisibility(View.VISIBLE); // might be GONE due to View Holder Pattern
        }
        //trick to set the position
        vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                setPosition(vh.getAdapterPosition());
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }
}
