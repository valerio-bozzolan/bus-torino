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
package it.reyboz.bustorino.layouts;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import it.reyboz.bustorino.lab.GTTSiteSucker.BusLine;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.R;

public class BusListView extends ListView {

    public BusListView(Context context) {
        super(context);
    }

    public BusListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BusListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void showBusLines(BusLine[] busLines, Context c) {
        // Populate the stupid ListView SimpleAdapter
        ArrayList<HashMap<String, Object>> entries = new ArrayList<HashMap<String, Object>>();
        for (BusLine busLine : busLines) {
            HashMap<String, Object> entry = new HashMap<String, Object>();
            String passages = busLine.getTimePassagesString();
            if (passages == null) {
                passages = c.getString(R.string.no_passages);
            }
            entry.put("icon", busLine.getBusLineName());
            entry.put("passages", passages);
            entries.add(entry);
        }

        // Show results using the stupid SimpleAdapter
        String[] from = { "icon", "passages" };
        int[] to = { R.id.busLineIcon, R.id.busLineNames };
        SimpleAdapter adapter = new SimpleAdapter(c,
                entries, R.layout.entry_bus_line_passage, from, to);
        setAdapter(adapter);

        invalidate();
    }

    public void showBusStops(BusStop[] busStops, Context c) {
        // Populate the stupid ListView SimpleAdapter
        ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();
        for(int i=0; i<busStops.length; i++) {
            HashMap<String, Object> singleEntry = new HashMap<String, Object>();

            busStops[i].orderBusLinesByName();

            int busStopID = busStops[i].getBusStopID();
            String busStopName = busStops[i].getBusStopName();
            String busLineNames = busStops[i].toString();

            singleEntry.put("bus-stop-ID", busStopID);
            singleEntry.put("bus-stop-name", busStopName);
            singleEntry.put("bus-line-names", String.format(c.getResources()
                    .getString(R.string.lines), "\n" + busLineNames));
            data.add(singleEntry);
        }
        String[] from = { "bus-stop-ID", "bus-stop-name", "bus-line-names" };
        int[] to = { R.id.busStopID, R.id.busStopName, R.id.busLineNames };
        SimpleAdapter adapter = new SimpleAdapter(c,
                data, R.layout.entry_bus_stop, from, to);
        setAdapter(adapter);
    }

}
