package it.reyboz.bustorino.backend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import it.reyboz.bustorino.R;

public class Notifications {
    public static final String DEFAULT_CHANNEL_ID ="Default";
    public static final String DB_UPDATE_CHANNELS_ID ="Database Update";

    public static void createDefaultNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.default_notification_channel);
            String description = context.getString(R.string.default_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Register a notification channel on Android Oreo and above
     * @param con a Context
     * @param name channel name
     * @param description channel description
     * @param importance channel importance (from NotificationManager)
     * @param ID channel ID
     */
    public static void createNotificationChannel(Context con, String name, String description, int importance, String ID){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = con.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    public static Notification makeMatoDownloadNotification(Context context,String title){
        return new NotificationCompat.Builder(context, Notifications.DB_UPDATE_CHANNELS_ID)
                //.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), Constants.PENDING_INTENT_FLAG_IMMUTABLE))
                .setSmallIcon(R.drawable.bus)
                .setOngoing(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentTitle(context.getString(R.string.app_name))
                .setLocalOnly(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentText(title)
                .build();
    }
    public static Notification makeMatoDownloadNotification(Context context){
        return makeMatoDownloadNotification(context, context.getString(R.string.downloading_data_mato));
    }

    public static void createDBNotificationChannel(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                Notifications.DB_UPDATE_CHANNELS_ID,
                context.getString(R.string.database_notification_channel),
                NotificationManager.IMPORTANCE_MIN
            );
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
