/*
	BusTO  - Fragments components
    Copyright (C) 2018-2026 Fabio Mazza

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
package it.reyboz.bustorino.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.android.material.snackbar.Snackbar;
import it.reyboz.bustorino.BuildConfig;

import java.util.Map;
import java.util.function.Consumer;

import static android.content.Context.MODE_PRIVATE;

public abstract class ScreenBaseFragment extends Fragment {

    protected final static String PREF_FILE= BuildConfig.APPLICATION_ID+".fragment_prefs";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }



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

    protected void showToastMessage(int messageID, boolean shortT) {
        final int length = shortT ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final Context context = getContext();
        if(context!=null)
            Toast.makeText(context, messageID, length).show();
    }
    protected void makeToast(String message){
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    protected void makeToast(int messageID){
        Toast.makeText(getContext(), messageID, Toast.LENGTH_SHORT).show();
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
    public ActivityResultLauncher<String[]> getPositionRequestLauncher(Consumer<Boolean> listener){
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

                listener.accept(coarseGranted && fineGranted);
            }
        });
    }

    protected FlexboxLayoutManager getFlexLayoutManager(@NonNull Context context) {
        var layoutManager = new FlexboxLayoutManager(context);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setJustifyContent(JustifyContent.FLEX_START);

        return layoutManager;
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
    public static void applyBottomInsetAsPadding(ViewGroup scrollableView) {
        final int originalPaddingBottom = scrollableView.getPaddingBottom();
        scrollableView.setClipToPadding(false); // ora lo trova
        ViewCompat.setOnApplyWindowInsetsListener(scrollableView, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(
                    v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), originalPaddingBottom + bars.bottom
            );
            return insets;
        });
    }


    public interface LocationRequestListener{
        void onPermissionResult(boolean locationGranted);
    }

}
