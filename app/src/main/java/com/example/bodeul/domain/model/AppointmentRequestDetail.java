package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 예약 상세 화면에서 요청, 연결 참여자, 진행 세션, 리포트를 함께 다루기 위한 묶음 모델이다.
 */
public final class AppointmentRequestDetail {
    private final AppointmentRequest appointmentRequest;
    @Nullable
    private final User patient;
    @Nullable
    private final User guardian;
    @Nullable
    private final User manager;
    @Nullable
    private final CompanionSession session;
    @Nullable
    private final SessionReport sessionReport;
    @Nullable
    private final HospitalGuide hospitalGuide;
    @Nullable
    private final AppointmentFollowUpRecord followUpRecord;

    public AppointmentRequestDetail(
            AppointmentRequest appointmentRequest,
            @Nullable User patient,
            @Nullable User guardian,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport sessionReport,
            @Nullable HospitalGuide hospitalGuide
    ) {
        this(
                appointmentRequest,
                patient,
                guardian,
                manager,
                session,
                sessionReport,
                hospitalGuide,
                null
        );
    }

    public AppointmentRequestDetail(
            AppointmentRequest appointmentRequest,
            @Nullable User patient,
            @Nullable User guardian,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport sessionReport,
            @Nullable HospitalGuide hospitalGuide,
            @Nullable AppointmentFollowUpRecord followUpRecord
    ) {
        this.appointmentRequest = appointmentRequest;
        this.patient = patient;
        this.guardian = guardian;
        this.manager = manager;
        this.session = session;
        this.sessionReport = sessionReport;
        this.hospitalGuide = hospitalGuide;
        this.followUpRecord = followUpRecord;
    }

    public AppointmentRequest getAppointmentRequest() {
        return appointmentRequest;
    }

    @Nullable
    public User getPatient() {
        return patient;
    }

    @Nullable
    public User getGuardian() {
        return guardian;
    }

    @Nullable
    public User getManager() {
        return manager;
    }

    @Nullable
    public CompanionSession getSession() {
        return session;
    }

    @Nullable
    public SessionReport getSessionReport() {
        return sessionReport;
    }

    @Nullable
    public HospitalGuide getHospitalGuide() {
        return hospitalGuide;
    }

    @Nullable
    public AppointmentFollowUpRecord getFollowUpRecord() {
        return followUpRecord;
    }
}
