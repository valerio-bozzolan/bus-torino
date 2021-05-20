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
