/*
	BusTO (middleware)
    Copyright (C) 2019 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.reyboz.bustorino.middleware;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import it.reyboz.bustorino.util.LocationCriteria;
import it.reyboz.bustorino.util.Permissions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Singleton class used to access location. Possibly extended with other location sources.
 */
public class AppLocationManager implements LocationListener {

    public static final int LOCATION_GPS_AVAILABLE = 22;
    public static final int LOCATION_UNAVAILABLE = -22;

    private final Context appContext;
    private final LocationManager locMan;
    public static final String DEBUG_TAG = "BUSTO LocAdapter";
    private final String BUNDLE_LOCATION =  "location";
    private static AppLocationManager instance;
    private int oldGPSLocStatus = LOCATION_UNAVAILABLE;
    private int minimum_time_milli = -1;

    private boolean isLocationPermissionGiven = false;

    private final ArrayList<WeakReference<LocationRequester>> requestersRef = new ArrayList<>();


    private AppLocationManager(Context context) {
        this.appContext = context.getApplicationContext();
        locMan  = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        isLocationPermissionGiven = checkLocationPermission(context);
    }

    public static AppLocationManager getInstance(Context con) {
        if(instance==null) instance = new AppLocationManager(con);
        return instance;
    }

    public static boolean checkLocationPermission(Context context){
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }
    

    private void requestGPSPositionUpdates() throws SecurityException{
        final int timeinterval = (minimum_time_milli>0 && minimum_time_milli<Integer.MAX_VALUE)? minimum_time_milli : 2000;

        locMan.removeUpdates(this);
        locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, timeinterval, 5, this);

    }
    private void cleanAndUpdateRequesters(){
        minimum_time_milli = Integer.MAX_VALUE;
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) iter.remove();
            else {
                minimum_time_milli = Math.min(cReq.getLocationCriteria().getTimeInterval(),minimum_time_milli);
            }
        }
        Log.d(DEBUG_TAG,"Updated requesters, got "+requestersRef.size()+" listeners to update every "+minimum_time_milli+" ms at least");
    }


    public void addLocationRequestFor(LocationRequester req){
        boolean present = false;
        minimum_time_milli = Integer.MAX_VALUE;
        int countNull = 0;
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) {
                countNull++;
                iter.remove();
            } else if(cReq.equals(req)){
                present = true;
                minimum_time_milli = Math.min(cReq.getLocationCriteria().getTimeInterval(),minimum_time_milli);
            }
        }
        Log.d(DEBUG_TAG, countNull+" listeners have been removed because null");
        if(!present) {
            WeakReference<LocationRequester> newref = new WeakReference<>(req);
            requestersRef.add(newref);
            minimum_time_milli = Math.min(req.getLocationCriteria().getTimeInterval(),minimum_time_milli);
            Log.d(DEBUG_TAG,"Added new stop requester, instance of "+req.getClass().getSimpleName());
        }
        if(requestersRef.size()>0){
            Log.d(DEBUG_TAG,"Requesting location updates");
            requestGPSPositionUpdates();

        }

    }
    public void removeLocationRequestFor(LocationRequester req){
        minimum_time_milli = Integer.MAX_VALUE;
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null || cReq.equals(req)) iter.remove();
            else {
                minimum_time_milli = Math.min(cReq.getLocationCriteria().getTimeInterval(),minimum_time_milli);
            }
        }
        if(requestersRef.size()<=0){
            locMan.removeUpdates(this);
        } else {
            requestGPSPositionUpdates();
        }
    }
    private void sendLocationStatusToAll(int status){
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) iter.remove();
            else cReq.onLocationStatusChanged(status);
        }
    }
    public boolean isRequesterRegistered(LocationRequester requester){
        for(WeakReference<LocationRequester> regRef: requestersRef){
            if(regRef.get()!=null && regRef.get() ==requester) return true;
        }
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(DEBUG_TAG,"found location:\nlat: "+location.getLatitude()+" lon: "+location.getLongitude()+"\naccuracy: "+location.getAccuracy());
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        int new_min_interval = Integer.MAX_VALUE;
        while(iter.hasNext()){
            final LocationRequester requester = iter.next().get();
            if(requester==null) iter.remove();
            else{
                final long timeNow = System.currentTimeMillis();
                final LocationCriteria criteria = requester.getLocationCriteria();
                if(location.getAccuracy()<criteria.getMinAccuracy() &&
                            (timeNow - requester.getLastUpdateTimeMillis())>criteria.getTimeInterval()){
                    requester.onLocationChanged(location);
                    Log.d("AppLocationManager","Updating position for instance of requester "+requester.getClass().getSimpleName());
                }
                //update minimum time interval
                new_min_interval = Math.min(requester.getLocationCriteria().getTimeInterval(),new_min_interval);
            }
        }
        minimum_time_milli = new_min_interval;
        if(requestersRef.size()==0){
            //stop requesting the position
            locMan.removeUpdates(this);
        }

        
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //IF ANOTHER LOCATION SOURCE IS READY, USE IT
        //OTHERWISE, SIGNAL THAT WE HAVE NO LOCATION
        if(oldGPSLocStatus !=status){
            if(status == LocationProvider.OUT_OF_SERVICE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                   sendLocationStatusToAll(LOCATION_UNAVAILABLE);
            }else if(status == LocationProvider.AVAILABLE){
                sendLocationStatusToAll(LOCATION_GPS_AVAILABLE);
            }
                oldGPSLocStatus = status;
            }
        Log.d(DEBUG_TAG, "Provider status changed: "+provider+" status: "+status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        cleanAndUpdateRequesters();
        requestGPSPositionUpdates();
        Log.d(DEBUG_TAG, "Provider: "+provider+" enabled");
        for(WeakReference<LocationRequester> req: requestersRef){
            if(req.get()==null) continue;
            req.get().onLocationProviderAvailable();
        }

    }

    @Override
    public void onProviderDisabled(String provider) {
        cleanAndUpdateRequesters();
        for(WeakReference<LocationRequester> req: requestersRef){
            if(req.get()==null) continue;
            req.get().onLocationDisabled();
        }
        //locMan.removeUpdates(this);
        Log.d(DEBUG_TAG, "Provider: "+provider+" disabled");
    }

    public boolean anyLocationProviderMatchesCriteria(Criteria cr) {
        return Permissions.anyLocationProviderMatchesCriteria(locMan, cr, true);
    }
    /**
     * Interface to be implemented to get the location request
     */
    public interface LocationRequester{
        /**
         * Do something with the newly obtained location
         * @param loc the obtained location
         */
        void onLocationChanged(Location loc);

        /**
         * Inform the requester that the GPS status has changed
         * @param status new status
         */
        void onLocationStatusChanged(int status);

        /**
         * We have a location provider available
         */
        void onLocationProviderAvailable();
        /**
         * Called when location is disabled
         */
        void onLocationDisabled();


        /**
         * Give the last time of update the requester has
         * Set it to -1 in order to receive each new location
         * @return the time for update in milliseconds since epoch
         */
        long getLastUpdateTimeMillis();

        /**
         * Get the specifications for the location
         * @return fully parsed LocationCriteria
         */
        LocationCriteria getLocationCriteria();
    }
}
