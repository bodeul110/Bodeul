package com.example.bodeul.ui.manager;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

import java.util.List;
import java.util.Locale;

/**
 * 가이드 화면에서 현재 위치를 한 번 읽어 세션에 공유할 때 사용하는 보조 객체다.
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
            String provider = resolveEnabledProvider(locationManager);
            if (TextUtils.isEmpty(provider)) {
                Location fallbackLocation = findBestLastKnownLocation(activity, locationManager);
                if (fallbackLocation == null) {
                    callback.onError(activity.getString(R.string.guide_share_location_error));
                    return;
                }
                dispatchSuccess(callback, fallbackLocation);
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
                        Location fallbackLocation = findBestLastKnownLocation(activity, locationManager);
                        if (fallbackLocation == null) {
                            callback.onError(activity.getString(R.string.guide_share_location_error));
                            return;
                        }
                        dispatchSuccess(callback, fallbackLocation);
                    }
            );
            return;
        }

        Location fallbackLocation = findBestLastKnownLocation(activity, locationManager);
        if (fallbackLocation == null) {
            callback.onError(activity.getString(R.string.guide_share_location_error));
            return;
        }
        dispatchSuccess(callback, fallbackLocation);
    }

    @Nullable
    private static Location findBestLastKnownLocation(Context context, LocationManager locationManager) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location == null) {
                continue;
            }
            if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = location;
            }
        }
        return bestLocation;
    }

    @Nullable
    private static String resolveEnabledProvider(LocationManager locationManager) {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return LocationManager.PASSIVE_PROVIDER;
        }
        return null;
    }

    private static void dispatchSuccess(Callback callback, Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        callback.onSuccess(
                latitude,
                longitude,
                String.format(Locale.KOREA, "위도 %.5f, 경도 %.5f", latitude, longitude)
        );
    }
}
