package com.example.bodeul.ui.manager;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/** 서버 저장 없이 화면 표시용 현재 위치를 한 번만 읽는다. */
final class ManagerCurrentLocationReader {
    private static final long TIMEOUT_MILLIS = 10_000L;
    private static final long MAX_FALLBACK_AGE_MILLIS = 5 * 60_000L;

    enum Failure {
        PERMISSION_REQUIRED,
        LOCATION_SERVICE_DISABLED,
        TIMED_OUT,
        UNAVAILABLE
    }

    interface Callback {
        void onSuccess(@NonNull Location location);

        void onFailure(@NonNull Failure failure);
    }

    private ManagerCurrentLocationReader() {
    }

    @android.annotation.SuppressLint("MissingPermission")
    static void read(@NonNull AppCompatActivity activity, @NonNull Callback callback) {
        if (!ManagerLocationSupport.hasFineLocationPermission(activity)) {
            callback.onFailure(Failure.PERMISSION_REQUIRED);
            return;
        }

        LocationManager locationManager = ContextCompat.getSystemService(activity, LocationManager.class);
        if (locationManager == null) {
            callback.onFailure(Failure.UNAVAILABLE);
            return;
        }

        String provider;
        try {
            if (!ManagerLocationSupport.hasAnyRealtimeProvider(locationManager)) {
                callback.onFailure(Failure.LOCATION_SERVICE_DISABLED);
                return;
            }
            provider = ManagerLocationSupport.resolveSingleShotProvider(locationManager);
        } catch (RuntimeException exception) {
            callback.onFailure(Failure.UNAVAILABLE);
            return;
        }
        if (ManagerLocationSupport.isEmptyProvider(provider)) {
            callback.onFailure(Failure.LOCATION_SERVICE_DISABLED);
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyCurrentLocation(activity, locationManager, provider, callback);
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        CancellationSignal cancellationSignal = new CancellationSignal();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                cancellationSignal.cancel();
                Location fallback = findFreshFallback(activity, locationManager);
                if (fallback != null) {
                    callback.onSuccess(fallback);
                } else {
                    callback.onFailure(Failure.TIMED_OUT);
                }
            }
        };
        mainHandler.postDelayed(timeout, TIMEOUT_MILLIS);

        try {
            locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(activity),
                    location -> {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        mainHandler.removeCallbacks(timeout);
                        if (location != null) {
                            callback.onSuccess(location);
                            return;
                        }
                        Location fallback = findFreshFallback(activity, locationManager);
                        if (fallback != null) {
                            callback.onSuccess(fallback);
                        } else {
                            callback.onFailure(Failure.UNAVAILABLE);
                        }
                    });
        } catch (RuntimeException exception) {
            mainHandler.removeCallbacks(timeout);
            cancellationSignal.cancel();
            if (completed.compareAndSet(false, true)) {
                callback.onFailure(Failure.UNAVAILABLE);
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void requestLegacyCurrentLocation(
            AppCompatActivity activity,
            LocationManager locationManager,
            String provider,
            Callback callback
    ) {
        AtomicBoolean completed = new AtomicBoolean(false);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Runnable[] timeoutHolder = new Runnable[1];
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                mainHandler.removeCallbacks(timeoutHolder[0]);
                locationManager.removeUpdates(this);
                callback.onSuccess(location);
            }

            @Override
            public void onProviderDisabled(@NonNull String disabledProvider) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                mainHandler.removeCallbacks(timeoutHolder[0]);
                locationManager.removeUpdates(this);
                callback.onFailure(Failure.LOCATION_SERVICE_DISABLED);
            }
        };
        timeoutHolder[0] = () -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            locationManager.removeUpdates(listener);
            Location fallback = findFreshFallback(activity, locationManager);
            if (fallback != null) {
                callback.onSuccess(fallback);
            } else {
                callback.onFailure(Failure.TIMED_OUT);
            }
        };
        mainHandler.postDelayed(timeoutHolder[0], TIMEOUT_MILLIS);

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (RuntimeException exception) {
            mainHandler.removeCallbacks(timeoutHolder[0]);
            if (completed.compareAndSet(false, true)) {
                callback.onFailure(Failure.UNAVAILABLE);
            }
        }
    }

    private static Location findFreshFallback(
            AppCompatActivity activity,
            LocationManager locationManager
    ) {
        try {
            Location fallback = ManagerLocationSupport.findBestLastKnownLocation(activity, locationManager);
            return fallback != null && isFreshFallback(fallback.getTime(), System.currentTimeMillis())
                    ? fallback
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static boolean isFreshFallback(long locationTimeMillis, long nowMillis) {
        if (locationTimeMillis <= 0L || nowMillis <= 0L) {
            return false;
        }
        return Math.abs(nowMillis - locationTimeMillis) <= MAX_FALLBACK_AGE_MILLIS;
    }
}
