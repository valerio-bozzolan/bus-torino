package it.reyboz.bustorino.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import it.reyboz.bustorino.R
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.AtomicInt

class Permissions private constructor(private val appContext: Context) {

    /*
    @get:RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    val notificationPermissions: Array<String>
        //final static public String[] NOTIFICATION_PERMISSION={Manifest.permission.POST_NOTIFICATIONS};
        get() = arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS)

     */
    private var askedTimesLocation = AtomicInteger(0)


    fun checkRequestLocationPermissions(activity: Activity, launcher: ActivityResultLauncher<Array<String>>): Boolean {

         //activity.getSharedPreferences(, Context.MODE_PRIVATE)
        var launched = false
        if(shouldShowRequestPermissionRationale(activity,Manifest.permission.ACCESS_FINE_LOCATION)){
            Toast.makeText(activity, R.string.enable_position_message_map, Toast.LENGTH_LONG).show()
        } /*else{
            //cannot show the dialog anymore, go to the settings
            openShowAppSettingsLocationDialog()
        }
        */
        val reqTimes = askedTimesLocation.getAndIncrement()
        Log.d(DEBUG_TAG, "Requesting location permissions, asked ${reqTimes} times ")
        if(reqTimes > 4){
            openShowAppSettingsLocationDialog()
        } else{
            launcher.launch(LOCATION_PERMISSIONS)
            launched = true
        }
        return launched
    }

    /**
     * Show alert dialog to enable location permission
     */
    fun openShowAppSettingsLocationDialog() {
        val context = appContext
        val builder = AlertDialog.Builder(context)

        builder.setTitle(R.string.no_permission_dialog_title)
        builder.setMessage(R.string.no_permission_dialog_text_location)
        builder.setPositiveButton(
            R.string.no_permission_dialog_open,
            DialogInterface.OnClickListener { dialogInterface: DialogInterface?, i: Int ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData(Uri.fromParts("package", context.getPackageName(), null))
                context.startActivity(intent)
            })
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }


    fun assertLocationPermissions(con: Context, activity: Activity) {
        if (!isPermissionGranted(con, Manifest.permission.ACCESS_FINE_LOCATION) ||
            !isPermissionGranted(con, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_POSITION
            )
        }
    }



    companion object{
        const val DEBUG_TAG: String = "BusTO -Permissions"

        const val PERMISSION_REQUEST_POSITION: Int = 33
        const val LOCATION_PERMISSION_GIVEN: String = "loc_permission"
        const val STORAGE_PERMISSION_REQ: Int = 291

        const val PERMISSION_OK: Int = 0
        const val PERMISSION_ASKING: Int = 11
        const val PERMISSION_NEG_CANNOT_ASK: Int = -3

        @JvmField
        val LOCATION_PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        @JvmStatic
        fun isPermissionGranted(con: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(con, permission) == PackageManager.PERMISSION_GRANTED
        }
        @JvmStatic
        fun bothLocationPermissionsGranted(con: Context): Boolean {
            return isPermissionGranted(con, Manifest.permission.ACCESS_FINE_LOCATION) &&
                    isPermissionGranted(con, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        @JvmStatic
        fun anyLocationPermissionsGranted(con: Context): Boolean {
            return isPermissionGranted(con, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    isPermissionGranted(con, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        /**
         * Check if the system requires the POST_NOTIFICATION permission to send notifications
         * @return true if required
         */
        @JvmStatic
        fun isNotificationPermissionNeeded() = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)



        @Volatile
        private var instance: Permissions? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: Permissions(context.applicationContext).also { instance = it }
            }

    }
}
