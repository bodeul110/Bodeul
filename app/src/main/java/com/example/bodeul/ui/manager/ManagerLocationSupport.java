package com.example.bodeul.ui.manager;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 가이드 화면 위치 공유에서 공통으로 쓰는 위치 해석 로직이다.
 */
public final class ManagerLocationSupport {
    private ManagerLocationSupport() {
    }

    public static boolean hasFineLocationPermission(@NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    @android.annotation.SuppressLint("MissingPermission")
    public static Location findBestLastKnownLocation(
            @NonNull Context context,
            @NonNull LocationManager locationManager
    ) {
        if (!hasFineLocationPermission(context)) {
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
    public static String resolveSingleShotProvider(@NonNull LocationManager locationManager) {
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

    public static List<String> resolveRealtimeProviders(@NonNull LocationManager locationManager) {
        List<String> providers = new ArrayList<>();
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        return providers;
    }

    public static String buildLocationSummary(@NonNull Location location) {
        return String.format(
                Locale.KOREA,
                "위도 %.5f, 경도 %.5f",
                location.getLatitude(),
                location.getLongitude()
        );
    }

    public static boolean hasAnyRealtimeProvider(@NonNull LocationManager locationManager) {
        return !resolveRealtimeProviders(locationManager).isEmpty();
    }

    public static boolean isEmptyProvider(@Nullable String provider) {
        return TextUtils.isEmpty(provider);
    }
}
