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
    private final double hospitalLatitude;
    private final double hospitalLongitude;
    private final String appointmentAt;
    private final String meetingPlace;
    private final String specialNotes;
    private final String patientConditionSummary;
    private final String medicationSummary;
    private final String mobilitySupportCode;
    private final String tripTypeCode;
    private final String managerGenderPreferenceCode;
    private final String paymentMethodCode;
    private final String couponCode;
    private final int basePrice;
    private final int optionSurchargePrice;
    private final int couponDiscountPrice;
    private final int finalPrice;
    private final String paymentStatusCode;
    private final String paymentApprovalCode;
    private final String paymentApprovedAt;
    private final String paymentProviderLabel;
    private AppointmentStatus status;
    private String managerUserId;
    private String managerName = "";
    private String managerPhone = "";
    private String managerEmail = "";

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
                0.0,
                0.0,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId
        );
    }

    public AppointmentRequest(
            String id,
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
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
                hospitalLatitude,
                hospitalLongitude,
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
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                0,
                0,
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
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                0.0,
                0.0,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail
        );
    }

    public AppointmentRequest(
            String id,
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
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
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                hospitalLatitude,
                hospitalLongitude,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                0,
                0,
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
            String guardianEmail,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice
    ) {
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                0.0,
                0.0,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail,
                patientConditionSummary,
                medicationSummary,
                mobilitySupportCode,
                tripTypeCode,
                managerGenderPreferenceCode,
                paymentMethodCode,
                couponCode,
                basePrice,
                optionSurchargePrice,
                couponDiscountPrice,
                finalPrice
        );
    }

    public AppointmentRequest(
            String id,
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
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
            String guardianEmail,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice
    ) {
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                hospitalLatitude,
                hospitalLongitude,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail,
                patientConditionSummary,
                medicationSummary,
                mobilitySupportCode,
                tripTypeCode,
                managerGenderPreferenceCode,
                paymentMethodCode,
                couponCode,
                basePrice,
                optionSurchargePrice,
                couponDiscountPrice,
                finalPrice,
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
            String guardianEmail,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice,
            String paymentStatusCode,
            String paymentApprovalCode,
            String paymentApprovedAt,
            String paymentProviderLabel
    ) {
        this(
                id,
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                0.0,
                0.0,
                appointmentAt,
                meetingPlace,
                specialNotes,
                status,
                managerUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail,
                patientConditionSummary,
                medicationSummary,
                mobilitySupportCode,
                tripTypeCode,
                managerGenderPreferenceCode,
                paymentMethodCode,
                couponCode,
                basePrice,
                optionSurchargePrice,
                couponDiscountPrice,
                finalPrice,
                paymentStatusCode,
                paymentApprovalCode,
                paymentApprovedAt,
                paymentProviderLabel
        );
    }

    public AppointmentRequest(
            String id,
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
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
            String guardianEmail,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice,
            String paymentStatusCode,
            String paymentApprovalCode,
            String paymentApprovedAt,
            String paymentProviderLabel
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
        this.hospitalLatitude = hospitalLatitude;
        this.hospitalLongitude = hospitalLongitude;
        this.appointmentAt = appointmentAt;
        this.meetingPlace = meetingPlace;
        this.specialNotes = specialNotes;
        this.patientConditionSummary = patientConditionSummary;
        this.medicationSummary = medicationSummary;
        this.mobilitySupportCode = mobilitySupportCode;
        this.tripTypeCode = tripTypeCode;
        this.managerGenderPreferenceCode = managerGenderPreferenceCode;
        this.paymentMethodCode = paymentMethodCode;
        this.couponCode = couponCode;
        this.basePrice = basePrice;
        this.optionSurchargePrice = optionSurchargePrice;
        this.couponDiscountPrice = couponDiscountPrice;
        this.finalPrice = finalPrice;
        this.paymentStatusCode = paymentStatusCode;
        this.paymentApprovalCode = paymentApprovalCode;
        this.paymentApprovedAt = paymentApprovedAt;
        this.paymentProviderLabel = paymentProviderLabel;
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

    public double getHospitalLatitude() {
        return hospitalLatitude;
    }

    public double getHospitalLongitude() {
        return hospitalLongitude;
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

    public String getPatientConditionSummary() {
        return patientConditionSummary;
    }

    public String getMedicationSummary() {
        return medicationSummary;
    }

    public String getMobilitySupportCode() {
        return mobilitySupportCode;
    }

    public String getTripTypeCode() {
        return tripTypeCode;
    }

    public String getManagerGenderPreferenceCode() {
        return managerGenderPreferenceCode;
    }

    public String getPaymentMethodCode() {
        return paymentMethodCode;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public int getOptionSurchargePrice() {
        return optionSurchargePrice;
    }

    public int getCouponDiscountPrice() {
        return couponDiscountPrice;
    }

    public int getFinalPrice() {
        return finalPrice;
    }

    public String getPaymentStatusCode() {
        return paymentStatusCode;
    }

    public String getPaymentApprovalCode() {
        return paymentApprovalCode;
    }

    public String getPaymentApprovedAt() {
        return paymentApprovedAt;
    }

    public String getPaymentProviderLabel() {
        return paymentProviderLabel;
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

    public String getManagerName() {
        return managerName;
    }

    public String getManagerPhone() {
        return managerPhone;
    }

    public String getManagerEmail() {
        return managerEmail;
    }

    public void setManagerProfile(String name, String phone, String email) {
        managerName = valueOrEmpty(name);
        managerPhone = valueOrEmpty(phone);
        managerEmail = valueOrEmpty(email);
    }

    // 매니저가 배정되면 요청 자체도 바로 매칭 완료 상태로 반영한다.
    public void assignManager(String managerUserId) {
        this.managerUserId = managerUserId;
        this.status = AppointmentStatus.MATCHED;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
