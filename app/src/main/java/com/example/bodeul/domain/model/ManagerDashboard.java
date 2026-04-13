package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 매니저 홈과 가이드 화면에 필요한 데이터를 한 번에 전달하는 묶음 모델이다.
 */
public class ManagerDashboard {
    // 화면 상단 카드와 안내 문구에 사용하는 참여자 정보다.
    private final User manager;
    private final User patient;
    private final User guardian;

    // 현재 배정 건의 요청, 세션, 가이드를 묶어 전달한다.
    private final AppointmentRequest appointmentRequest;
    private final CompanionSession session;
    private final HospitalGuide hospitalGuide;

    // 리포트는 아직 작성되지 않았을 수 있어 nullable로 둔다.
    @Nullable
    private final SessionReport sessionReport;

    public ManagerDashboard(
            User manager,
            User patient,
            User guardian,
            AppointmentRequest appointmentRequest,
            CompanionSession session,
            HospitalGuide hospitalGuide,
            @Nullable SessionReport sessionReport
    ) {
        this.manager = manager;
        this.patient = patient;
        this.guardian = guardian;
        this.appointmentRequest = appointmentRequest;
        this.session = session;
        this.hospitalGuide = hospitalGuide;
        this.sessionReport = sessionReport;
    }

    public User getManager() {
        return manager;
    }

    public User getPatient() {
        return patient;
    }

    public User getGuardian() {
        return guardian;
    }

    public AppointmentRequest getAppointmentRequest() {
        return appointmentRequest;
    }

    public CompanionSession getSession() {
        return session;
    }

    public HospitalGuide getHospitalGuide() {
        return hospitalGuide;
    }

    @Nullable
    public SessionReport getSessionReport() {
        return sessionReport;
    }
}
