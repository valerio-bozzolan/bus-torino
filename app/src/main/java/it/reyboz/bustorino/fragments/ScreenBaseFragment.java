package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import it.reyboz.bustorino.BuildConfig;

import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public abstract class ScreenBaseFragment extends Fragment {

    protected final static String PREF_FILE= BuildConfig.APPLICATION_ID+".fragment_prefs";

    protected void setOption(String optionName, boolean value) {
        Context mContext = getContext();
        assert mContext != null;
        SharedPreferences.Editor editor = mContext.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.commit();
    }

    protected boolean getOption(String optionName, boolean optDefault) {
        Context mContext = getContext();
        assert mContext != null;
        return getOption(mContext, optionName, optDefault);
    }

    protected void showToastMessage(int messageID, boolean short_lenght) {
        final int length = short_lenght ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final Context context = getContext();
        if(context!=null)
            Toast.makeText(context, messageID, length).show();
    }

    public void hideKeyboard() {
        if (getActivity()==null) return;
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**
     * Find the view on which the snackbar should be shown
     * @return a view or null if you don't want the snackbar shown
     */
    @Nullable
    public abstract View getBaseViewForSnackBar();

    /**
     * Empty method to override properties of the Snackbar before showing it
     * @param snackbar the Snackbar to be possibly modified
     */
    public void setSnackbarPropertiesBeforeShowing(Snackbar snackbar){

    }
    public boolean showSnackbarOnDBUpdate() {
        return true;
    }

    public static boolean getOption(Context context, String optionName, boolean optDefault){
        SharedPreferences preferences = context.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        return preferences.getBoolean(optionName, optDefault);
    }
    public static void setOption(Context context,String optionName, boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.apply();
    }
    public ActivityResultLauncher<String[]> getPositionRequestLauncher(LocationRequestListener listener){
        return registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if (result == null) return;

                if (result.get(Manifest.permission.ACCESS_COARSE_LOCATION) == null ||
                        result.get(Manifest.permission.ACCESS_FINE_LOCATION) == null)
                    return;
                final boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                final boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                if (coarseGranted != fineGranted){
                    Log.e("BusTO-ScreenBaseFragment", "the two permissions have different values, coarse "+
                            coarseGranted +", fineGranted "+fineGranted);
                }

                listener.onPermissionResult(coarseGranted || fineGranted);
            }
        });
    }
    /*protected void runActionFavorites(@NonNull Stop s, @NonNull FavoritesChangeWorker.Action action, @NonNull FavoritesChangeWorker.Companion.ResultListener resultListener){
        Context mContext = requireContext();

        WorkManager workManager = WorkManager.getInstance(mContext);

        WorkRequest req = FavoritesChangeWorker.makeRequest(s, action);
        workManager.enqueue(req);
        Context appContext = mContext.getApplicationContext();

        //FavoritesChangeWorker.registerListener(mContext, getViewLifecycleOwner(), s, action, resultListener);
        workManager.getWorkInfosByTagLiveData(FavoritesChangeWorker.getTag(s, action))
                .observe(getViewLifecycleOwner(), wi -> {
                    Log.d("BusTO-BaseFragment", "workinfo for stop "+s.ID+" has arrived");
                    if(wi.isEmpty()){
                        return;
                    }
                    WorkInfo workInfo = wi.get(wi.size() - 1);
                    Data progress = wi.get(wi.size()-1).getProgress();

                    int actvalue = progress.getInt(ACTION_ARG,-1);
                    boolean done = progress.getBoolean(DONE_ARG, false);
                    if (done) {
                        // at this point the action should be just ADD or REMOVE

                        if (actvalue == FavoritesChangeWorker.Action.ADD.getValue()) {
                            // now added
                            Toast.makeText(appContext, R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
                        } else if (actvalue == FavoritesChangeWorker.Action.REMOVE.getValue()) {
                            // now removed
                            Toast.makeText(appContext, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // wtf
                        Toast.makeText(appContext, R.string.cant_add_to_favorites, Toast.LENGTH_SHORT).show();
                    }
                    Log.d("busTO-ScreenBaseFragm", "favorites action="+actvalue+ ",done="+done);

                    // aggiorna UI
                    resultListener.doStuffWithResult(done);
                });
    }

     */

    public interface LocationRequestListener{
        void onPermissionResult(boolean locationGranted);
    }

}
