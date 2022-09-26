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
import androidx.fragment.app.Fragment;

import java.util.List;

import it.reyboz.bustorino.R;

public class BarcodeScanUtils {


    public static boolean checkTargetPackageExists(Context context,Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (availableApps != null) {
            return !availableApps.isEmpty();
        }
        return false;
    }

    public static AlertDialog showDownloadDialog(@Nullable Activity activity,@Nullable final Fragment fragment) {
        if (activity == null){
            if (fragment==null) throw new IllegalArgumentException("Cannot put both activity and fragment null");
            activity = fragment.getActivity();
        }
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(activity);
        downloadDialog.setTitle(R.string.title_barcode_scanner_install);
        downloadDialog.setMessage(R.string.message_install_barcode_scanner);
        final  Activity finalActivity = activity;
        downloadDialog.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
            final String packageName = BarcodeScanOptions.BS_PACKAGE;
            Uri uri = Uri.parse("market://details?id=" + packageName);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                if (fragment == null) {
                    finalActivity.startActivity(intent);
                } else {
                    fragment.startActivity(intent);
                }
            } catch (ActivityNotFoundException anfe) {
                // Hmm, market is not installed
                Log.w("BusTO-BarcodeScanUtils", "Google Play is not installed; cannot install " + packageName);
            }
        });
        downloadDialog.setNegativeButton(R.string.no, null);
        downloadDialog.setCancelable(true);
        return downloadDialog.show();
    }
}
