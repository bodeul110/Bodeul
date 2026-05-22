package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 매니저가 실제로 수행 중인 동행 세션의 진행 상태와 공유 메모를 담는다.
 */
public class CompanionSession {
    // 어떤 요청에 어떤 매니저가 수행 중인지 연결하는 핵심 정보다.
    private final String id;
    private final String appointmentRequestId;
    private final String managerUserId;

    // 현장 진행 상황과 공유 메모는 동행 중 계속 갱신된다.
    private int currentStepOrder;
    private SessionStatus status;
    private String guardianUpdate;
    private String locationSummary;
    private String fieldPhotoNote;
    private String medicationNote;
    private String pharmacySummary;
    private boolean pharmacyCompleted;
    @Nullable
    private Double sharedLatitude;
    @Nullable
    private Double sharedLongitude;
    private long sharedLocationUpdatedAtMillis;
    private final List<CompanionChatMessage> chatMessages;

    public CompanionSession(
            String id,
            String appointmentRequestId,
            String managerUserId,
            int currentStepOrder,
            SessionStatus status,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            boolean pharmacyCompleted
    ) {
        this(
                id,
                appointmentRequestId,
                managerUserId,
                currentStepOrder,
                status,
                guardianUpdate,
                locationSummary,
                fieldPhotoNote,
                medicationNote,
                pharmacySummary,
                pharmacyCompleted,
                null,
                null,
                0L,
                Collections.emptyList()
        );
    }

    public CompanionSession(
            String id,
            String appointmentRequestId,
            String managerUserId,
            int currentStepOrder,
            SessionStatus status,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            boolean pharmacyCompleted,
            List<CompanionChatMessage> chatMessages
    ) {
        this(
                id,
                appointmentRequestId,
                managerUserId,
                currentStepOrder,
                status,
                guardianUpdate,
                locationSummary,
                fieldPhotoNote,
                medicationNote,
                pharmacySummary,
                pharmacyCompleted,
                null,
                null,
                0L,
                chatMessages
        );
    }

    public CompanionSession(
            String id,
            String appointmentRequestId,
            String managerUserId,
            int currentStepOrder,
            SessionStatus status,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            boolean pharmacyCompleted,
            @Nullable Double sharedLatitude,
            @Nullable Double sharedLongitude,
            long sharedLocationUpdatedAtMillis
    ) {
        this(
                id,
                appointmentRequestId,
                managerUserId,
                currentStepOrder,
                status,
                guardianUpdate,
                locationSummary,
                fieldPhotoNote,
                medicationNote,
                pharmacySummary,
                pharmacyCompleted,
                sharedLatitude,
                sharedLongitude,
                sharedLocationUpdatedAtMillis,
                Collections.emptyList()
        );
    }

    public CompanionSession(
            String id,
            String appointmentRequestId,
            String managerUserId,
            int currentStepOrder,
            SessionStatus status,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            boolean pharmacyCompleted,
            @Nullable Double sharedLatitude,
            @Nullable Double sharedLongitude,
            long sharedLocationUpdatedAtMillis,
            List<CompanionChatMessage> chatMessages
    ) {
        this.id = id;
        this.appointmentRequestId = appointmentRequestId;
        this.managerUserId = managerUserId;
        this.currentStepOrder = currentStepOrder;
        this.status = status;
        this.guardianUpdate = guardianUpdate;
        this.locationSummary = locationSummary;
        this.fieldPhotoNote = fieldPhotoNote;
        this.medicationNote = medicationNote;
        this.pharmacySummary = pharmacySummary;
        this.pharmacyCompleted = pharmacyCompleted;
        this.sharedLatitude = sharedLatitude;
        this.sharedLongitude = sharedLongitude;
        this.sharedLocationUpdatedAtMillis = sharedLocationUpdatedAtMillis;
        this.chatMessages = new ArrayList<>(chatMessages);
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

    // 단계 전환에 맞춰 세션 전체의 상태를 갱신한다.
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

    public String getLocationSummary() {
        return locationSummary;
    }

    // 위치 공유나 이동 상황을 짧게 적어 다음 화면에서도 재사용한다.
    public void setLocationSummary(String locationSummary) {
        this.locationSummary = locationSummary;
    }

    @Nullable
    public Double getSharedLatitude() {
        return sharedLatitude;
    }

    @Nullable
    public Double getSharedLongitude() {
        return sharedLongitude;
    }

    public long getSharedLocationUpdatedAtMillis() {
        return sharedLocationUpdatedAtMillis;
    }

    public boolean hasSharedLocationCoordinates() {
        return sharedLatitude != null && sharedLongitude != null;
    }

    // 실제 좌표 공유 시각을 같이 남겨 보호자 화면과 지도 이동에 재사용한다.
    public void updateSharedLocation(@Nullable Double sharedLatitude, @Nullable Double sharedLongitude, long sharedLocationUpdatedAtMillis) {
        this.sharedLatitude = sharedLatitude;
        this.sharedLongitude = sharedLongitude;
        this.sharedLocationUpdatedAtMillis = sharedLocationUpdatedAtMillis;
    }

    public String getFieldPhotoNote() {
        return fieldPhotoNote;
    }

    // 현장 사진이나 접수 서류 확인 메모를 별도 필드로 관리한다.
    public void setFieldPhotoNote(String fieldPhotoNote) {
        this.fieldPhotoNote = fieldPhotoNote;
    }

    public String getMedicationNote() {
        return medicationNote;
    }

    // 수령 약과 복약 관련 메모를 저장한다.
    public void setMedicationNote(String medicationNote) {
        this.medicationNote = medicationNote;
    }

    public String getPharmacySummary() {
        return pharmacySummary;
    }

    public void setPharmacySummary(String pharmacySummary) {
        this.pharmacySummary = pharmacySummary;
    }

    public boolean isPharmacyCompleted() {
        return pharmacyCompleted;
    }

    public void setPharmacyCompleted(boolean pharmacyCompleted) {
        this.pharmacyCompleted = pharmacyCompleted;
    }

    public List<CompanionChatMessage> getChatMessages() {
        return Collections.unmodifiableList(chatMessages);
    }

    public void addChatMessage(CompanionChatMessage chatMessage) {
        this.chatMessages.add(chatMessage);
    }
}
