package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;

public interface CompanionSessionService {

    List<SessionView> getMySessions(AppUserRepository.AppUser appUser);

    SessionView getSession(AppUserRepository.AppUser appUser, UUID sessionId);

    SessionView updateSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UpdateSessionCommand command);

    SessionView advanceSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            long version);

    default SessionView endCare(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            long version) {
        return advanceSession(appUser, sessionId, version);
    }

    ReportView getReport(AppUserRepository.AppUser appUser, UUID sessionId);

    ReportView submitReport(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            SubmitReportCommand command);

    record UpdateSessionCommand(
            long version,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            Boolean preConsultationConfirmed,
            Boolean prescriptionCollected,
            Boolean pharmacyCompleted,
            Boolean medicationGuidanceCompleted,
            Boolean liveLocationSharingActive,
            String locationAlertStage) {
    }

    record SubmitReportCommand(
            long version,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            String medicationComparisonDecisionCode,
            String medicationComparisonNote,
            String nextVisitAt,
            String managerJournal) {

        public SubmitReportCommand(
                long version,
                String summary,
                String treatmentNotes,
                String medicationNotes,
                String medicationName,
                String medicationChangeSummary,
                String medicationScheduleNote,
                String medicationComparisonDecisionCode,
                String medicationComparisonNote,
                String nextVisitAt) {
            this(
                    version,
                    summary,
                    treatmentNotes,
                    medicationNotes,
                    medicationName,
                    medicationChangeSummary,
                    medicationScheduleNote,
                    medicationComparisonDecisionCode,
                    medicationComparisonNote,
                    nextVisitAt,
                    summary);
        }
    }

    record SessionView(
            UUID id,
            String legacyFirestoreId,
            UUID appointmentRequestId,
            UUID managerUserId,
            int currentStepOrder,
            int totalStepCount,
            String currentStatus,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            boolean preConsultationConfirmed,
            boolean prescriptionCollected,
            boolean pharmacyCompleted,
            boolean medicationGuidanceCompleted,
            boolean liveLocationSharingActive,
            String liveLocationSharingStartedAt,
            String locationAlertStage,
            String locationAlertSentAt,
            long version,
            String startedAt,
            String completedAt,
            String canceledAt,
            UUID guideId,
            Long guideRevision,
            List<GuideStepView> steps,
            String currentStepCode,
            boolean canAdvance,
            String blockedReason,
            String careEndedAt,
            String managerJournal,
            String reportGenerationStatus,
            int reportGenerationAttempts,
            String reportGenerationLastError,
            String reportGenerationUpdatedAt,
            List<ArtifactView> artifacts) {

        public SessionView(
                UUID id,
                String legacyFirestoreId,
                UUID appointmentRequestId,
                UUID managerUserId,
                int currentStepOrder,
                int totalStepCount,
                String currentStatus,
                String guardianUpdate,
                String locationSummary,
                String fieldPhotoNote,
                String medicationNote,
                String pharmacySummary,
                boolean preConsultationConfirmed,
                boolean prescriptionCollected,
                boolean pharmacyCompleted,
                boolean medicationGuidanceCompleted,
                boolean liveLocationSharingActive,
                String liveLocationSharingStartedAt,
                String locationAlertStage,
                String locationAlertSentAt,
                long version,
                String startedAt,
                String completedAt,
                String canceledAt,
                UUID guideId,
                Long guideRevision,
                List<GuideStepView> steps,
                String currentStepCode,
                boolean canAdvance,
                String blockedReason) {
            this(
                    id,
                    legacyFirestoreId,
                    appointmentRequestId,
                    managerUserId,
                    currentStepOrder,
                    totalStepCount,
                    currentStatus,
                    guardianUpdate,
                    locationSummary,
                    fieldPhotoNote,
                    medicationNote,
                    pharmacySummary,
                    preConsultationConfirmed,
                    prescriptionCollected,
                    pharmacyCompleted,
                    medicationGuidanceCompleted,
                    liveLocationSharingActive,
                    liveLocationSharingStartedAt,
                    locationAlertStage,
                    locationAlertSentAt,
                    version,
                    startedAt,
                    completedAt,
                    canceledAt,
                    guideId,
                    guideRevision,
                    steps,
                    currentStepCode,
                    canAdvance,
                    blockedReason,
                    "",
                    "",
                    "NOT_REQUESTED",
                    0,
                    "",
                    "",
                    List.of());
        }
    }

    record GuideStepView(
            String code,
            int order,
            String title,
            String description,
            String videoAssetId,
            String videoAssetVersion,
            String videoFallbackText) {

        public GuideStepView(String code, int order, String title, String description) {
            this(code, order, title, description, null, null, null);
        }
    }

    record ArtifactView(
            UUID id,
            String purpose,
            String fileName,
            String contentType,
            long sizeBytes,
            String createdAt) {
    }

    record ReportView(
            UUID id,
            String legacyFirestoreId,
            UUID companionSessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            String medicationComparisonDecisionCode,
            String medicationComparisonNote,
            String nextVisitAt,
            long version) {
    }
}
