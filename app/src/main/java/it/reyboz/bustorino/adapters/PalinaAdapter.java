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
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.*;

import androidx.recyclerview.widget.RecyclerView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.util.PassaggiSorter;
import it.reyboz.bustorino.util.RouteSorterByArrivalTime;
import org.jetbrains.annotations.NotNull;

/**
 * This once was a ListView Adapter for BusLine[].
 *
 * Thanks to Framentos developers for the guide:
 * http://www.framentos.com/en/android-tutorial/2012/07/16/listview-in-android-using-custom-listadapter-and-viewcache/#
 *
 * @author Valerio Bozzolan
 * @author Ludovico Pavesi
 * @author Fabio Mazza
 */
public class PalinaAdapter extends RecyclerView.Adapter<PalinaAdapter.PalinaViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int ROW_LAYOUT = R.layout.entry_bus_line_passage;
    private static final int metroBg = R.drawable.route_background_metro;
    private static final int busBg = R.drawable.route_background_bus;
    private static final int extraurbanoBg = R.drawable.route_background_bus_long_distance;

    private static final int busIcon = R.drawable.bus;
    private static final int trainIcon = R.drawable.subway;
    private static final int tramIcon = R.drawable.tram;

    private final String KEY_CAPITALIZE;
    private Capitalize capit;

    private final List<Route> mRoutes;
    private final PalinaClickListener mRouteListener;

    @NonNull
    @NotNull
    @Override
    public PalinaViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(ROW_LAYOUT, parent, false);
        return new PalinaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull PalinaViewHolder vh, int position) {
        final Route route = mRoutes.get(position);
        final Context con = vh.itemView.getContext();
        final Resources res = con.getResources();

        vh.routeIDTextView.setText(route.getDisplayCode());
        vh.routeCard.setOnClickListener(view -> mRouteListener.requestShowingRoute(route));
        if(route.destinazione==null || route.destinazione.length() == 0) {
            vh.rowRouteDestination.setVisibility(View.GONE);
            // move around the route timetable
            final ViewGroup.MarginLayoutParams pars = (ViewGroup.MarginLayoutParams) vh.rowRouteTimetable.getLayoutParams();
            if (pars!=null){
                pars.topMargin = 16;
                if(Build.VERSION.SDK_INT >= 17)
                    pars.setMarginStart(20);
                pars.leftMargin = 20;
            }
        } else {
            // View Holder Pattern(R) renders each element from a previous one: if the other one had an invisible rowRouteDestination, we need to make it visible.
            vh.rowRouteDestination.setVisibility(View.VISIBLE);
            String dest = route.destinazione;
            switch (capit){
                case ALL:
                    dest = route.destinazione.toUpperCase(Locale.ROOT);
                    break;
                case FIRST:
                    dest = utils.toTitleCase(route.destinazione, true);
                    break;
                case DO_NOTHING:
                default:

            }
            vh.rowRouteDestination.setText(dest);

            //set click listener
            vh.itemView.setOnClickListener(view -> {
                mRouteListener.showRouteFullDirection(route);
            });
        }

        switch (route.type) {
            //UNKNOWN = BUS for the moment
            case UNKNOWN:
            case BUS:
            default:
                // convertView could contain another background, reset it
                //vh.rowStopIcon.setBackgroundResource(busBg);

                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
                break;
            case LONG_DISTANCE_BUS:
                //vh.rowStopIcon.setBackgroundResource(extraurbanoBg);
                vh.routeCard.setCardBackgroundColor(ResourcesCompat.getColor(res, R.color.extraurban_bus_bg, null));
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
                break;
            case METRO:
                //vh.rowStopIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                //vh.rowStopIcon.setBackgroundResource(metroBg);
                vh.routeIDTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                vh.routeCard.setCardBackgroundColor(ResourcesCompat.getColor(res, R.color.metro_bg, null));
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(trainIcon, 0, 0, 0);
                break;
            case RAILWAY:
                //vh.rowStopIcon.setBackgroundResource(busBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(trainIcon, 0, 0, 0);
                break;
            case TRAM: // never used but whatever.
                //vh.rowStopIcon.setBackgroundResource(busBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(tramIcon, 0, 0, 0);
                break;
        }

        List<Passaggio> passaggi = route.passaggi;
        //TODO: Sort the passaggi with realtime first if source is GTTJSONFetcher
        if(passaggi.size() == 0) {
            vh.rowRouteTimetable.setText(R.string.no_passages);

        } else {
            vh.rowRouteTimetable.setText(route.getPassaggiToString());
        }

    }

    @Override
    public int getItemCount() {
        return mRoutes.size();
    }

    //private static final int cityIcon = R.drawable.city;

    // hey look, a pattern!
    public static class PalinaViewHolder extends RecyclerView.ViewHolder {
        //final TextView rowStopIcon;
        final TextView routeIDTextView;
        final CardView routeCard;
        final TextView rowRouteDestination;
        final TextView rowRouteTimetable;

        public PalinaViewHolder(@NonNull @NotNull View view) {
            super(view);
            /*
            convertView.findViewById(R.id.routeID);
            vh.rowRouteDestination = (TextView) convertView.findViewById(R.id.routeDestination);
            vh.rowRouteTimetable = (TextView) convertView.findViewById(R.id.routesThatStopHere);
             */
            //rowStopIcon = view.findViewById(R.id.routeID);
            routeIDTextView = view.findViewById(R.id.routeNameTextView);
            routeCard = view.findViewById(R.id.routeCard);
            rowRouteDestination = view.findViewById(R.id.routeDestination);
            rowRouteTimetable = view.findViewById(R.id.routesThatStopHere);
        }
    }
    private static Capitalize getCapitalize(SharedPreferences shPr, String key){
        String capitalize = shPr.getString(key, "");

        switch (capitalize.trim()){
            case "KEEP":
                return Capitalize.DO_NOTHING;
            case "CAPITALIZE_ALL":
                return Capitalize.ALL;

            case "CAPITALIZE_FIRST":
                return Capitalize.FIRST;
        }
        return  Capitalize.DO_NOTHING;
    }

    public PalinaAdapter(Context context, Palina p, PalinaClickListener listener, boolean hideEmptyRoutes) {
        Comparator<Passaggio> sorter = null;
        if (p.getPassaggiSourceIfAny()== Passaggio.Source.GTTJSON){
            sorter = new PassaggiSorter();
        }
        final List<Route> routes;
        if (hideEmptyRoutes){
            // build the routes by filtering them
            routes = new ArrayList<>();
            for(Route r: p.queryAllRoutes()){
                //add only if there is at least one passage
                if (r.numPassaggi()>0){
                    routes.add(r);
                }
            }
        } else
            routes = p.queryAllRoutes();
        for(Route r: routes){
            if (sorter==null) Collections.sort(r.passaggi);
            else Collections.sort(r.passaggi, sorter);
        }

        Collections.sort(routes,new RouteSorterByArrivalTime());

        mRoutes = routes;
        KEY_CAPITALIZE = context.getString(R.string.pref_arrival_times_capit);
        SharedPreferences defSharPref = PreferenceManager.getDefaultSharedPreferences(context);
        defSharPref.registerOnSharedPreferenceChangeListener(this);
        this.capit = getCapitalize(defSharPref, KEY_CAPITALIZE);

        this.mRouteListener = listener;
    }



    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(KEY_CAPITALIZE)){
            capit = getCapitalize(sharedPreferences, KEY_CAPITALIZE);

            notifyDataSetChanged();
        }
    }

    enum Capitalize{
        DO_NOTHING, ALL, FIRST
    }

    public interface PalinaClickListener{
        /**
         * Simple click listener for the whole line (show info)
         * @param route for toast
         */
        void showRouteFullDirection(Route route);

        /**
         * Show the line with all the stops in the app
         * @param route partial line info
         */
        void requestShowingRoute(Route route);
    }
}
