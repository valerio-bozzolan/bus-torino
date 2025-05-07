package it.reyboz.bustorino.backend;


public class GPSPoint {

    public final double latitude;
    public final double longitude;

    public GPSPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getLatitudeE6() {
        return (int) (latitude*1e6d);
    }

    public int getLongitudeE6() {
        return (int) (longitude*1e6d);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
