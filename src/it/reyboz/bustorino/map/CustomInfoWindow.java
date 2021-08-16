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
    private final String stopID, name;

    @Override
    public void onOpen(Object item) {
        super.onOpen(item);
        TextView descr_textView = (TextView) mView.findViewById(R.id.bubble_description);
        CharSequence text = descr_textView.getText();
        if (text==null || !text.toString().isEmpty()){
            descr_textView.setVisibility(View.VISIBLE);
        } else
            descr_textView.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mView.setElevation(3.2f);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public CustomInfoWindow(MapView mapView, String stopID, String name, TouchResponder responder) {
        // get the personalized layout
        super(R.layout.map_popup, mapView);
        touchResponder =responder;
        this.stopID = stopID;
        this.name = name;

        // make clickable
        mView.setOnTouchListener((View v, MotionEvent e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                // on click
                touchResponder.onActionUp(stopID, name);
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
