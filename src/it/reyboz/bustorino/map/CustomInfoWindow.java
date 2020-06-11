package it.reyboz.bustorino.map;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;

import it.reyboz.bustorino.ActivityMain;
import it.reyboz.bustorino.R;

public class CustomInfoWindow extends BasicInfoWindow {

    @SuppressLint("ClickableViewAccessibility")
    public CustomInfoWindow(MapView mapView, String ID, String stopName) {
        // get the personalized layout
        super(R.layout.map_popup, mapView);

        // make clickable
        mView.setOnTouchListener((View v, MotionEvent e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                // on click

                // create an intent with these extras
                Intent intent = new Intent(mapView.getContext(), ActivityMain.class);
                Bundle b = new Bundle();
                b.putString("bus-stop-ID", ID);
                b.putString("bus-stop-display-name", stopName);
                intent.putExtras(b);
                intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

                // start ActivityMain with the previous intent
                mapView.getContext().startActivity(intent);
            }
            return true;
        });
    }

}
