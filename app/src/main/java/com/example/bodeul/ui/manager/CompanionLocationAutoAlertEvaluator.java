package com.example.bodeul.ui.manager;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.data.map.HospitalMapCoordinateResult;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;

/**
 * 매니저 실시간 위치와 병원/약국 좌표를 비교해서 자동 알림 단계를 판정한다.
 */
public final class CompanionLocationAutoAlertEvaluator {
    private static final float HOSPITAL_NEAR_THRESHOLD_METERS = 150f;
    private static final float PHARMACY_NEAR_THRESHOLD_METERS = 180f;

    @Nullable
    public CompanionLocationAlertStage evaluate(
            @NonNull ManagerDashboard dashboard,
            @Nullable HospitalMapCoordinateResult coordinateResult,
            double latitude,
            double longitude
    ) {
        CompanionSession session = dashboard.getSession();
        if (!isSupportedStatus(session.getStatus())) {
            return null;
        }
        CompanionLocationAlertStage currentStage = session.getLocationAlertStage();
        if (currentStage == CompanionLocationAlertStage.PHARMACY_NEAR || coordinateResult == null) {
            return null;
        }

        if (currentStage.canAdvanceTo(CompanionLocationAlertStage.PHARMACY_NEAR)
                && isNear(latitude, longitude, coordinateResult.getPharmacyCoordinate(), PHARMACY_NEAR_THRESHOLD_METERS)) {
            return CompanionLocationAlertStage.PHARMACY_NEAR;
        }

        if (currentStage.canAdvanceTo(CompanionLocationAlertStage.HOSPITAL_NEAR)
                && isNear(latitude, longitude, coordinateResult.getHospitalCoordinate(), HOSPITAL_NEAR_THRESHOLD_METERS)) {
            return CompanionLocationAlertStage.HOSPITAL_NEAR;
        }

        return null;
    }

    private boolean isNear(
            double latitude,
            double longitude,
            @Nullable KakaoPlaceCoordinate coordinate,
            float thresholdMeters
    ) {
        if (coordinate == null) {
            return false;
        }
        float[] distanceResult = new float[1];
        Location.distanceBetween(
                latitude,
                longitude,
                coordinate.getLatitude(),
                coordinate.getLongitude(),
                distanceResult
        );
        return distanceResult[0] <= thresholdMeters;
    }

    // 자동 위치 알림은 실제 동행이 진행 중인 상태에서만 보낸다.
    private boolean isSupportedStatus(@Nullable SessionStatus status) {
        return status == SessionStatus.MEETING
                || status == SessionStatus.WAITING
                || status == SessionStatus.IN_TREATMENT
                || status == SessionStatus.PAYMENT;
    }
}
