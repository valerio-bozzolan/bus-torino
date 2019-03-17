package it.reyboz.bustorino.middleware;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import it.reyboz.bustorino.R;

/**
 * Activity class that contains all the generally useful methods
 */
public abstract class GeneralActivity extends AppCompatActivity {
    final protected int PERMISSION_REQUEST_POSITION = 33;

    protected void setOption(String optionName, boolean value) {
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.commit();
    }

    protected boolean getOption(String optionName, boolean optDefault) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        return preferences.getBoolean(optionName, optDefault);
    }
    public void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    public boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
    public abstract void showMessage(int messageID);
    public abstract void showKeyboard();

    public void assertLocationPermissions() {
        if(ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_POSITION);
        }
    }
    public void createSnackbar(int ViewID, String message,int duration){
        Snackbar.make(findViewById(ViewID),message,duration);
    }
    /*
    METHOD THAT MIGHT BE USEFUL LATER
    public void assertPermissions(String[] permissions){
        ArrayList<String> permissionstoRequest = new ArrayList<>();

        for(int i=0;i<permissions.length;i++){
            if(ContextCompat.checkSelfPermission(getApplicationContext(),permissions[i])!=PackageManager.PERMISSION_GRANTED){
                permissionstoRequest.add(permissions[i]);
            }
        }

        ActivityCompat.requestPermissions(this,permissionstoRequest.toArray(new String[permissionstoRequest]));
    }
    */
}
