package com.example.bodeul.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * 알림 권한과 시스템 알림 허용 상태를 함께 확인해 안전하게 알림을 발송한다.
 */
public final class NotificationPermissionSupport {
    private static final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    private NotificationPermissionSupport() {
    }

    public static boolean isRuntimePermissionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean hasPermission(@NonNull Context context) {
        if (!isRuntimePermissionRequired()) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPendingPermissionRequest(@NonNull Context context) {
        return isRuntimePermissionRequired() && !hasPermission(context);
    }

    public static boolean canPostNotifications(@NonNull Context context) {
        return hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static boolean notifyIfPermitted(
            @NonNull Context context,
            int notificationId,
            @NonNull Notification notification
    ) {
        if (!canPostNotifications(context)) {
            return false;
        }
        try {
            notifyInternal(context, notificationId, notification);
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static void notifyInternal(
            @NonNull Context context,
            int notificationId,
            @NonNull Notification notification
    ) {
        NotificationManagerCompat.from(context).notify(notificationId, notification);
    }
}
