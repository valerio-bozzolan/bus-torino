/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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
    private TextView entryView = null;
    private TextView busLineIconTextView = null;
    private TextView busLinePassagesTextView = null;
    private TextView busLineVehicleIcon = null;
    private View theViewToUse = null;
    private static int layout = R.layout.entry_bus_line_passage;

    public PalinaAdapter(Context context, Palina p) {
        // TODO: find a more efficient way if there's one
        super(context, layout, p.queryAllRoutes());
    }

    /**
     * Some parts taken from the AdapterBusLines class, some parts inspired by this tutorial:
     * http://www.simplesoft.it/android/guida-agli-adapter-e-le-listview-in-android.html
     * And some other bits and bobs TIRATI FUORI DAL NULLA CON L'INTUIZIONE INTELLETTUALE PERCHÉ
     * SEMBRA CHE NESSUNO ABBIA LA MINIMA IDEA DI COME FUNZIONA UN ADAPTER SU ANDROID.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            if(this.theViewToUse == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(layout, null);
                this.theViewToUse = convertView;
            }
        } else {
            this.theViewToUse = convertView;
        }

        // ------------------- /!\ Don't use convertView beyond this point /!\ ---------------------

        if(this.entryView == null) {
            // I'd be surprised if this doesn't catch fire as soon as it runs.
            this.entryView = (TextView) this.theViewToUse.findViewById(R.id.busLineNames);
            this.busLineIconTextView = (TextView) this.theViewToUse.findViewById(R.id.busLineIcon);
            this.busLinePassagesTextView = (TextView) this.theViewToUse.findViewById(R.id.busLineNames);;
            this.busLineVehicleIcon = (TextView) this.theViewToUse.findViewById(R.id.vehicleIcon); // Vehicle icon
        }

        Route route = getItem(position);

        // Take the TextView from layout and set the busLine name
        // TODO: pezza temporanea da sistemare
        busLineIconTextView.setText(route.name + " > " + route.destinazione);

        List<Passaggio> passaggi = route.passaggi;
        if(passaggi.size() == 0) {
            this.busLinePassagesTextView.setText(R.string.no_passages);
            this.busLineVehicleIcon.setVisibility(View.INVISIBLE);
        } else {
            String resultString = "";
            for(Passaggio passaggio : passaggi) {
                // "+" calls concat() and some other stuff internally, this should be faster
                resultString = resultString.concat(passaggio.toString()).concat(" ");
            }
            this.busLinePassagesTextView.setText(resultString);
        }

        return this.theViewToUse;
    }
}
