package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

interface CompanionSessionRepository {

    List<SessionRecord> findAllForUser(UUID userId, AppUserRole role);

    Optional<SessionRecord> findById(UUID sessionId);

    Optional<ReportRecord> findReportBySessionId(UUID sessionId);

    Optional<SessionRecord> updateDetails(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            SessionPatch patch);

    Optional<SessionRecord> advance(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId);

    default Optional<SessionRecord> endCare(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId,
            boolean exposeCareEndedStatus) {
        return Optional.empty();
    }

    default Optional<SessionRecord> finalizeSession(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId,
            String managerJournal,
            boolean allowLegacyCompletion) {
        return Optional.empty();
    }

    default ReportRecord saveReportAndMarkReady(
            UUID sessionId,
            ReportMutation report) {
        throw new UnsupportedOperationException("리포트 저장을 구현해야 합니다.");
    }

    default void markReportGenerationFailed(UUID sessionId, String errorMessage) {
    }

    @Deprecated
    default Optional<CompletionRecord> completeWithReport(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId,
            ReportMutation report) {
        return Optional.empty();
    }

    record SessionPatch(
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

    record ReportMutation(
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            String medicationComparisonDecisionCode,
            String medicationComparisonNote,
            Instant nextVisitAt,
            String nextVisitNote) {
    }

    record SessionRecord(
            UUID id,
            String firestoreId,
            UUID appointmentRequestId,
            UUID managerUserId,
            UUID patientUserId,
            UUID guardianUserId,
            int currentStepOrder,
            int totalStepCount,
            GuideSnapshotRecord guideSnapshot,
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
            Instant liveLocationSharingStartedAt,
            String locationAlertStage,
            Instant locationAlertSentAt,
            long version,
            Instant startedAt,
            Instant completedAt,
            Instant canceledAt,
            Instant careEndedAt,
            String managerJournal,
            String reportGenerationStatus,
            int reportGenerationAttempts,
            String reportGenerationLastError,
            Instant reportGenerationUpdatedAt,
            List<ArtifactRecord> artifacts) {

        public SessionRecord(
                UUID id,
                String firestoreId,
                UUID appointmentRequestId,
                UUID managerUserId,
                UUID patientUserId,
                UUID guardianUserId,
                int currentStepOrder,
                int totalStepCount,
                GuideSnapshotRecord guideSnapshot,
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
                Instant liveLocationSharingStartedAt,
                String locationAlertStage,
                Instant locationAlertSentAt,
                long version,
                Instant startedAt,
                Instant completedAt,
                Instant canceledAt) {
            this(
                    id,
                    firestoreId,
                    appointmentRequestId,
                    managerUserId,
                    patientUserId,
                    guardianUserId,
                    currentStepOrder,
                    totalStepCount,
                    guideSnapshot,
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
                    null,
                    "",
                    "NOT_REQUESTED",
                    0,
                    "",
                    null,
                    List.of());
        }
    }

    record GuideSnapshotRecord(
            UUID guideId,
            Long guideRevision,
            Integer stepContractVersion,
            String source,
            boolean present,
            List<GuideStepRecord> steps) {

        public GuideSnapshotRecord {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    record GuideStepRecord(
            String code,
            int order,
            String title,
            String description,
            String videoAssetId,
            String videoAssetVersion,
            String videoFallbackText) {

        public GuideStepRecord(String code, int order, String title, String description) {
            this(code, order, title, description, null, null, null);
        }
    }

    record ArtifactRecord(
            UUID id,
            String purpose,
            String fileName,
            String contentType,
            long sizeBytes,
            Instant createdAt) {
    }

    record ReportRecord(
            UUID id,
            String firestoreId,
            UUID companionSessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            String medicationComparisonDecisionCode,
            String medicationComparisonNote,
            Instant nextVisitAt,
            String nextVisitNote,
            long version) {
    }

    @Deprecated
    record CompletionRecord(SessionRecord session, ReportRecord report) {
    }

}
