package com.bodeul.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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
    private List<Object> events;

    @BeforeEach
    void setUp() {
        repository = new FakeCompanionSessionRepository();
        events = new ArrayList<>();
        repository.session = Optional.of(session("IN_TREATMENT", 2, 5, 3));
        service = new DefaultCompanionSessionService(repository, events::add);
    }

    @Test
    void participantCanReadLinkedSession() {
        var result = service.getSession(user(PATIENT_ID, AppUserRole.PATIENT), SESSION_ID);

        assertThat(result.id()).isEqualTo(SESSION_ID);
        assertThat(result.totalStepCount()).isEqualTo(5);
        assertThat(result.steps()).hasSize(5);
        assertThat(result.currentStepCode()).isEqualTo("STEP_2");
        assertThat(result.canAdvance()).isTrue();
        assertThat(result.blockedReason()).isNull();
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
                3, "대기 중입니다.", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(
                user(PATIENT_ID, AppUserRole.PATIENT), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_manager_required");
    }

    @Test
    void assignedManagerUpdatesOnlyProvidedFields() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, "진료실 입장", null, null, null, null, true, null, null, null, null);

        var result = service.updateSession(manager(), SESSION_ID, command);

        assertThat(result.guardianUpdate()).isEqualTo("진료실 입장");
        assertThat(result.prescriptionCollected()).isTrue();
        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void staleVersionIsRejectedBeforeWrite() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                2, "변경", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_version_conflict");
    }

    @Test
    void advanceUsesServerGuideStepCount() {
        var result = service.advanceSession(manager(), SESSION_ID, 3);

        assertThat(result.currentStepOrder()).isEqualTo(3);
        assertThat(result.currentStepCode()).isEqualTo("STEP_3");
        assertThat(repository.advanceCount).isEqualTo(1);
    }

    @Test
    void zeroOrderMeansGuideEntryBeforeFirstStep() {
        repository.session = Optional.of(session("READY", 0, 1, 3));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.currentStepCode()).isNull();
        assertThat(result.canAdvance()).isTrue();
    }

    @Test
    void oneStepSnapshotReachesLastStepAfterFirstAdvance() {
        repository.session = Optional.of(session("READY", 0, 1, 3));

        var result = service.advanceSession(manager(), SESSION_ID, 3);

        assertThat(result.currentStepOrder()).isEqualTo(1);
        assertThat(result.currentStepCode()).isEqualTo("STEP_1");
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.blockedReason()).isEqualTo("LAST_STEP_REACHED");
    }

    @Test
    void snapshotSizesAreReturnedWithoutTruncation() {
        for (int stepCount : List.of(1, 7, 13, 14)) {
            repository.session = Optional.of(session("READY", 0, stepCount, 3));

            var result = service.getSession(manager(), SESSION_ID);

            assertThat(result.totalStepCount()).isEqualTo(stepCount);
            assertThat(result.steps()).hasSize(stepCount);
            assertThat(result.canAdvance()).isTrue();
        }
    }

    @Test
    void thirteenAndLaterStepBoundariesUseFullSnapshotLength() {
        repository.session = Optional.of(session("PAYMENT", 12, 13, 3));
        assertThat(service.getSession(manager(), SESSION_ID).canAdvance()).isTrue();

        repository.session = Optional.of(session("PAYMENT", 13, 13, 3));
        assertThat(service.getSession(manager(), SESSION_ID).blockedReason())
                .isEqualTo("LAST_STEP_REACHED");

        repository.session = Optional.of(session("PAYMENT", 13, 14, 3));
        var fourteenStep = service.getSession(manager(), SESSION_ID);
        assertThat(fourteenStep.steps()).hasSize(14);
        assertThat(fourteenStep.canAdvance()).isTrue();
    }

    @Test
    void legacyCoreSevenStepSnapshotRemainsSupported() {
        List<CompanionSessionRepository.GuideStepRecord> steps = guideSteps(7);
        steps.set(2, new CompanionSessionRepository.GuideStepRecord(
                "LEGACY_CORE_RECEPTION", 3, "진료 접수", "대기 순서를 확인합니다."));
        var snapshot = new CompanionSessionRepository.GuideSnapshotRecord(
                null, null, null, "LEGACY_CORE_7_V1", true, steps);
        repository.session = Optional.of(session("IN_TREATMENT", 3, 7, 3, snapshot));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.guideId()).isNull();
        assertThat(result.guideRevision()).isNull();
        assertThat(result.steps()).hasSize(7);
        assertThat(result.currentStepCode()).isEqualTo("LEGACY_CORE_RECEPTION");
        assertThat(result.canAdvance()).isTrue();
    }

    @Test
    void unknownStepCodeIsPreservedAndDoesNotBlockProgress() {
        List<CompanionSessionRepository.GuideStepRecord> steps = guideSteps(3);
        steps.set(1, new CompanionSessionRepository.GuideStepRecord(
                "UNLISTED_EXTENSION", 2, "추가 단계", "병원별 추가 안내"));
        repository.session = Optional.of(session(
                "WAITING",
                2,
                3,
                3,
                hospitalGuideSnapshot(steps)));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.currentStepCode()).isEqualTo("UNLISTED_EXTENSION");
        assertThat(result.steps().get(1).title()).isEqualTo("추가 단계");
        assertThat(result.canAdvance()).isTrue();
    }

    @Test
    void advanceStopsAtLastGuideStep() {
        repository.session = Optional.of(session("PAYMENT", 5, 5, 3));

        var result = service.getSession(manager(), SESSION_ID);
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.blockedReason()).isEqualTo("LAST_STEP_REACHED");

        assertThatThrownBy(() -> service.advanceSession(manager(), SESSION_ID, 3))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.advanceCount).isZero();
    }

    @Test
    void missingOrEmptyGuideSnapshotBlocksProgress() {
        repository.session = Optional.of(session(
                "READY",
                0,
                0,
                3,
                new CompanionSessionRepository.GuideSnapshotRecord(
                        null, null, null, "GUIDE_NOT_FOUND", true, List.of())));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.steps()).isEmpty();
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.blockedReason()).isEqualTo("GUIDE_NOT_READY");
        assertThatThrownBy(() -> service.advanceSession(manager(), SESSION_ID, 3))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.advanceCount).isZero();

        repository.session = Optional.of(session(
                "READY",
                0,
                0,
                3,
                new CompanionSessionRepository.GuideSnapshotRecord(
                        null, null, null, "UNRESOLVED_LEGACY", false, List.of())));
        var unresolved = service.getSession(manager(), SESSION_ID);
        assertThat(unresolved.canAdvance()).isFalse();
        assertThat(unresolved.blockedReason()).isEqualTo("GUIDE_NOT_READY");
    }

    @Test
    void unsupportedLegacyGuideDoesNotInferStepCodes() {
        var legacyStep = new CompanionSessionRepository.GuideStepRecord(
                null, 1, "기존 단계", "코드 없는 기존 가이드");
        repository.session = Optional.of(session(
                "READY",
                0,
                1,
                3,
                new CompanionSessionRepository.GuideSnapshotRecord(
                        UUID.randomUUID(),
                        1L,
                        0,
                        "LEGACY_HOSPITAL_GUIDE_V0",
                        true,
                        List.of(legacyStep))));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.steps()).singleElement()
                .extracting(CompanionSessionService.GuideStepView::code)
                .isNull();
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.blockedReason()).isEqualTo("STEP_CONTRACT_MISMATCH");
    }

    @Test
    void mismatchedStepOrderAndOutOfRangeCurrentStepAreRejected() {
        List<CompanionSessionRepository.GuideStepRecord> mismatched = guideSteps(2);
        mismatched.set(1, new CompanionSessionRepository.GuideStepRecord(
                "STEP_2", 3, "단계 2", "설명 2"));
        repository.session = Optional.of(session(
                "WAITING", 2, 2, 3, hospitalGuideSnapshot(mismatched)));

        var mismatchedResult = service.getSession(manager(), SESSION_ID);
        assertThat(mismatchedResult.currentStepCode()).isNull();
        assertThat(mismatchedResult.blockedReason()).isEqualTo("STEP_CONTRACT_MISMATCH");

        repository.session = Optional.of(session("WAITING", 8, 7, 3));
        var outOfRangeResult = service.getSession(manager(), SESSION_ID);
        assertThat(outOfRangeResult.currentStepCode()).isNull();
        assertThat(outOfRangeResult.blockedReason()).isEqualTo("STEP_CONTRACT_MISMATCH");

        assertThatThrownBy(() -> service.advanceSession(manager(), SESSION_ID, 3))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.advanceCount).isZero();
    }

    @Test
    void reportCompletesSessionAndPreservesFreeTextNextVisit() {
        repository.session = Optional.of(session("RETURNING", 5, 5, 3));
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
    void reportRejectsSessionWithoutValidatedLastStepContract() {
        var command = new CompanionSessionService.SubmitReportCommand(
                3,
                "진료 동행 완료",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
        var unsupportedSnapshot = new CompanionSessionRepository.GuideSnapshotRecord(
                UUID.randomUUID(),
                1L,
                0,
                "LEGACY_HOSPITAL_GUIDE_V0",
                true,
                List.of(new CompanionSessionRepository.GuideStepRecord(
                        null,
                        1,
                        "기존 마지막 단계",
                        "코드 없는 기존 가이드")));
        repository.session = Optional.of(session(
                "RETURNING",
                1,
                1,
                3,
                unsupportedSnapshot));

        assertThatThrownBy(() -> service.submitReport(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.lastReport).isNull();
    }

    @Test
    void terminalSessionRejectsFurtherWrites() {
        repository.session = Optional.of(session("COMPLETED", 5, 5, 4));
        var command = new CompanionSessionService.UpdateSessionCommand(
                4, "변경", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");

        var result = service.getSession(manager(), SESSION_ID);
        assertThat(result.currentStepCode()).isEqualTo("STEP_5");
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.blockedReason()).isEqualTo("SESSION_TERMINAL");
    }

    @Test
    void invalidLocationAlertStageIsRejected() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, null, null, null, null, "unexpected");

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");
    }

    @Test
    void changedLocationAlertPublishesParticipantNotificationAfterWrite() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, null, null, null, null, "hospital_near");

        service.updateSession(manager(), SESSION_ID, command);

        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(CompanionLocationAlertChangedEvent.class, event -> {
                    assertThat(event.alertStage()).isEqualTo("hospital_near");
                    assertThat(event.recipientUserIds()).containsExactly(PATIENT_ID, GUARDIAN_ID);
                });
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
        return session(status, step, totalSteps, version, hospitalGuideSnapshot(guideSteps(totalSteps)));
    }

    private CompanionSessionRepository.SessionRecord session(
            String status,
            int step,
            int totalSteps,
            long version,
            CompanionSessionRepository.GuideSnapshotRecord guideSnapshot) {
        return new CompanionSessionRepository.SessionRecord(
                SESSION_ID,
                "legacy-session",
                APPOINTMENT_ID,
                MANAGER_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                step,
                totalSteps,
                guideSnapshot,
                status,
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                false,
                null,
                "none",
                null,
                version,
                Instant.parse("2026-07-18T00:00:00Z"),
                null,
                null);
    }

    private CompanionSessionRepository.GuideSnapshotRecord hospitalGuideSnapshot(
            List<CompanionSessionRepository.GuideStepRecord> steps) {
        return new CompanionSessionRepository.GuideSnapshotRecord(
                UUID.fromString("45bd0403-59a7-449a-90f6-fae10c79da30"),
                2L,
                1,
                "HOSPITAL_GUIDE_STEP_CODE_V1",
                true,
                steps);
    }

    private List<CompanionSessionRepository.GuideStepRecord> guideSteps(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count)
                .mapToObj(order -> new CompanionSessionRepository.GuideStepRecord(
                        "STEP_" + order,
                        order,
                        "단계 " + order,
                        "설명 " + order))
                .toList());
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
                    patch.locationAlertStage() == null
                            ? current.locationAlertStage()
                            : patch.locationAlertStage(),
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
                    current.locationAlertStage(),
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
                    current.locationAlertStage(),
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
                String locationAlertStage,
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
                    current.guideSnapshot(),
                    status,
                    guardianUpdate,
                    current.locationSummary(),
                    current.fieldPhotoNote(),
                    current.medicationNote(),
                    current.pharmacySummary(),
                    prescriptionCollected,
                    current.pharmacyCompleted(),
                    current.medicationGuidanceCompleted(),
                    current.liveLocationSharingActive(),
                    current.liveLocationSharingStartedAt(),
                    locationAlertStage,
                    current.locationAlertSentAt(),
                    version,
                    current.startedAt(),
                    current.completedAt(),
                    current.canceledAt());
        }
    }
}
