package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companion-sessions")
@Profile({"database", "companion-session-test"})
class CompanionSessionController {

    private final CompanionSessionService sessionService;

    CompanionSessionController(CompanionSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    ResponseEntity<SessionsResponse> getMySessions(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser) {
        return noStore(new SessionsResponse(sessionService.getMySessions(appUser)));
    }

    @GetMapping("/{sessionId}")
    ResponseEntity<CompanionSessionService.SessionView> getSession(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId) {
        return noStore(sessionService.getSession(appUser, sessionId));
    }

    @PatchMapping("/{sessionId}")
    ResponseEntity<CompanionSessionService.SessionView> updateSession(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody UpdateSessionRequest request) {
        return noStore(sessionService.updateSession(
                appUser,
                sessionId,
                request == null ? null : request.toCommand()));
    }

    @PostMapping("/{sessionId}/advance")
    ResponseEntity<CompanionSessionService.SessionView> advanceSession(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody VersionRequest request) {
        long version = request == null || request.version() == null ? -1 : request.version();
        return noStore(sessionService.advanceSession(appUser, sessionId, version));
    }

    @GetMapping("/{sessionId}/report")
    ResponseEntity<CompanionSessionService.ReportView> getReport(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId) {
        return noStore(sessionService.getReport(appUser, sessionId));
    }

    @PutMapping("/{sessionId}/report")
    ResponseEntity<CompanionSessionService.ReportView> submitReport(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody SubmitReportRequest request) {
        return noStore(sessionService.submitReport(
                appUser,
                sessionId,
                request == null ? null : request.toCommand()));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record SessionsResponse(List<CompanionSessionService.SessionView> sessions) {
    }

    record VersionRequest(Long version) {
    }

    record UpdateSessionRequest(
            Long version,
            String guardianUpdate,
            String locationSummary,
            String fieldPhotoNote,
            String medicationNote,
            String pharmacySummary,
            Boolean prescriptionCollected,
            Boolean pharmacyCompleted,
            Boolean medicationGuidanceCompleted) {

        CompanionSessionService.UpdateSessionCommand toCommand() {
            return new CompanionSessionService.UpdateSessionCommand(
                    version == null ? -1 : version,
                    guardianUpdate,
                    locationSummary,
                    fieldPhotoNote,
                    medicationNote,
                    pharmacySummary,
                    prescriptionCollected,
                    pharmacyCompleted,
                    medicationGuidanceCompleted);
        }
    }

    record SubmitReportRequest(
            Long version,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            String medicationComparisonDecisionCode,
            String medicationComparisonNote,
            String nextVisitAt) {

        CompanionSessionService.SubmitReportCommand toCommand() {
            return new CompanionSessionService.SubmitReportCommand(
                    version == null ? -1 : version,
                    summary,
                    treatmentNotes,
                    medicationNotes,
                    medicationName,
                    medicationChangeSummary,
                    medicationScheduleNote,
                    medicationComparisonDecisionCode,
                    medicationComparisonNote,
                    nextVisitAt);
        }
    }
}
