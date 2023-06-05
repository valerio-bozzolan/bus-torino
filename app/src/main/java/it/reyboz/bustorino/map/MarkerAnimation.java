package it.reyboz.bustorino.map;

/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html */


        import android.animation.ObjectAnimator;
        import android.animation.TypeEvaluator;
        import android.util.Property;

        import org.osmdroid.util.GeoPoint;
        import org.osmdroid.views.MapView;
        import org.osmdroid.views.overlay.Marker;

public class MarkerAnimation {


    public static ObjectAnimator makeMarkerAnimator(final MapView map, Marker marker, GeoPoint finalPosition, final GeoPointInterpolator GeoPointInterpolator, int durationMs) {
        TypeEvaluator<GeoPoint> typeEvaluator = new TypeEvaluator<GeoPoint>() {
            @Override
            public GeoPoint evaluate(float fraction, GeoPoint startValue, GeoPoint endValue) {
                return GeoPointInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, GeoPoint> property = Property.of(Marker.class, GeoPoint.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(durationMs);
        //animator.start();
        return animator;
    }
}
