package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompanionSessionServiceTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID PATIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");
    private static final UUID GUARDIAN_ID = UUID.fromString("6b82d10f-8f20-4a77-b9b4-055a346b689d");
    private static final UUID MANAGER_ID = UUID.fromString("fdb39fea-f2da-408e-bf46-77dbf2265a73");

    private FakeCompanionSessionRepository repository;
    private DefaultCompanionSessionService service;

    @BeforeEach
    void setUp() {
        repository = new FakeCompanionSessionRepository();
        repository.session = Optional.of(session("IN_TREATMENT", 2, 5, 3));
        service = new DefaultCompanionSessionService(repository);
    }

    @Test
    void participantCanReadLinkedSession() {
        var result = service.getSession(user(PATIENT_ID, AppUserRole.PATIENT), SESSION_ID);

        assertThat(result.id()).isEqualTo(SESSION_ID);
        assertThat(result.totalStepCount()).isEqualTo(5);
    }

    @Test
    void unrelatedParticipantCannotReadSession() {
        var otherPatient = user(UUID.randomUUID(), AppUserRole.PATIENT);

        assertThatThrownBy(() -> service.getSession(otherPatient, SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void patientCannotUpdateManagerFields() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, "대기 중입니다.", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(
                user(PATIENT_ID, AppUserRole.PATIENT), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_manager_required");
    }

    @Test
    void assignedManagerUpdatesOnlyProvidedFields() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, "진료실 입장", null, null, null, null, true, null, null);

        var result = service.updateSession(manager(), SESSION_ID, command);

        assertThat(result.guardianUpdate()).isEqualTo("진료실 입장");
        assertThat(result.prescriptionCollected()).isTrue();
        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void staleVersionIsRejectedBeforeWrite() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                2, "변경", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_version_conflict");
    }

    @Test
    void advanceUsesServerGuideStepCount() {
        var result = service.advanceSession(manager(), SESSION_ID, 3);

        assertThat(result.currentStepOrder()).isEqualTo(3);
        assertThat(repository.advanceCount).isEqualTo(1);
    }

    @Test
    void advanceStopsAtLastGuideStep() {
        repository.session = Optional.of(session("PAYMENT", 5, 5, 3));

        assertThatThrownBy(() -> service.advanceSession(manager(), SESSION_ID, 3))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
    }

    @Test
    void reportCompletesSessionAndPreservesFreeTextNextVisit() {
        var command = new CompanionSessionService.SubmitReportCommand(
                3,
                "진료 동행 완료",
                "검사 완료",
                "복약 안내 완료",
                "처방약",
                "변경 없음",
                "아침 식후",
                "MATCHED",
                "기존 처방과 일치",
                "의사 안내 후 예약");

        var report = service.submitReport(manager(), SESSION_ID, command);

        assertThat(report.summary()).isEqualTo("진료 동행 완료");
        assertThat(report.nextVisitAt()).isEqualTo("의사 안내 후 예약");
        assertThat(repository.lastReport.nextVisitAt()).isNull();
    }

    @Test
    void terminalSessionRejectsFurtherWrites() {
        repository.session = Optional.of(session("COMPLETED", 5, 5, 4));
        var command = new CompanionSessionService.UpdateSessionCommand(
                4, "변경", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
    }

    private AppUserRepository.AppUser manager() {
        return user(MANAGER_ID, AppUserRole.MANAGER);
    }

    private AppUserRepository.AppUser user(UUID id, AppUserRole role) {
        return new AppUserRepository.AppUser(id, "firebase-" + id, role);
    }

    private CompanionSessionRepository.SessionRecord session(
            String status,
            int step,
            int totalSteps,
            long version) {
        return new CompanionSessionRepository.SessionRecord(
                SESSION_ID,
                "legacy-session",
                APPOINTMENT_ID,
                MANAGER_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                step,
                totalSteps,
                status,
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                version,
                Instant.parse("2026-07-18T00:00:00Z"),
                null,
                null);
    }

    private final class FakeCompanionSessionRepository implements CompanionSessionRepository {
        private Optional<SessionRecord> session = Optional.empty();
        private ReportRecord report;
        private ReportMutation lastReport;
        private int advanceCount;

        @Override
        public List<SessionRecord> findAllForUser(UUID userId, AppUserRole role) {
            return session.stream().toList();
        }

        @Override
        public Optional<SessionRecord> findById(UUID sessionId) {
            return session.filter(value -> value.id().equals(sessionId));
        }

        @Override
        public Optional<ReportRecord> findReportBySessionId(UUID sessionId) {
            return Optional.ofNullable(report);
        }

        @Override
        public Optional<SessionRecord> updateDetails(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                SessionPatch patch) {
            if (session.isEmpty() || session.get().version() != expectedVersion) {
                return Optional.empty();
            }
            SessionRecord current = session.get();
            session = Optional.of(copy(
                    current,
                    current.currentStepOrder(),
                    current.currentStatus(),
                    patch.guardianUpdate() == null ? current.guardianUpdate() : patch.guardianUpdate(),
                    Boolean.TRUE.equals(patch.prescriptionCollected()) || current.prescriptionCollected(),
                    current.version() + 1));
            return session;
        }

        @Override
        public Optional<SessionRecord> advance(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId) {
            advanceCount++;
            SessionRecord current = session.orElseThrow();
            session = Optional.of(copy(
                    current,
                    current.currentStepOrder() + 1,
                    "IN_TREATMENT",
                    current.guardianUpdate(),
                    current.prescriptionCollected(),
                    current.version() + 1));
            return session;
        }

        @Override
        public Optional<CompletionRecord> completeWithReport(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId,
                ReportMutation reportMutation) {
            lastReport = reportMutation;
            SessionRecord current = session.orElseThrow();
            session = Optional.of(copy(
                    current,
                    current.currentStepOrder(),
                    "COMPLETED",
                    current.guardianUpdate(),
                    current.prescriptionCollected(),
                    current.version() + 1));
            report = new ReportRecord(
                    UUID.randomUUID(),
                    null,
                    sessionId,
                    reportMutation.summary(),
                    reportMutation.treatmentNotes(),
                    reportMutation.medicationNotes(),
                    reportMutation.medicationName(),
                    reportMutation.medicationChangeSummary(),
                    reportMutation.medicationScheduleNote(),
                    reportMutation.medicationComparisonDecisionCode(),
                    reportMutation.medicationComparisonNote(),
                    reportMutation.nextVisitAt(),
                    reportMutation.nextVisitNote(),
                    0);
            return Optional.of(new CompletionRecord(session.get(), report));
        }

        private SessionRecord copy(
                SessionRecord current,
                int step,
                String status,
                String guardianUpdate,
                boolean prescriptionCollected,
                long version) {
            return new SessionRecord(
                    current.id(),
                    current.firestoreId(),
                    current.appointmentRequestId(),
                    current.managerUserId(),
                    current.patientUserId(),
                    current.guardianUserId(),
                    step,
                    current.totalStepCount(),
                    status,
                    guardianUpdate,
                    current.locationSummary(),
                    current.fieldPhotoNote(),
                    current.medicationNote(),
                    current.pharmacySummary(),
                    prescriptionCollected,
                    current.pharmacyCompleted(),
                    current.medicationGuidanceCompleted(),
                    version,
                    current.startedAt(),
                    current.completedAt(),
                    current.canceledAt());
        }
    }
}
