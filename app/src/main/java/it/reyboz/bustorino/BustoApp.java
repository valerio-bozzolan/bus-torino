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
package it.reyboz.bustorino;

import android.content.Context;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.PreferencesHolder;
import it.reyboz.bustorino.fragments.SettingsFragment;
import org.acra.ACRA;
import org.acra.BuildConfig;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import java.lang.reflect.Array;
import java.util.*;

import static org.acra.ReportField.*;


public class BustoApp extends MultiDexApplication {
    private static final List<ReportField> REPORT_FIELDS = List.of(REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME,
            PACKAGE_NAME, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, BUILD_CONFIG, CUSTOM_DATA,
            IS_SILENT, STACK_TRACE, INITIAL_CONFIGURATION, CRASH_CONFIGURATION, DISPLAY, USER_COMMENT,
            USER_APP_START_DATE, USER_CRASH_DATE, LOGCAT, SHARED_PREFERENCES);


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder();
        // mail stuff
        MailSenderConfigurationBuilder mailConfig = new MailSenderConfigurationBuilder();
        mailConfig.withMailTo("gtt@succhia.cz")
                .withReportFileName(it.reyboz.bustorino.BuildConfig.VERSION_NAME +"_report.json")
                .withBody(getString(R.string.acra_email_message))
                .setEnabled(true);
        //dialog stuff
        DialogConfigurationBuilder dialogBuild = new DialogConfigurationBuilder();
        dialogBuild.withText(getString(R.string.message_crash))
                .withResTheme(R.style.AppTheme).setEnabled(true);
        //Set options
        builder.withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withDeleteUnapprovedReportsOnApplicationStart(true);
        //Add plugins
        builder.withPluginConfigurations(
            mailConfig.build(), dialogBuild.build()
        );


        builder.setReportContent(REPORT_FIELDS);
        if (!it.reyboz.bustorino.BuildConfig.DEBUG)
            ACRA.init(this, builder);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        checkApplyDefaultSettingsValues();

        var dayorNight = PreferencesHolder.getAppThemeDayNight(this);
        AppCompatDelegate.setDefaultNightMode(dayorNight);
        
    }

    /**
     * Adjust setting to match the default ones
     */
    protected void checkApplyDefaultSettingsValues(){
        SharedPreferences mainSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = mainSharedPref.edit();
        boolean edit = false;

        //Day or night theme
        var str = mainSharedPref.getString(PreferencesHolder.PREF_THEME_DAY_NIGHT, "");
        if(str.isEmpty()){
            editor.putString(PreferencesHolder.PREF_THEME_DAY_NIGHT, "system");
            edit = true;
        }

        //Main fragment to show
        String screen = mainSharedPref.getString(SettingsFragment.PREF_KEY_STARTUP_SCREEN, "");
        if (screen.isEmpty()){
            editor.putString(SettingsFragment.PREF_KEY_STARTUP_SCREEN, "arrivals");
            edit=true;
        }
        //Fetchers
        final Set<String> setSelected = mainSharedPref.getStringSet(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE, new HashSet<>());
        if (setSelected.isEmpty()){
            String[] defaultVals = getResources().getStringArray(R.array.arrivals_sources_values_default);
            editor.putStringSet(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE, utils.convertArrayToSet(defaultVals));
            edit=true;
        }
        //Live bus positions
        final String keySourcePositions=getString(R.string.pref_positions_source);
        final String positionsSource = mainSharedPref.getString(keySourcePositions, "");
        if(positionsSource.isEmpty()){
            String[] defaultVals = getResources().getStringArray(R.array.positions_source_values);
            editor.putString(keySourcePositions, defaultVals[0]);
            edit=true;
        }
        //Map style
        final String mapStylePref = mainSharedPref.getString(SettingsFragment.LIBREMAP_STYLE_PREF_KEY, "");
        if(mapStylePref.isEmpty()){
            final String[] defaultVals = getResources().getStringArray(R.array.map_style_pref_values);
            editor.putString(SettingsFragment.LIBREMAP_STYLE_PREF_KEY, defaultVals[0]);
            edit=true;
        }
        if (edit){
            editor.apply();
        }

    }
}
