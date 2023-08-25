package it.reyboz.bustorino.map;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Property;


import android.view.animation.LinearInterpolator;
import it.reyboz.bustorino.R;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

public class MarkerUtils {

    public static final int LINEAR_ANIMATION = 1;

    /* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html */
    public static ObjectAnimator makeMarkerAnimator(final MapView map, Marker marker, GeoPoint finalPosition, int animationType, int durationMs) {

        GeoPointInterpolator interpolator;
        switch (animationType){
            case LINEAR_ANIMATION:
                interpolator = new GeoPointInterpolator.Linear();
                break;
            default:
                throw new IllegalArgumentException("Value "+animationType+ " for animationType is invalid");
        }
        TypeEvaluator<GeoPoint> typeEvaluator = (fraction, startValue, endValue) ->
                interpolator.interpolate(fraction, startValue, endValue);
        Property<Marker, GeoPoint> property = Property.of(Marker.class, GeoPoint.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition);
        switch (animationType){
            case LINEAR_ANIMATION:

                animator.setInterpolator(new LinearInterpolator());
            default:
        }
        animator.setDuration(durationMs);
        //animator.start();
        return animator;
    }

    public static Marker makeMarker(GeoPoint geoPoint, String stopID, String stopName,
                             String routesStopping,
                             MapView  map,
                             CustomInfoWindow.TouchResponder responder,
                             Drawable icon,
                                    int infoWindowLayout,
                                    int titleColorId) {

        // add a marker
        final Marker marker = new Marker(map);

        // set custom info window as info window
        CustomInfoWindow popup = new CustomInfoWindow(map, stopID, stopName, routesStopping, responder, infoWindowLayout, titleColorId);
        marker.setInfoWindow(popup);

        // make the marker clickable
        marker.setOnMarkerClickListener((thisMarker, mapView) -> {
            if (thisMarker.isInfoWindowOpen()) {
                // on second click
                Log.w("BusTO-OsmMap", "Pressed on the click marker");
            } else {
                // on first click

                // hide all opened info window
                InfoWindow.closeAllInfoWindowsOn(map);
                // show this particular info window
                thisMarker.showInfoWindow();
                // move the map to its position
                map.getController().animateTo(thisMarker.getPosition());
            }

            return true;
        });

        // set its position
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // add to it an icon
        //marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));

        marker.setIcon(icon);
        // add to it a title
        marker.setTitle(stopName);
        // set the description as the ID
        marker.setSnippet(stopID);

        // show popup info window of the searched marker
        /*if (isStartMarker) {
            marker.showInfoWindow();
            //map.getController().animateTo(marker.getPosition());
        }*/

        return marker;
    }
}
