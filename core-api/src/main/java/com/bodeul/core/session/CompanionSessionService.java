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
            Boolean prescriptionCollected,
            Boolean pharmacyCompleted,
            Boolean medicationGuidanceCompleted) {
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
            String nextVisitAt) {
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
            boolean prescriptionCollected,
            boolean pharmacyCompleted,
            boolean medicationGuidanceCompleted,
            long version,
            String startedAt,
            String completedAt,
            String canceledAt) {
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
