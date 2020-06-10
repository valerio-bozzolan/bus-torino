package it.reyboz.bustorino.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import android.widget.TextView;
import org.osmdroid.api.IMapView;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;

import it.reyboz.bustorino.ActivityMain;
import it.reyboz.bustorino.R;

public class CustomInfoWindow extends BasicInfoWindow {

    @Override
    public void onOpen(Object item) {
        super.onOpen(item);

        TextView descr_textView = (TextView) mView.findViewById(R.id.bubble_description);
        CharSequence text = descr_textView.getText();
        if (text==null || !text.toString().isEmpty()){
            descr_textView.setVisibility(View.VISIBLE);
        } else
            descr_textView.setVisibility(View.GONE);

        mView.setElevation(3.2f);
    }

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
