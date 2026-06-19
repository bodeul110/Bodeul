package com.example.bodeul.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.bodeul.R;
import com.example.bodeul.ui.support.ClientSupportActivity;

/**
 * 문의 답변 푸시를 시스템 알림으로 표시한다.
 */
public final class ClientSupportPushNotifier {
    private static final String CHANNEL_ID = "clientSupportUpdates";

    private ClientSupportPushNotifier() {}

    public static void showAnsweredNotification(
            @NonNull Context context,
            @NonNull ClientSupportPushPayload payload
    ) {
        createChannelIfNeeded(context);

        Intent destinationIntent = TextUtils.isEmpty(payload.getAppointmentRequestId())
                ? ClientSupportActivity.createIntent(context, null, payload.getSupportRequestId())
                : ClientSupportActivity.createIntent(
                        context,
                        payload.getAppointmentRequestId(),
                        payload.getSupportRequestId()
                );
        destinationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                payload.toNotificationId(),
                destinationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String body = resolveBody(context, payload);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(resolveTitle(context, payload))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(payload.toNotificationId(), builder.build());
    }

    @NonNull
    private static String resolveTitle(@NonNull Context context, @NonNull ClientSupportPushPayload payload) {
        if (!TextUtils.isEmpty(payload.getTitle())) {
            return payload.getTitle();
        }
        return context.getString(R.string.client_support_push_title);
    }

    @NonNull
    private static String resolveBody(@NonNull Context context, @NonNull ClientSupportPushPayload payload) {
        if (!TextUtils.isEmpty(payload.getBody())) {
            return payload.getBody();
        }
        return context.getString(R.string.client_support_push_body_fallback);
    }

    private static void createChannelIfNeeded(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.client_support_push_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.client_support_push_channel_description));
        notificationManager.createNotificationChannel(channel);
    }
}
