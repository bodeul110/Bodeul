package com.example.bodeul.ui.manager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;

public class ManagerLocationService extends Service {
    private static final String ACTION_START = "ACTION_START";
    private static final String ACTION_STOP = "ACTION_STOP";
    private static final String CHANNEL_ID = "ManagerLocationChannel";
    private static final int NOTIFICATION_ID = 101;

    private ManagerLiveLocationTracker tracker;
    private ManagerRepository managerRepository;
    private AuthRepository authRepository;
    private String currentManagerUserId;

    public static void start(Context context) {
        Intent intent = new Intent(context, ManagerLocationService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, ManagerLocationService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        managerRepository = ServiceLocator.provideManagerRepository(this);
        authRepository = ServiceLocator.provideAuthRepository(this);
        tracker = new ManagerLiveLocationTracker();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startForeground(NOTIFICATION_ID, buildNotification());
                authRepository.getCurrentUser(new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        currentManagerUserId = result.getId();
                        startTracking();
                    }

                    @Override
                    public void onError(String message) {
                        // Fail silently in background
                    }
                });
            } else if (ACTION_STOP.equals(action)) {
                stopTracking();
                stopForegroundCompat();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startTracking() {
        if (!tracker.isRunning() && currentManagerUserId != null) {
            tracker.start(this, new ManagerLiveLocationTracker.Callback() {
                @Override
                public void onLocationReceived(double latitude, double longitude, String summary) {
                    managerRepository.shareCurrentLocation(currentManagerUserId, latitude, longitude, summary, new RepositoryCallback<ManagerDashboard>() {
                        @Override
                        public void onSuccess(ManagerDashboard result) {}
                        
                        @Override
                        public void onError(String message) {}
                    });
                }

                @Override
                public void onError(String message) {
                    // Fail silently in background
                }
            });
        }
    }

    private void stopTracking() {
        tracker.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Intent pendingIntentActivity = new Intent(this, ManagerGuideActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, pendingIntentActivity, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.manager_location_notification_body))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.manager_location_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.manager_location_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }
}
