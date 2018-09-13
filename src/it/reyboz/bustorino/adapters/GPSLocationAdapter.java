package it.reyboz.bustorino.adapters;

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
import android.util.Log;

public class GPSLocationAdapter implements LocationListener {
    private Context con;
    private LocationManager locMan;

    public GPSLocationAdapter(Context con) {
        this.con = con;
        locMan  = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean startRequestingPosition(){
        try {
            locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
            return true;
        } catch (SecurityException exc){
            exc.printStackTrace();
            return false;
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        Log.d("GPSLocationListener","found location:\nlat: "+location.getLatitude()+" lon: "+location.getLongitude()+"\naccuracy: "+location.getAccuracy());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        startRequestingPosition();
    }

    @Override
    public void onProviderDisabled(String provider) {
        locMan.removeUpdates(this);
    }

}
