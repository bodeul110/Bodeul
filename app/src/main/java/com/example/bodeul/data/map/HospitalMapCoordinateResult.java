package com.example.bodeul.data.map;

import androidx.annotation.Nullable;

/**
 * 병원과 약국의 좌표 검색 결과를 함께 전달한다.
 */
public final class HospitalMapCoordinateResult {
    @Nullable
    private final KakaoPlaceCoordinate hospitalCoordinate;
    @Nullable
    private final KakaoPlaceCoordinate pharmacyCoordinate;

    public HospitalMapCoordinateResult(
            @Nullable KakaoPlaceCoordinate hospitalCoordinate,
            @Nullable KakaoPlaceCoordinate pharmacyCoordinate
    ) {
        this.hospitalCoordinate = hospitalCoordinate;
        this.pharmacyCoordinate = pharmacyCoordinate;
    }

    @Nullable
    public KakaoPlaceCoordinate getHospitalCoordinate() {
        return hospitalCoordinate;
    }

    @Nullable
    public KakaoPlaceCoordinate getPharmacyCoordinate() {
        return pharmacyCoordinate;
    }

    public boolean hasAnyCoordinate() {
        return hospitalCoordinate != null || pharmacyCoordinate != null;
    }
}
