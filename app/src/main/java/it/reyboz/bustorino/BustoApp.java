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

import androidx.multidex.MultiDexApplication;
import org.acra.ACRA;
import org.acra.BuildConfig;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import static org.acra.ReportField.*;


public class BustoApp extends MultiDexApplication {
    private static final ReportField[] REPORT_FIELDS = {REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME,
            PACKAGE_NAME, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, BUILD_CONFIG, CUSTOM_DATA,
            IS_SILENT, STACK_TRACE, INITIAL_CONFIGURATION, CRASH_CONFIGURATION, DISPLAY, USER_COMMENT,
            USER_APP_START_DATE, USER_CRASH_DATE, LOGCAT, SHARED_PREFERENCES};

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
        builder.setBuildConfigClass(BuildConfig.class).setReportFormat(StringFormat.JSON)
        .setDeleteUnapprovedReportsOnApplicationStart(true);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class).setMailTo("gtt@succhia.cz")
                .setReportFileName(it.reyboz.bustorino.BuildConfig.VERSION_NAME +"_report.json")
                .setResBody(R.string.acra_email_message)
                .setEnabled(true);
        builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class).setResText(R.string.message_crash)
                .setResTheme(R.style.AppTheme)
                .setEnabled(true);
        builder.setReportContent(REPORT_FIELDS);
        if (!it.reyboz.bustorino.BuildConfig.DEBUG)
            ACRA.init(this, builder);

    }

}
