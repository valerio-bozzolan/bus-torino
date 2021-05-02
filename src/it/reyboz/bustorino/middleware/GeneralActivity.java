package it.reyboz.bustorino.middleware;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.HashMap;

import it.reyboz.bustorino.R;

/**
 * Activity class that contains all the generally useful methods
 */
public abstract class GeneralActivity extends AppCompatActivity {
    final static protected int PERMISSION_REQUEST_POSITION = 33;
    final static protected String LOCATION_PERMISSION_GIVEN = "loc_permission";
    final static protected int STORAGE_PERMISSION_REQ = 291;

    final static protected int PERMISSION_OK = 0;
    final static protected int PERMISSION_ASKING = 11;
    final static protected int PERMISSION_NEG_CANNOT_ASK = -3;

    final static private String DEBUG_TAG = "BusTO-GeneralAct";

    /*
     * Permission stuff
     */
    protected HashMap<String,Runnable> permissionDoneRunnables = new HashMap<>();
    protected HashMap<String,Integer> permissionAsked = new HashMap<>();

    protected void setOption(String optionName, boolean value) {
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.commit();
    }

    protected boolean getOption(String optionName, boolean optDefault) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        return preferences.getBoolean(optionName, optDefault);
    }

    protected SharedPreferences getMainSharedPreferences(){
        return getSharedPreferences(getString(R.string.mainSharedPreferences),MODE_PRIVATE);
    }
    public void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public void showToastMessage(int messageID, boolean short_lenght) {
        final int length = short_lenght ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast.makeText(getApplicationContext(), messageID, length).show();
    }

    public int askForPermissionIfNeeded(String permission, int requestID){

        if(ContextCompat.checkSelfPermission(getApplicationContext(),permission)==PackageManager.PERMISSION_GRANTED){
            return PERMISSION_OK;
        }
        //need to ask for the permission
        //consider scenario when we have already asked for permission
        boolean alreadyAsked = false;
        Integer num_trials = 0;
        synchronized (this){
            if (permissionAsked.containsKey(permission)){
                num_trials = permissionAsked.get(permission);
                if (num_trials != null && num_trials > 3)
                    alreadyAsked = true;

            }
        }
        Log.d(DEBUG_TAG,"Already asked for permission: "+permission+" -> "+num_trials);

        if(!alreadyAsked){
            ActivityCompat.requestPermissions(this,new String[]{permission}, requestID);
            synchronized (this){
                if (num_trials!=null){
                    permissionAsked.put(permission, num_trials+1);
                }
            }
            return PERMISSION_ASKING;
        } else {

            return PERMISSION_NEG_CANNOT_ASK;
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
