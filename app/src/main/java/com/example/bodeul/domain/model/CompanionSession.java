package com.example.bodeul.domain.model;

/**
 * 매니저가 실제로 수행 중인 동행 세션의 진행 상태를 담는다.
 */
public class CompanionSession {
    // 어떤 요청을 어떤 매니저가 수행 중인지 연결하는 식별 정보다.
    private final String id;
    private final String appointmentRequestId;
    private final String managerUserId;

    // 현장 진행 상황과 공유 메모는 동행 중 계속 갱신된다.
    private int currentStepOrder;
    private SessionStatus status;
    private String guardianUpdate;
    private String medicationNote;

    public CompanionSession(
            String id,
            String appointmentRequestId,
            String managerUserId,
            int currentStepOrder,
            SessionStatus status,
            String guardianUpdate,
            String medicationNote
    ) {
        this.id = id;
        this.appointmentRequestId = appointmentRequestId;
        this.managerUserId = managerUserId;
        this.currentStepOrder = currentStepOrder;
        this.status = status;
        this.guardianUpdate = guardianUpdate;
        this.medicationNote = medicationNote;
    }

    public String getId() {
        return id;
    }

    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    public int getCurrentStepOrder() {
        return currentStepOrder;
    }

    // 현재 진행 중인 가이드 단계를 갱신한다.
    public void setCurrentStepOrder(int currentStepOrder) {
        this.currentStepOrder = currentStepOrder;
    }

    public SessionStatus getStatus() {
        return status;
    }

    // 단계 전환에 맞춰 세션의 대표 상태를 갱신한다.
    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getGuardianUpdate() {
        return guardianUpdate;
    }

    // 보호자에게 공유할 현장 메시지를 저장한다.
    public void setGuardianUpdate(String guardianUpdate) {
        this.guardianUpdate = guardianUpdate;
    }

    public String getMedicationNote() {
        return medicationNote;
    }

    // 약 수령 및 복약 관련 메모를 저장한다.
    public void setMedicationNote(String medicationNote) {
        this.medicationNote = medicationNote;
    }
}
