/*
	BusTO  - Fragments components
    Copyright (C) 2020 Fabio Mazza

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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.*;
import it.reyboz.bustorino.R;

import java.lang.ref.WeakReference;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getName();

    private static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";
    //private static final
    Handler mHandler;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mHandler = new Handler();
        return super.onCreateView(inflater, container, savedInstanceState);

    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        //getPreferenceManager().setSharedPreferencesName(getString(R.string.mainSharedPreferences));
        convertStringPrefToIntIfNeeded(getString(R.string.pref_key_num_recents), getContext());

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        setPreferencesFromResource(R.xml.preferences,rootKey);
        /*EditTextPreference editPref = findPreference(getString(R.string.pref_key_num_recents));
        editPref.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSelection(0,editText.getText().length());
        });
         */

        //ListPreference preference = findPreference(R.string.arrival_times)


    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        Log.d(TAG,"Preference key "+key+" changed");
        //sometimes this happens
        if(getContext()==null) return;
        /*
        THIS CODE STAYS COMMENTED FOR FUTURE REFERENCES
        if (key.equals(getString(R.string.pref_key_num_recents))){
            //check that is it an int

            String value = sharedPreferences.getString(key,"");
            boolean valid = value.length() != 0;
            try{
                Integer intValue = Integer.parseInt(value);
            } catch (NumberFormatException ex){
                valid = false;
            }
            if (!valid){
                Toast.makeText(getContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show();
                if(pref instanceof EditTextPreference){
                    EditTextPreference prefEdit = (EditTextPreference) pref;
                    //Intent intent = prefEdit.getIntent();
                    Log.d(TAG, "opening preference, dialog showing "+
                            (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG)!=null) );
                    //getPreferenceManager().showDialog(pref);
                    //onDisplayPreferenceDialog(prefEdit);
                    mHandler.postDelayed(new DelayedDisplay(prefEdit), 500);
                }

            }
        }
         */

        Log.d("BusTO Settings", "changed "+key+"\n "+sharedPreferences.getAll());

    }

    private void convertStringPrefToIntIfNeeded(String preferenceKey, Context con){
        if (con == null) return;
        SharedPreferences defaultSharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        try{

            Integer val = defaultSharedPref.getInt(preferenceKey, 0);
        } catch (NumberFormatException | ClassCastException ex){
            //convert the preference
            //final String preferenceNumRecents = getString(R.string.pref_key_num_recents);
            Log.d("Preference - BusTO", "Converting to integer the string preference "+preferenceKey);
            String currentValue = defaultSharedPref.getString(preferenceKey, "10");
            int newValue;
            try{
                newValue = Integer.parseInt(currentValue);
            } catch (NumberFormatException e){
                newValue = 10;
            }
            final SharedPreferences.Editor editor  = defaultSharedPref.edit();
            editor.remove(preferenceKey);
            editor.putInt(preferenceKey, newValue);
            editor.apply();
        }
    }

    class DelayedDisplay implements Runnable{
        private final WeakReference<DialogPreference> preferenceWeakReference;

        public DelayedDisplay(DialogPreference preference) {
            this.preferenceWeakReference = new WeakReference<>(preference);
        }

        @Override
        public void run() {
            if(preferenceWeakReference.get()==null)
                return;

            getPreferenceManager().showDialog(preferenceWeakReference.get());
        }
    }
}
