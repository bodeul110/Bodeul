package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 예약 화면에서 병원과 진료과 선택에 사용하는 병원 후보 묶음이다.
 */
public final class BookingHospitalOption {
    private final String hospitalName;
    private final List<String> departmentNames;
    private final double latitude;
    private final double longitude;

    public BookingHospitalOption(String hospitalName, List<String> departmentNames) {
        this(hospitalName, departmentNames, 0.0, 0.0);
    }

    public BookingHospitalOption(String hospitalName, List<String> departmentNames, double latitude, double longitude) {
        this.hospitalName = normalize(hospitalName);
        this.departmentNames = Collections.unmodifiableList(new ArrayList<>(departmentNames));
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public List<String> getDepartmentNames() {
        return departmentNames;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getDepartmentCount() {
        return departmentNames.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
