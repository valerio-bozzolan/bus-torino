package it.reyboz.bustorino;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.BuildConfig;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import static org.acra.ReportField.*;


public class BustoApp extends Application {
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
