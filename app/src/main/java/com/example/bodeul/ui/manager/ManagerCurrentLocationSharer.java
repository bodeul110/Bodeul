package com.example.bodeul.ui.manager;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 가이드 화면에서 현재 위치를 한 번 읽어 세션에 공유할 때 쓰는 보조 객체다.
 */
public final class ManagerCurrentLocationSharer {
    public interface Callback {
        void onSuccess(double latitude, double longitude, String summary);

        void onError(String message);
    }

    private ManagerCurrentLocationSharer() {
    }

    public static void share(@NonNull AppCompatActivity activity, @NonNull Callback callback) {
        LocationManager locationManager = ContextCompat.getSystemService(activity, LocationManager.class);
        if (locationManager == null) {
            callback.onError(activity.getString(R.string.guide_share_location_error));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String provider = ManagerLocationSupport.resolveSingleShotProvider(locationManager);
            if (ManagerLocationSupport.isEmptyProvider(provider)) {
                dispatchLastKnownLocation(activity, locationManager, callback);
                return;
            }
            locationManager.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(activity),
                    location -> {
                        if (location != null) {
                            dispatchSuccess(callback, location);
                            return;
                        }
                        dispatchLastKnownLocation(activity, locationManager, callback);
                    }
            );
            return;
        }

        dispatchLastKnownLocation(activity, locationManager, callback);
    }

    private static void dispatchLastKnownLocation(
            AppCompatActivity activity,
            LocationManager locationManager,
            Callback callback
    ) {
        Location fallbackLocation = ManagerLocationSupport.findBestLastKnownLocation(activity, locationManager);
        if (fallbackLocation == null) {
            callback.onError(activity.getString(R.string.guide_share_location_error));
            return;
        }
        dispatchSuccess(callback, fallbackLocation);
    }

    private static void dispatchSuccess(Callback callback, Location location) {
        callback.onSuccess(
                location.getLatitude(),
                location.getLongitude(),
                ManagerLocationSupport.buildLocationSummary(location)
        );
    }
}
