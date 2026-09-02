package com.example.bodeul.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.bodeul.R;

/** 기존 매니저 단말 위치 공유가 현재 빌드에서 허용되는지 단일 기준으로 판정한다. */
public final class LegacyManagerLocationSharingPolicy {
    private LegacyManagerLocationSharingPolicy() {
    }

    public static boolean isEnabled(@NonNull Context context) {
        return resolve(context.getResources().getBoolean(
                R.bool.bodeul_legacy_manager_location_enabled));
    }

    static boolean resolve(boolean configured) {
        return configured;
    }
}
