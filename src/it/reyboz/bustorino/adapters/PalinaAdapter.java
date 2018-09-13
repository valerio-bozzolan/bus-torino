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
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;

/**
 * This once was a ListView Adapter for BusLine[].
 *
 * Thanks to Framentos developers for the guide:
 * http://www.framentos.com/en/android-tutorial/2012/07/16/listview-in-android-using-custom-listadapter-and-viewcache/#
 *
 * @author Valerio Bozzolan
 * @author Ludovico Pavesi
 */
public class PalinaAdapter extends ArrayAdapter<Route> {
    private LayoutInflater li;
    private static int row_layout = R.layout.entry_bus_line_passage;
    private static final int metroBg = R.drawable.route_background_metro;
    private static final int busBg = R.drawable.route_background_bus;
    private static final int extraurbanoBg = R.drawable.route_background_bus_long_distance;
    private static final int busIcon = R.drawable.bus;
    private static final int trainIcon = R.drawable.subway;
    private static final int tramIcon = R.drawable.tram;
    //private static final int cityIcon = R.drawable.city;

    // hey look, a pattern!
    private static class ViewHolder {
        TextView rowStopIcon;
        TextView rowRouteDestination;
        TextView rowRouteTimetable;
    }

    public PalinaAdapter(Context context, Palina p) {
        super(context, row_layout, p.queryAllRoutes());
        li = LayoutInflater.from(context);
    }

    /**
     * Some parts taken from the AdapterBusLines class.<br>
     * Some parts inspired by these enlightening tutorials:<br>
     * http://www.simplesoft.it/android/guida-agli-adapter-e-le-listview-in-android.html<br>
     * https://www.codeofaninja.com/2013/09/android-viewholder-pattern-example.html<br>
     * And some other bits and bobs TIRATI FUORI DAL NULLA CON L'INTUIZIONE INTELLETTUALE PERCHÉ
     * SEMBRA CHE NESSUNO ABBIA LA MINIMA IDEA DI COME FUNZIONA UN ADAPTER SU ANDROID.
     */
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder vh;

        if(convertView == null) {
            // INFLATE!
            // setting a parent here is not supported and causes a fatal exception, apparently.
            convertView = li.inflate(row_layout, null);

            // STORE TEXTVIEWS!
            vh = new ViewHolder();
            vh.rowStopIcon = (TextView) convertView.findViewById(R.id.routeID);
            vh.rowRouteDestination = (TextView) convertView.findViewById(R.id.routeDestination);
            vh.rowRouteTimetable = (TextView) convertView.findViewById(R.id.routesThatStopHere);

            // STORE VIEWHOLDER IN\ON\OVER\UNDER\ABOVE\BESIDE THE VIEW!
            convertView.setTag(vh);
        } else {
            // RECOVER THIS STUFF!
            vh = (ViewHolder) convertView.getTag();
        }

        Route route = getItem(position);
        vh.rowStopIcon.setText(route.name);
        if(route.destinazione==null || route.destinazione.length() == 0) {
            vh.rowRouteDestination.setVisibility(View.GONE);
        } else {
            // View Holder Pattern(R) renders each element from a previous one: if the other one had an invisible rowRouteDestination, we need to make it visible.
            vh.rowRouteDestination.setVisibility(View.VISIBLE);
            vh.rowRouteDestination.setText(route.destinazione);
        }

        switch (route.type) {
            case BUS:
            default:
                // convertView could contain another background, reset it
                vh.rowStopIcon.setBackgroundResource(busBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
                break;
            case LONG_DISTANCE_BUS:
                vh.rowStopIcon.setBackgroundResource(extraurbanoBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(busIcon, 0, 0, 0);
                break;
            case METRO:
                vh.rowStopIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                vh.rowStopIcon.setBackgroundResource(metroBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(trainIcon, 0, 0, 0);
                break;
            case RAILWAY:
                vh.rowStopIcon.setBackgroundResource(busBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(trainIcon, 0, 0, 0);
                break;
            case TRAM: // never used but whatever.
                vh.rowStopIcon.setBackgroundResource(busBg);
                vh.rowRouteDestination.setCompoundDrawablesWithIntrinsicBounds(tramIcon, 0, 0, 0);
                break;
        }

        List<Passaggio> passaggi = route.passaggi;
        if(passaggi.size() == 0) {
            vh.rowRouteTimetable.setText(R.string.no_passages);
        } else {
            String resultString = "";
            for(Passaggio passaggio : passaggi) {
                // "+" calls concat() and some other stuff internally, this should be faster
                resultString = resultString.concat(passaggio.toString()).concat(" ");
            }
            vh.rowRouteTimetable.setText(resultString);
        }

        return convertView;
    }
}
