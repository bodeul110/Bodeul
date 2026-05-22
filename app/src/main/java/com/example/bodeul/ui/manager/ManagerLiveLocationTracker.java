package com.example.bodeul.ui.manager;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

import java.util.List;

/**
 * 가이드 화면이 보이는 동안 연속 위치 업데이트를 수집한다.
 */
public final class ManagerLiveLocationTracker {
    private static final long MIN_UPDATE_INTERVAL_MILLIS = 10_000L;
    private static final float MIN_UPDATE_DISTANCE_METERS = 15f;

    public interface Callback {
        void onLocationReceived(double latitude, double longitude, String summary);

        void onError(String message);
    }

    @Nullable
    private LocationManager locationManager;
    @Nullable
    private LocationListener locationListener;
    @Nullable
    private Location lastDispatchedLocation;
    private long lastDispatchedAtMillis;
    private boolean running;

    public boolean start(@NonNull AppCompatActivity activity, @NonNull Callback callback) {
        stop();
        if (!ManagerLocationSupport.hasFineLocationPermission(activity)) {
            callback.onError(activity.getString(R.string.guide_share_location_permission_denied));
            return false;
        }

        LocationManager resolvedLocationManager = ContextCompat.getSystemService(activity, LocationManager.class);
        if (resolvedLocationManager == null) {
            callback.onError(activity.getString(R.string.guide_share_location_error));
            return false;
        }

        List<String> providers = ManagerLocationSupport.resolveRealtimeProviders(resolvedLocationManager);
        if (providers.isEmpty()) {
            callback.onError(activity.getString(R.string.guide_live_location_provider_error));
            return false;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                dispatchIfNeeded(callback, location);
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                if (running && !ManagerLocationSupport.hasAnyRealtimeProvider(resolvedLocationManager)) {
                    callback.onError(activity.getString(R.string.guide_live_location_provider_error));
                }
            }
        };

        try {
            for (String provider : providers) {
                resolvedLocationManager.requestLocationUpdates(
                        provider,
                        MIN_UPDATE_INTERVAL_MILLIS,
                        MIN_UPDATE_DISTANCE_METERS,
                        listener,
                        Looper.getMainLooper()
                );
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            callback.onError(activity.getString(R.string.guide_live_location_provider_error));
            return false;
        }

        locationManager = resolvedLocationManager;
        locationListener = listener;
        running = true;

        Location lastKnownLocation = ManagerLocationSupport.findBestLastKnownLocation(activity, resolvedLocationManager);
        if (lastKnownLocation != null) {
            dispatchIfNeeded(callback, lastKnownLocation);
        }
        return true;
    }

    public void stop() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        locationManager = null;
        locationListener = null;
        lastDispatchedLocation = null;
        lastDispatchedAtMillis = 0L;
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private void dispatchIfNeeded(Callback callback, Location location) {
        if (!running) {
            return;
        }
        if (!shouldDispatch(location)) {
            return;
        }
        lastDispatchedLocation = new Location(location);
        lastDispatchedAtMillis = System.currentTimeMillis();
        callback.onLocationReceived(
                location.getLatitude(),
                location.getLongitude(),
                ManagerLocationSupport.buildLocationSummary(location)
        );
    }

    private boolean shouldDispatch(Location location) {
        if (lastDispatchedLocation == null) {
            return true;
        }
        if (location.distanceTo(lastDispatchedLocation) >= MIN_UPDATE_DISTANCE_METERS) {
            return true;
        }
        return System.currentTimeMillis() - lastDispatchedAtMillis >= MIN_UPDATE_INTERVAL_MILLIS;
    }
}
