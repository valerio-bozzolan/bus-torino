package it.reyboz.bustorino.backend;

import org.osmdroid.api.IGeoPoint;

public class GPSPoint implements IGeoPoint {

    public final double latitude;
    public final double longitude;

    public GPSPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public int getLatitudeE6() {
        return (int) (latitude*1e6d);
    }

    @Override
    public int getLongitudeE6() {
        return (int) (longitude*1e6d);
    }

    @Override
    public double getLatitude() {
        return latitude;
    }

    @Override
    public double getLongitude() {
        return longitude;
    }
}
