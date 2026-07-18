package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 매니저가 실제로 수행 중인 동행 세션의 진행 상태와 공유 메모를 담는다.
 */
public class CompanionSession {
    private static final int MAX_SHARED_LOCATION_HISTORY = 10;

    // 어떤 요청에 어떤 매니저가 수행 중인지 연결하는 핵심 정보다.
    private final String id;
    private final String appointmentRequestId;
    private final String managerUserId;
    private String realtimeSessionId;

    // 현장 진행 상황과 공유 메모는 동행 중 계속 갱신된다.
    private int currentStepOrder;
    private SessionStatus status;
    private String guardianUpdate;
    private String locationSummary;
    private String fieldPhotoNote;
    private String medicationNote;
    private String pharmacySummary;
    private boolean prescriptionCollected;
    private boolean pharmacyCompleted;
    private boolean medicationGuidanceCompleted;
    @Nullable
    private Double sharedLatitude;
    @Nullable
    private Double sharedLongitude;
    private long sharedLocationUpdatedAtMillis;
    private boolean liveLocationSharingActive;
    private long liveLocationSharingStartedAtMillis;
    private final List<CompanionLocationHistoryEntry> sharedLocationHistory;
    private final List<CompanionChatMessage> chatMessages;
    private CompanionLocationAlertStage locationAlertStage = CompanionLocationAlertStage.NONE;
    private long locationAlertSentAtMillis;
    private long patientChatReadAtMillis;
    private long guardianChatReadAtMillis;
    private long managerChatReadAtMillis;

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
                false,
                0L,
                Collections.emptyList(),
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
                false,
                0L,
                Collections.emptyList(),
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
                false,
                0L,
                Collections.emptyList(),
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
                false,
                0L,
                Collections.emptyList(),
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
            long sharedLocationUpdatedAtMillis,
            boolean liveLocationSharingActive,
            long liveLocationSharingStartedAtMillis,
            List<CompanionLocationHistoryEntry> sharedLocationHistory,
            List<CompanionChatMessage> chatMessages
    ) {
        this.id = id;
        this.appointmentRequestId = appointmentRequestId;
        this.managerUserId = managerUserId;
        this.realtimeSessionId = id;
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
        this.liveLocationSharingActive = liveLocationSharingActive;
        this.liveLocationSharingStartedAtMillis = liveLocationSharingStartedAtMillis;
        this.sharedLocationHistory = new ArrayList<>(sharedLocationHistory);
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

    public String getRealtimeSessionId() {
        return realtimeSessionId;
    }

    public void setRealtimeSessionId(String realtimeSessionId) {
        this.realtimeSessionId = realtimeSessionId == null || realtimeSessionId.trim().isEmpty()
                ? id
                : realtimeSessionId.trim();
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

    // 단계 전환에 맞춰 세션 전체 상태를 갱신한다.
    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getGuardianUpdate() {
        return guardianUpdate;
    }

    // 보호자에게 공유할 현장 메시지를 덮어쓴다.
    public void setGuardianUpdate(String guardianUpdate) {
        this.guardianUpdate = guardianUpdate;
    }

    public String getLocationSummary() {
        return locationSummary;
    }

    // 위치 공유와 이동 상황을 짧게 적어 다음 화면에서도 재사용한다.
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

    public boolean isLiveLocationSharingActive() {
        return liveLocationSharingActive;
    }

    public long getLiveLocationSharingStartedAtMillis() {
        return liveLocationSharingStartedAtMillis;
    }

    public boolean hasSharedLocationCoordinates() {
        return sharedLatitude != null && sharedLongitude != null;
    }

    // 실제 좌표와 공유 시각을 함께 담아 보호자와 관리자 화면에서 재사용한다.
    public void updateSharedLocation(
            @Nullable Double sharedLatitude,
            @Nullable Double sharedLongitude,
            long sharedLocationUpdatedAtMillis
    ) {
        this.sharedLatitude = sharedLatitude;
        this.sharedLongitude = sharedLongitude;
        this.sharedLocationUpdatedAtMillis = sharedLocationUpdatedAtMillis;
    }

    public void updateLiveLocationSharing(boolean liveLocationSharingActive, long startedAtMillis) {
        this.liveLocationSharingActive = liveLocationSharingActive;
        this.liveLocationSharingStartedAtMillis = liveLocationSharingActive ? startedAtMillis : 0L;
    }

    public List<CompanionLocationHistoryEntry> getSharedLocationHistory() {
        return Collections.unmodifiableList(sharedLocationHistory);
    }

    public void recordSharedLocation(
            double latitude,
            double longitude,
            String summary,
            long capturedAtMillis
    ) {
        updateSharedLocation(latitude, longitude, capturedAtMillis);
        sharedLocationHistory.add(0, new CompanionLocationHistoryEntry(
                latitude,
                longitude,
                summary == null ? "" : summary,
                capturedAtMillis
        ));
        while (sharedLocationHistory.size() > MAX_SHARED_LOCATION_HISTORY) {
            sharedLocationHistory.remove(sharedLocationHistory.size() - 1);
        }
    }

    public void replaceSharedLocationHistory(List<CompanionLocationHistoryEntry> locations) {
        sharedLocationHistory.clear();
        if (locations != null) {
            sharedLocationHistory.addAll(locations);
        }
        while (sharedLocationHistory.size() > MAX_SHARED_LOCATION_HISTORY) {
            sharedLocationHistory.remove(sharedLocationHistory.size() - 1);
        }
        if (sharedLocationHistory.isEmpty()) {
            updateSharedLocation(null, null, 0L);
            return;
        }
        CompanionLocationHistoryEntry latest = sharedLocationHistory.get(0);
        updateSharedLocation(
                latest.getLatitude(),
                latest.getLongitude(),
                latest.getCapturedAtMillis());
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

    // 수령 결과와 복약 관련 메모를 덮어쓴다.
    public void setMedicationNote(String medicationNote) {
        this.medicationNote = medicationNote;
    }

    public String getPharmacySummary() {
        return pharmacySummary;
    }

    public void setPharmacySummary(String pharmacySummary) {
        this.pharmacySummary = pharmacySummary;
    }

    public boolean isPrescriptionCollected() {
        return prescriptionCollected;
    }

    public void setPrescriptionCollected(boolean prescriptionCollected) {
        this.prescriptionCollected = prescriptionCollected;
    }

    public boolean isPharmacyCompleted() {
        return pharmacyCompleted;
    }

    public void setPharmacyCompleted(boolean pharmacyCompleted) {
        this.pharmacyCompleted = pharmacyCompleted;
    }

    public boolean isMedicationGuidanceCompleted() {
        return medicationGuidanceCompleted;
    }

    public void setMedicationGuidanceCompleted(boolean medicationGuidanceCompleted) {
        this.medicationGuidanceCompleted = medicationGuidanceCompleted;
    }

    public boolean hasAnyPharmacyProgress() {
        return prescriptionCollected || pharmacyCompleted || medicationGuidanceCompleted;
    }

    public List<CompanionChatMessage> getChatMessages() {
        return Collections.unmodifiableList(chatMessages);
    }

    public void addChatMessage(CompanionChatMessage chatMessage) {
        this.chatMessages.add(chatMessage);
    }

    public void replaceChatMessages(List<CompanionChatMessage> messages) {
        chatMessages.clear();
        if (messages != null) {
            chatMessages.addAll(messages);
        }
    }

    public void clearChatReadState() {
        patientChatReadAtMillis = 0L;
        guardianChatReadAtMillis = 0L;
        managerChatReadAtMillis = 0L;
    }

    public CompanionLocationAlertStage getLocationAlertStage() {
        return locationAlertStage;
    }

    public long getLocationAlertSentAtMillis() {
        return locationAlertSentAtMillis;
    }

    public void setLocationAlertStage(@Nullable CompanionLocationAlertStage locationAlertStage) {
        this.locationAlertStage = locationAlertStage == null
                ? CompanionLocationAlertStage.NONE
                : locationAlertStage;
    }

    public void setLocationAlertSentAtMillis(long locationAlertSentAtMillis) {
        this.locationAlertSentAtMillis = Math.max(locationAlertSentAtMillis, 0L);
    }

    public long getPatientChatReadAtMillis() {
        return patientChatReadAtMillis;
    }

    public long getGuardianChatReadAtMillis() {
        return guardianChatReadAtMillis;
    }

    public long getManagerChatReadAtMillis() {
        return managerChatReadAtMillis;
    }

    public long getChatReadAtMillis(@Nullable UserRole role) {
        if (role == UserRole.PATIENT) {
            return patientChatReadAtMillis;
        }
        if (role == UserRole.GUARDIAN) {
            return guardianChatReadAtMillis;
        }
        if (role == UserRole.MANAGER) {
            return managerChatReadAtMillis;
        }
        return 0L;
    }

    // 채팅 화면을 열어 확인한 마지막 시각을 역할별로 저장한다.
    public void markChatRead(@Nullable UserRole role, long readAtMillis) {
        long normalizedReadAtMillis = Math.max(readAtMillis, 0L);
        if (role == UserRole.PATIENT) {
            patientChatReadAtMillis = normalizedReadAtMillis;
            return;
        }
        if (role == UserRole.GUARDIAN) {
            guardianChatReadAtMillis = normalizedReadAtMillis;
            return;
        }
        if (role == UserRole.MANAGER) {
            managerChatReadAtMillis = normalizedReadAtMillis;
        }
    }
}
