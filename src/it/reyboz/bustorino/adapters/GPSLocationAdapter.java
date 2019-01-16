package it.reyboz.bustorino.adapters;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.util.Log;

import java.util.ArrayList;

public class GPSLocationAdapter implements LocationListener {
    private Context con;
    private LocationManager locMan;
    public static final String DEBUG_TAG = "BUSTO LocAdapter";
    private final String BUNDLE_LOCATION =  "location";

    private int oldLocStatus = -2;
    private float maxAccuracy;
    private ArrayList<Integer> loadersIDsToNotify;

    LoaderManager.LoaderCallbacks<Cursor> callbacks;
    
    public GPSLocationAdapter(Context con,float maxAccuracyinMeters) {
        this.con = con;
        this.maxAccuracy = maxAccuracyinMeters;
        locMan  = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
    }
    

    boolean startRequestingPosition(){
        try {
            locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
            return true;
        } catch (SecurityException exc){
            exc.printStackTrace();
            return false;
        }
    }

    public float getMaxAccuracy() {
        return maxAccuracy;
    }

    public void setMaxAccuracy(float maxAccuracy) {
        this.maxAccuracy = maxAccuracy;
    }

    public void addLoaderToPing(int loaderID){
        if(!loadersIDsToNotify.contains(loaderID)){
            loadersIDsToNotify.add(loaderID);
        } else Log.d(DEBUG_TAG,"Requested to add loader "+loaderID+" which is already there");
    }
    public void removeLoaderToPing(int loaderID){
        if(loadersIDsToNotify.contains(loaderID)){
            loadersIDsToNotify.remove(loaderID);
        } else Log.d(DEBUG_TAG,"Requested to remove loader "+loaderID+" which is not present");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("GPSLocationListener","found location:\nlat: "+location.getLatitude()+" lon: "+location.getLongitude()+"\naccuracy: "+location.getAccuracy());
        
        float accu = location.getAccuracy();
        if(accu< maxAccuracy){
            Bundle msgBundle = new Bundle();
            msgBundle.putParcelable(BUNDLE_LOCATION,location);

        }
        
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
