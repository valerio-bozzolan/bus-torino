package it.reyboz.bustorino.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import it.reyboz.bustorino.BuildConfig;

import static android.content.Context.MODE_PRIVATE;

public abstract class ScreenBaseFragment extends Fragment {

    protected String PREF_FILE= BuildConfig.APPLICATION_ID+".fragment_prefs";

    protected void setOption(String optionName, boolean value) {
        Context mContext = getContext();
        SharedPreferences.Editor editor = mContext.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.commit();
    }

    protected boolean getOption(String optionName, boolean optDefault) {
        Context mContext = getContext();
        SharedPreferences preferences = mContext.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        return preferences.getBoolean(optionName, optDefault);
    }

    protected void showToastMessage(int messageID, boolean short_lenght) {
        final int length = short_lenght ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast.makeText(getContext(), messageID, length).show();
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
     * @return
     */
    @Nullable
    public abstract View getBaseViewForSnackBar();
}
