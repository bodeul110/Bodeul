package com.example.bodeul.domain.model;

/**
 * 환자 또는 보호자가 생성한 병원 동행 요청 정보와 참가자 스냅샷을 함께 담는다.
 */
public class AppointmentRequest {
    private final String id;
    private final String patientUserId;
    private final String guardianUserId;
    private final String patientName;
    private final String patientPhone;
    private final String patientEmail;
    private final String guardianName;
    private final String guardianPhone;
    private final String guardianEmail;
    private final String hospitalName;
    private final String departmentName;
    private final String appointmentAt;
    private final String meetingPlace;
    private final String specialNotes;
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
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }

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
            String managerUserId,
            String patientName,
            String patientPhone,
            String patientEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail
    ) {
        this.id = id;
        this.patientUserId = patientUserId;
        this.guardianUserId = guardianUserId;
        this.patientName = patientName;
        this.patientPhone = patientPhone;
        this.patientEmail = patientEmail;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.guardianEmail = guardianEmail;
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

    public String getPatientName() {
        return patientName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public String getGuardianEmail() {
        return guardianEmail;
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

    // 요청 진행 상태를 화면과 저장소 양쪽에서 함께 갱신한다.
    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    // 매니저가 배정되면 요청 자체도 바로 매칭 완료 상태로 반영한다.
    public void assignManager(String managerUserId) {
        this.managerUserId = managerUserId;
        this.status = AppointmentStatus.MATCHED;
    }
}
