package com.example.bodeul.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.bodeul.R;
import com.example.bodeul.ui.booking.BookingLiveLocationActivity;
import com.example.bodeul.util.NotificationPermissionSupport;

public final class CompanionLocationAlertPushNotifier {
    private static final String CHANNEL_ID = "bodeul_companion_location_alert";

    private CompanionLocationAlertPushNotifier() {
    }

    public static void showNotification(Context context, CompanionLocationAlertPushPayload payload) {
        createChannelIfNeeded(context);
        Intent destinationIntent = BookingLiveLocationActivity.createIntent(
                context,
                payload.getAppointmentRequestId()
        );
        destinationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                payload.getAppointmentRequestId().hashCode(),
                destinationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(resolveTitle(context, payload))
                .setContentText(resolveBody(context, payload))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(resolveBody(context, payload)))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationPermissionSupport.notifyIfPermitted(
                context,
                ("location-" + payload.getAppointmentRequestId()).hashCode(),
                builder.build()
        );
    }

    private static String resolveTitle(Context context, CompanionLocationAlertPushPayload payload) {
        if (!payload.getTitle().isEmpty()) {
            return payload.getTitle();
        }
        return context.getString(R.string.companion_location_alert_push_title);
    }

    private static String resolveBody(Context context, CompanionLocationAlertPushPayload payload) {
        if (!payload.getBody().isEmpty()) {
            return payload.getBody();
        }
        return context.getString(R.string.companion_location_alert_push_body_fallback);
    }

    private static void createChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.companion_location_alert_push_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.companion_location_alert_push_channel_description));
        manager.createNotificationChannel(channel);
    }
}
