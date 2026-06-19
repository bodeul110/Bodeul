package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.map.HospitalMapCoordinateQuery;
import com.example.bodeul.data.map.HospitalMapCoordinateResult;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.ManagerDashboard;

/**
 * 연속 위치 공유 결과를 기준으로 자동 위치 알림 발송 대상을 판정하고 세션 상태를 저장한다.
 */
public final class CompanionLocationAutoAlertDispatcher {
    private final KakaoLocalPlaceSearchClient placeSearchClient;
    private final CompanionLocationAutoAlertEvaluator evaluator = new CompanionLocationAutoAlertEvaluator();

    private String currentQueryKey = "";
    @Nullable
    private HospitalMapCoordinateResult currentCoordinateResult;
    private boolean coordinateSearchInFlight;
    @Nullable
    private PendingDispatch pendingDispatch;

    public CompanionLocationAutoAlertDispatcher(@NonNull Context context) {
        this.placeSearchClient = new KakaoLocalPlaceSearchClient(context);
    }

    public void dispatchIfNeeded(
            @NonNull ManagerDashboard dashboard,
            double latitude,
            double longitude,
            @NonNull String managerUserId,
            @NonNull ManagerRepository managerRepository
    ) {
        if (!placeSearchClient.isConfigured()) {
            return;
        }
        if (dashboard.getSession().getLocationAlertStage() == CompanionLocationAlertStage.PHARMACY_NEAR) {
            return;
        }

        PendingDispatch dispatch = new PendingDispatch(dashboard, latitude, longitude, managerUserId, managerRepository);
        HospitalMapCoordinateQuery query = new HospitalMapCoordinateQuery(
                dashboard.getAppointmentRequest().getHospitalName(),
                dashboard.getAppointmentRequest().getDepartmentName()
        );
        if (query.isEmpty()) {
            return;
        }

        String queryKey = query.buildPrimaryHospitalQuery();
        if (TextUtils.equals(currentQueryKey, queryKey) && currentCoordinateResult != null) {
            dispatchIfStageResolved(dispatch, currentCoordinateResult);
            return;
        }

        pendingDispatch = dispatch;
        if (TextUtils.equals(currentQueryKey, queryKey) && coordinateSearchInFlight) {
            return;
        }

        currentQueryKey = queryKey;
        coordinateSearchInFlight = true;
        placeSearchClient.searchHospitalAndPharmacy(query, new KakaoLocalPlaceSearchClient.Callback() {
            @Override
            public void onSuccess(HospitalMapCoordinateResult result) {
                if (!TextUtils.equals(currentQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = result;
                if (pendingDispatch != null && TextUtils.equals(pendingDispatch.queryKey, queryKey)) {
                    dispatchIfStageResolved(pendingDispatch, result);
                    pendingDispatch = null;
                }
            }

            @Override
            public void onError(String message) {
                if (!TextUtils.equals(currentQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = null;
                pendingDispatch = null;
            }
        });
    }

    private void dispatchIfStageResolved(PendingDispatch dispatch, HospitalMapCoordinateResult coordinateResult) {
        CompanionLocationAlertStage nextStage = evaluator.evaluate(
                dispatch.dashboard,
                coordinateResult,
                dispatch.latitude,
                dispatch.longitude
        );
        if (nextStage == null) {
            return;
        }
        dispatch.managerRepository.saveCompanionLocationAlert(dispatch.managerUserId, nextStage);
    }

    private final class PendingDispatch {
        private final ManagerDashboard dashboard;
        private final double latitude;
        private final double longitude;
        private final String managerUserId;
        private final ManagerRepository managerRepository;
        private final String queryKey;

        private PendingDispatch(
                ManagerDashboard dashboard,
                double latitude,
                double longitude,
                String managerUserId,
                ManagerRepository managerRepository
        ) {
            this.dashboard = dashboard;
            this.latitude = latitude;
            this.longitude = longitude;
            this.managerUserId = managerUserId;
            this.managerRepository = managerRepository;
            this.queryKey = new HospitalMapCoordinateQuery(
                    dashboard.getAppointmentRequest().getHospitalName(),
                    dashboard.getAppointmentRequest().getDepartmentName()
            ).buildPrimaryHospitalQuery();
        }
    }
}
