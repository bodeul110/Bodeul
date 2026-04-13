package com.example.bodeul.domain.model;

/**
 * 환자 또는 보호자가 생성한 병원 동행 요청 정보를 담는다.
 */
public class AppointmentRequest {
    // 요청 자체와 요청 당사자를 식별하기 위한 기본 정보다.
    private final String id;
    private final String patientUserId;
    private final String guardianUserId;

    // 병원 방문에 필요한 일정 및 장소 정보다.
    private final String hospitalName;
    private final String departmentName;
    private final String appointmentAt;
    private final String meetingPlace;
    private final String specialNotes;

    // 매칭 이후에는 상태와 담당 매니저가 바뀔 수 있다.
    private AppointmentStatus status;
    private String managerUserId;

    public AppointmentRequest(
            String id,
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            AppointmentStatus status,
            String managerUserId
    ) {
        this.id = id;
        this.patientUserId = patientUserId;
        this.guardianUserId = guardianUserId;
        this.hospitalName = hospitalName;
        this.departmentName = departmentName;
        this.appointmentAt = appointmentAt;
        this.meetingPlace = meetingPlace;
        this.specialNotes = specialNotes;
        this.status = status;
        this.managerUserId = managerUserId;
    }

    public String getId() {
        return id;
    }

    public String getPatientUserId() {
        return patientUserId;
    }

    public String getGuardianUserId() {
        return guardianUserId;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getAppointmentAt() {
        return appointmentAt;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    // 요청 진행 상태를 갱신한다.
    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    // 매니저 배정이 완료되면 담당자와 상태를 함께 반영한다.
    public void assignManager(String managerUserId) {
        this.managerUserId = managerUserId;
        this.status = AppointmentStatus.MATCHED;
    }
}
