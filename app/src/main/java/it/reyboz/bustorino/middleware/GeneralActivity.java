/*
	BusTO - Arrival times for Turin public transport.
    Copyright (C) 2021 Fabio Mazza

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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.HashMap;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.PreferencesHolder;

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
        return PreferencesHolder.getMainSharedPreferences(this);
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
                if (num_trials != null && num_trials > 4)
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

    //KEYBOARD STUFF
    protected  View getRootView() {
        return findViewById(android.R.id.content);
    }

    /**
     * This method doesn't work, DO NOT USE
     * @return if the keyboard is open
     * TODO: fix this if you want
     */
    @Deprecated
    public Boolean isKeyboardOpen(){
        Rect visibleBounds = new Rect();
        this.getRootView().getWindowVisibleDisplayFrame(visibleBounds);

        double heightDiff = getRootView().getHeight() - visibleBounds.height();
        final double marginOfError = Math.round(utils.convertDipToPixels(this,50f));
        return heightDiff > marginOfError;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void setSystemBarAppearance(boolean isSystemInDarkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isSystemInDarkTheme) {
                if (getWindow() != null && getWindow().getInsetsController() != null) {
                    getWindow().getInsetsController().setSystemBarsAppearance(
                            0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } else {
                if (getWindow() != null && getWindow().getInsetsController() != null) {
                    getWindow().getInsetsController().setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        }
    }

    protected OnApplyWindowInsetsListener applyBottomAndBordersInsetsListener = (v, windowInsets) -> {
        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        // Apply the insets as a margin to the view. This solution sets only the
        // bottom, left, and right dimensions, but you can apply whichever insets are
        // appropriate to your layout. You can also update the view padding if that's
        // more appropriate.
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        mlp.leftMargin = insets.left;
        mlp.bottomMargin = insets.bottom;
        mlp.rightMargin = insets.right;
        v.setLayoutParams(mlp);
        //set for toolbar

        // Return CONSUMED if you don't want the window insets to keep passing
        // down to descendant views.
        return WindowInsetsCompat.CONSUMED;
    };

    protected OnApplyWindowInsetsListener applyBottomInsetsListener = (v, windowInsets) -> {
        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        // Apply the insets as a margin to the view. This solution sets only the
        // bottom, left, and right dimensions, but you can apply whichever insets are
        // appropriate to your layout. You can also update the view padding if that's
        // more appropriate.
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        mlp.bottomMargin = insets.bottom;
        v.setLayoutParams(mlp);
        //set for toolbar

        // Return CONSUMED if you don't want the window insets to keep passing
        // down to descendant views.
        return WindowInsetsCompat.CONSUMED;
    };

}
