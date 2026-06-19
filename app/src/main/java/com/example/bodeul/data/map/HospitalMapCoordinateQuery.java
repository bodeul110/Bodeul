package com.example.bodeul.data.map;

import android.text.TextUtils;

/**
 * 병원/약국 좌표 검색에 필요한 기본 질의 묶음이다.
 */
public final class HospitalMapCoordinateQuery {
    private final String hospitalName;
    private final String departmentName;

    public HospitalMapCoordinateQuery(String hospitalName, String departmentName) {
        this.hospitalName = hospitalName == null ? "" : hospitalName.trim();
        this.departmentName = departmentName == null ? "" : departmentName.trim();
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(hospitalName);
    }

    public String buildPrimaryHospitalQuery() {
        if (TextUtils.isEmpty(departmentName)) {
            return hospitalName;
        }
        return hospitalName + " " + departmentName;
    }

    public String buildFallbackHospitalQuery() {
        return hospitalName;
    }

    public String buildPrimaryPharmacyQuery() {
        return hospitalName + " 약국";
    }

    public String buildFallbackPharmacyQuery() {
        return hospitalName + " 근처 약국";
    }
}
