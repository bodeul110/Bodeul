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

    Optional<CompletionRecord> completeWithReport(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId,
            ReportMutation report);

    record SessionPatch(
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
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
            String currentStatus,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
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

    record CompletionRecord(SessionRecord session, ReportRecord report) {
    }
}
