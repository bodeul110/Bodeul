package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 보호자 화면에서 요청, 진행 상태, 최종 리포트를 한 카드로 묶어 전달한다.
 */
public class GuardianReportEntry {
    private final AppointmentRequest appointmentRequest;
    @Nullable
    private final User patient;
    @Nullable
    private final User manager;
    @Nullable
    private final CompanionSession session;
    @Nullable
    private final SessionReport sessionReport;
    @Nullable
    private final HospitalGuide hospitalGuide;

    public GuardianReportEntry(
            AppointmentRequest appointmentRequest,
            @Nullable User patient,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport sessionReport,
            @Nullable HospitalGuide hospitalGuide
    ) {
        this.appointmentRequest = appointmentRequest;
        this.patient = patient;
        this.manager = manager;
        this.session = session;
        this.sessionReport = sessionReport;
        this.hospitalGuide = hospitalGuide;
    }

    public AppointmentRequest getAppointmentRequest() {
        return appointmentRequest;
    }

    @Nullable
    public User getPatient() {
        return patient;
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
}
