package it.reyboz.bustorino.middleware;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class GPSLocationAdapter implements LocationListener {
    private Context con;
    private LocationManager locMan;

    public GPSLocationAdapter(Context con) {
        this.con = con;
        locMan  = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
    }

    public void requestPosition(){

    }


    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

}
