package com.example.bodeul.domain.model;

/**
 * 예약 폼에 반영할 병원과 진료과 선택 결과를 담는다.
 */
public final class BookingHospitalSelection {
    private final String hospitalName;
    private final String departmentName;

    public BookingHospitalSelection(String hospitalName, String departmentName) {
        this.hospitalName = normalize(hospitalName);
        this.departmentName = normalize(departmentName);
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public boolean isComplete() {
        return !hospitalName.isEmpty() && !departmentName.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
