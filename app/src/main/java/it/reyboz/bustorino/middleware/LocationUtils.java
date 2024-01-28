package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.ContextCompat;

public class LocationUtils {

    public static LocationManager getSystemLocationManager(Context context){
        return ContextCompat.getSystemService(context, LocationManager.class);
    }

    //thanks to https://stackoverflow.com/questions/10311834/how-to-check-if-location-services-are-enabled
    public static Boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is a new method provided in API 28
            LocationManager lm = getSystemLocationManager(context);
            return lm.isLocationEnabled();
        } else {
            // This was deprecated in API 28
            int mode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }
}
