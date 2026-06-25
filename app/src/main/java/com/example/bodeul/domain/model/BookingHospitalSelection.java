package com.example.bodeul.domain.model;

/**
 * 예약 폼에 반영할 병원과 진료과 선택 결과를 담는다.
 */
public final class BookingHospitalSelection {
    private final String hospitalName;
    private final String departmentName;
    private final double hospitalLatitude;
    private final double hospitalLongitude;

    public BookingHospitalSelection(String hospitalName, String departmentName) {
        this(hospitalName, departmentName, 0.0, 0.0);
    }

    public BookingHospitalSelection(String hospitalName, String departmentName, double hospitalLatitude, double hospitalLongitude) {
        this.hospitalName = normalize(hospitalName);
        this.departmentName = normalize(departmentName);
        this.hospitalLatitude = hospitalLatitude;
        this.hospitalLongitude = hospitalLongitude;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public double getHospitalLatitude() {
        return hospitalLatitude;
    }

    public double getHospitalLongitude() {
        return hospitalLongitude;
    }

    public boolean isComplete() {
        return !hospitalName.isEmpty() && !departmentName.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
