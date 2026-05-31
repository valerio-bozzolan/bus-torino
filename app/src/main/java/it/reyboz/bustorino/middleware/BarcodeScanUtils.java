package it.reyboz.bustorino.middleware;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Barrier;
import androidx.fragment.app.Fragment;

import java.util.List;

import it.reyboz.bustorino.R;

public class BarcodeScanUtils {


    public static boolean checkTargetPackageExists(Context context,Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !availableApps.isEmpty();
    }

    public static AlertDialog showDownloadDialog(@NonNull Context context) {

        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(context);
        downloadDialog.setTitle(R.string.title_barcode_scanner_install);
        downloadDialog.setMessage(R.string.message_install_barcode_scanner);
        downloadDialog.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
            final String packageName = BarcodeScanOptions.BINARY_EYE_PACKAGE;
            Uri uri = Uri.parse("market://details?id=" + packageName);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException anfe) {
                // Hmm, market is not installed
                Log.w("BusTO-BarcodeScanUtils", "Google Play is not installed; cannot install " + packageName);
                //Open the browser on the F-Droid web page
                intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://f-droid.org/en/packages/"+BarcodeScanOptions.BINARY_EYE_PACKAGE+"/"));
                context.startActivity(intent);
            }
        });
        downloadDialog.setNegativeButton(R.string.no, null);
        downloadDialog.setCancelable(true);
        return downloadDialog.show();
    }
}
