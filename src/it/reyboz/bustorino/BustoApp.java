package it.reyboz.bustorino;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.BuildConfig;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;


public class BustoApp extends Application {

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
        if (!it.reyboz.bustorino.BuildConfig.DEBUG)
            ACRA.init(this, builder);
    }
}
