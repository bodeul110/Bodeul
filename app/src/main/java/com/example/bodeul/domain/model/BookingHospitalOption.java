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

    public BookingHospitalOption(String hospitalName, List<String> departmentNames) {
        this.hospitalName = normalize(hospitalName);
        this.departmentNames = Collections.unmodifiableList(new ArrayList<>(departmentNames));
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public List<String> getDepartmentNames() {
        return departmentNames;
    }

    public int getDepartmentCount() {
        return departmentNames.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
