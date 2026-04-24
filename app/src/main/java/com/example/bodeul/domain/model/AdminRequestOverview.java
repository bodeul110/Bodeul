package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 관리자 화면에서 요청 한 건을 운영 정보와 함께 보여주기 위한 요약 모델이다.
 */
public class AdminRequestOverview {
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
    private final boolean hasGuide;
    private final boolean hasLinkedParticipants;

    public AdminRequestOverview(
            AppointmentRequest appointmentRequest,
            @Nullable User patient,
            @Nullable User guardian,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport sessionReport,
            boolean hasGuide,
            boolean hasLinkedParticipants
    ) {
        this.appointmentRequest = appointmentRequest;
        this.patient = patient;
        this.guardian = guardian;
        this.manager = manager;
        this.session = session;
        this.sessionReport = sessionReport;
        this.hasGuide = hasGuide;
        this.hasLinkedParticipants = hasLinkedParticipants;
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

    public boolean hasGuide() {
        return hasGuide;
    }

    public boolean hasLinkedParticipants() {
        return hasLinkedParticipants;
    }
}
