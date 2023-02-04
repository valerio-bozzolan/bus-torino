/*
	BusTO - Map components
	Copyright (C) 2020 Andrea Ugo
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
package it.reyboz.bustorino.map;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;

import it.reyboz.bustorino.R;

public class CustomInfoWindow extends BasicInfoWindow {
    //TODO: Make the action on the Click customizable
    private final TouchResponder touchResponder;
    private final String stopID, name, routesStopping;
    //final DisplayMetrics metrics;

    @Override
    public void onOpen(Object item) {
        super.onOpen(item);
        TextView descr_textView = mView.findViewById(R.id.bubble_description);
        CharSequence text = descr_textView.getText();
        TextView titleTV = mView.findViewById(R.id.bubble_title);
        //Log.d("BusTO-MapInfoWindow", "Descrip: "+text+", title "+(titleTV==null? "null": titleTV.getText()));

        if (text==null || !text.toString().isEmpty()){
            descr_textView.setVisibility(View.VISIBLE);
        } else
            descr_textView.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mView.setElevation(3.2f);
        }
        TextView subDescriptTextView = mView.findViewById(R.id.bubble_subdescription);
        if (routesStopping!=null && !routesStopping.isEmpty()){
            subDescriptTextView.setText(routesStopping);
            subDescriptTextView.setVisibility(View.VISIBLE);
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    public CustomInfoWindow(MapView mapView, String stopID, String name, String routesStopping, TouchResponder responder) {
        // get the personalized layout
        super(R.layout.map_popup, mapView);
        touchResponder =responder;
        this.stopID = stopID;
        this.name = name;
        this.routesStopping = routesStopping;

        //metrics = Resources.getSystem().getDisplayMetrics();

        // make clickable
        mView.setOnTouchListener((View v, MotionEvent e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                // on click
                touchResponder.onActionUp(this.stopID, this.name);
            }
            return true;
        });
    }

    public interface TouchResponder{
        /**
         * React to a click on the stop View
         * @param stopID the stop id
         * @param stopName the stop name
         */
        void onActionUp(@NonNull String stopID, @Nullable String stopName);
    }
}
