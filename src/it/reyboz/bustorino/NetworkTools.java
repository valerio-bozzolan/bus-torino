package it.reyboz.bustorino;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public class NetworkTools {

    // Check network connection
    public static boolean isConnected(Context c){
        ConnectivityManager connMgr = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    // «Turn on WiFi!»
    public static void showNetworkError(Context c) {
    	Toast.makeText(c, R.string.network_error, Toast.LENGTH_SHORT).show();
    }
}