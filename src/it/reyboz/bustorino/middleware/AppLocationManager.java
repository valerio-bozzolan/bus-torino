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

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import it.reyboz.bustorino.util.LocationCriteria;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Singleton class used to access location. Possibly extended with other location sources.
 */
public class AppLocationManager implements LocationListener {

    public static final int LOCATION_GPS_AVAILABLE = 22;
    public static final int LOCATION_UNAVAILABLE = -22;

    private Context con;
    private LocationManager locMan;
    public static final String DEBUG_TAG = "BUSTO LocAdapter";
    private final String BUNDLE_LOCATION =  "location";
    private static AppLocationManager instance;
    private int oldGPSLocStatus = LOCATION_UNAVAILABLE;

    private ArrayList<WeakReference<LocationRequester>> requestersRef = new ArrayList<>();


    private AppLocationManager(Context con) {
        this.con = con;
        locMan  = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
    }

    public static AppLocationManager getInstance(Context con) {
        if(instance==null) instance = new AppLocationManager(con.getApplicationContext());
        return instance;
    }
    

    private void startRequestingPosition(){
        try {
            locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
        } catch (SecurityException exc){
            exc.printStackTrace();
            Toast.makeText(con,"Cannot access GPS location",Toast.LENGTH_SHORT).show();
        }
    }
    private void cleanRequesters(){
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) iter.remove();
        }
    }


    public void addLocationRequestFor(LocationRequester req){
        boolean present = false;
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) iter.remove();
            else if(cReq.equals(req)){
                present = true;
            }
        }
        if(!present) {
            WeakReference<LocationRequester> newref = new WeakReference<>(req);
            requestersRef.add(newref);
        }
        if(requestersRef.size()>0){
            startRequestingPosition();
        }

    }
    public void removeLocationRequestFor(LocationRequester req){
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null || cReq.equals(req)) iter.remove();
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

    @Override
    public void onLocationChanged(Location location) {
        Log.d("GPSLocationListener","found location:\nlat: "+location.getLatitude()+" lon: "+location.getLongitude()+"\naccuracy: "+location.getAccuracy());
        ListIterator<WeakReference<LocationRequester>> iter = requestersRef.listIterator();
        while(iter.hasNext()){
            final LocationRequester cReq = iter.next().get();
            if(cReq==null) iter.remove();
            else{
                final LocationCriteria cr = cReq.getLocationCriteria();
                if(location.getAccuracy()<cr.getMinAccuracy()){
                    cReq.onLocationChanged(location);
                }

            }
        }
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
    }

    @Override
    public void onProviderEnabled(String provider) {
        startRequestingPosition();
    }

    @Override
    public void onProviderDisabled(String provider) {
        locMan.removeUpdates(this);
    }


    public interface LocationRequester{
        void onLocationChanged(Location loc);
        void onLocationStatusChanged(int status);
        LocationCriteria getLocationCriteria();
    }
}
