package it.reyboz.bustorino.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.util.Log;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

public class Permissions {
    final static public String DEBUG_TAG = "BusTO -Permissions";

    final static public int PERMISSION_REQUEST_POSITION = 33;
    final static public String LOCATION_PERMISSION_GIVEN = "loc_permission";
    final static public int STORAGE_PERMISSION_REQ = 291;

    final static public int PERMISSION_OK = 0;
    final static public int PERMISSION_ASKING = 11;
    final static public int PERMISSION_NEG_CANNOT_ASK = -3;

    final static public String[] LOCATION_PERMISSIONS={Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION};

    public static boolean anyLocationProviderMatchesCriteria(LocationManager mng, Criteria cr, boolean enabled) {
        List<String> providers = mng.getProviders(cr, enabled);
        Log.d(DEBUG_TAG, "Getting enabled location providers: ");
        for (String s : providers) {
            Log.d(DEBUG_TAG, "Provider " + s);
        }
        return providers.size() > 0;
    }
    public static boolean isPermissionGranted(Context con,String permission){
        return ContextCompat.checkSelfPermission(con, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean locationPermissionGranted(Context con){
        return isPermissionGranted(con, Manifest.permission.ACCESS_FINE_LOCATION) &&
                isPermissionGranted(con, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static void assertLocationPermissions(Context con, Activity activity) {
        if(!isPermissionGranted(con, Manifest.permission.ACCESS_FINE_LOCATION) ||
                !isPermissionGranted(con,Manifest.permission.ACCESS_COARSE_LOCATION)){
            ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_POSITION);
        }
    }
}
