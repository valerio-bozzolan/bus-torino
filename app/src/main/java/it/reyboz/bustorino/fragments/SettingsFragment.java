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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.preference.*;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.data.GtfsMaintenanceWorker;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getName();

    private static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";
    //private static final
    Handler mHandler;
    public final static String PREF_KEY_STARTUP_SCREEN="startup_screen_to_show";
    public final static String KEY_ARRIVALS_FETCHERS_USE = "arrivals_fetchers_use_setting";
    public final static String LIVE_POSITIONS_PREF_MQTT_VALUE="mqtt";

    private boolean setSummaryStartupPref = false;

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

        ListPreference startupScreenPref = findPreference(PREF_KEY_STARTUP_SCREEN);
        if(startupScreenPref !=null){
            if (startupScreenPref.getValue()==null){
                startupScreenPref.setSummary(getString(R.string.nav_arrivals_text));
                setSummaryStartupPref = true;
            }
        }

        //Log.d("BusTO-PrefFrag","startup screen pref is "+startupScreenPref.getValue());

        Preference dbUpdateNow = findPreference("pref_db_update_now");
        if (dbUpdateNow!=null)
        dbUpdateNow.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        //trigger update
                        if(getContext()!=null) {
                            DatabaseUpdate.requestDBUpdateWithWork(getContext().getApplicationContext(), true, true);
                            Toast.makeText(getContext(),R.string.requesting_db_update,Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        return false;
                    }
                }
        );

        else {
            Log.e("BusTO-Preferences", "Cannot find db update preference");
        }
        Preference clearGtfsTrips = findPreference("pref_clear_gtfs_trips");
        if (clearGtfsTrips != null) {
            clearGtfsTrips.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull @NotNull Preference preference) {
                    if (getContext() != null) {
                        OneTimeWorkRequest requ = GtfsMaintenanceWorker.Companion.makeOneTimeRequest(GtfsMaintenanceWorker.CLEAR_GTFS_TRIPS);
                        WorkManager.getInstance(getContext()).enqueue(requ);
                        WorkManager.getInstance(getContext()).getWorkInfosByTagLiveData(GtfsMaintenanceWorker.CLEAR_GTFS_TRIPS).observe(getViewLifecycleOwner(),
                                (Observer<List<WorkInfo>>) workInfos -> {
                                    if(workInfos.isEmpty())
                                        return;
                                    if(workInfos.get(0).getState()==(WorkInfo.State.SUCCEEDED)){
                                        Toast.makeText(
                                                getContext(), R.string.all_trips_removed, Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                });
                        return true;
                    }
                    return false;
                }
            });
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        Log.d(TAG,"Preference key "+key+" changed");
        if (key.equals(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE)){
            Log.d(TAG, "New value is: "+sharedPreferences.getStringSet(key, new HashSet<>()));
        }

        //sometimes this happens
        if(getContext()==null) return;
        if(key.equals(PREF_KEY_STARTUP_SCREEN) && setSummaryStartupPref && pref !=null){
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
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
        SharedPreferences defaultSharedPref = PreferenceManager.getDefaultSharedPreferences(con);
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
