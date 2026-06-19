package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;

import java.util.List;

/**
 * 매니저 홈, 가이드, 과거 이력 화면에서 사용하는 기능 전용 저장소 계약이다.
 */
public interface ManagerRepository {
    String MESSAGE_NO_ACTIVE_SESSION = "현재 배정된 동행 일정이 없습니다.";

    void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback);

    void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback);

    void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback);

    void sendCompanionChatMessage(
            String managerUserId,
            String message,
            CompanionChatAttachment attachment,
            RepositoryCallback<ManagerDashboard> callback
    );

    void markCompanionChatRead(String managerUserId);

    void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage);

    void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    );

    void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    );

    void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback);

    void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback);

    void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback);

    void savePharmacySummary(String managerUserId, String pharmacySummary, RepositoryCallback<ManagerDashboard> callback);

    void updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    );

    void updatePharmacyCompleted(String managerUserId, boolean pharmacyCompleted, RepositoryCallback<ManagerDashboard> callback);

    void updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    );

    void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback);

    void getManagerDocumentOverview(String managerUserId, RepositoryCallback<ManagerDocumentOverview> callback);

    void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    );

    void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    );

    void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    boolean isFirebaseBacked();
}
