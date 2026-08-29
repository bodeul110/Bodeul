package com.bodeul.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentAccess;
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
    private static final List<String> PRODUCT_GUIDE_STEP_CODES = List.of(
            "MEETING_CONFIRMATION",
            "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE",
            "VITALS_CHECK",
            "PRE_CONSULTATION",
            "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY",
            "PAYMENT_EVIDENCE",
            "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS",
            "MEDICATION_CONFIRMATION",
            "CARE_COMPLETION",
            "MANAGER_JOURNAL");
    private static final List<String> LEGACY_GUIDE_STEP_CODES = List.of(
            "LEGACY_CORE_PATIENT_CONTACT",
            "LEGACY_CORE_RECEPTION_PREPARATION",
            "LEGACY_CORE_RECEPTION",
            "LEGACY_CORE_CONSULTATION",
            "LEGACY_CORE_PAYMENT",
            "LEGACY_CORE_PHARMACY",
            "LEGACY_CORE_RETURN_AND_CLOSE");

    private FakeCompanionSessionRepository repository;
    private DefaultCompanionSessionService service;
    private List<Object> events;
    private RecordingConsentAccess consentAccess;

    @BeforeEach
    void setUp() {
        repository = new FakeCompanionSessionRepository();
        events = new ArrayList<>();
        consentAccess = new RecordingConsentAccess();
        repository.session = Optional.of(session("IN_TREATMENT", 2, 5, 3));
        service = new DefaultCompanionSessionService(
                repository,
                events::add,
                properties(false),
                consentAccess);
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
                3, "대기 중입니다.", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(
                user(PATIENT_ID, AppUserRole.PATIENT), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_manager_required");
    }

    @Test
    void assignedManagerUpdatesOnlyProvidedFields() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, "진료실 입장", null, null, null, null, null, true, null, null, null, null);

        var result = service.updateSession(manager(), SESSION_ID, command);

        assertThat(result.guardianUpdate()).isEqualTo("진료실 입장");
        assertThat(result.prescriptionCollected()).isTrue();
        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void staleVersionIsRejectedBeforeWrite() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                2, "변경", null, null, null, null, null, null, null, null, null, null);

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
    void disabledPreConsultationEnforcementAllowsExistingAppToAdvance() {
        List<CompanionSessionRepository.GuideStepRecord> steps = guideSteps(3);
        steps.set(1, new CompanionSessionRepository.GuideStepRecord(
                "PRE_CONSULTATION",
                2,
                "진료 전 확인",
                "증상과 질문을 확인합니다."));
        repository.session = Optional.of(session(
                "WAITING",
                2,
                3,
                3,
                hospitalGuideSnapshot(steps),
                false));

        var current = service.getSession(manager(), SESSION_ID);

        assertThat(current.preConsultationConfirmed()).isFalse();
        assertThat(current.canAdvance()).isTrue();
        assertThat(current.blockedReason()).isNull();

        var advanced = service.advanceSession(manager(), SESSION_ID, 3);

        assertThat(advanced.currentStepOrder()).isEqualTo(3);
        assertThat(repository.advanceCount).isEqualTo(1);
    }

    @Test
    void enabledPreConsultationEnforcementRequiresPersistedConfirmationBeforeAdvance() {
        List<CompanionSessionRepository.GuideStepRecord> steps = guideSteps(3);
        steps.set(1, new CompanionSessionRepository.GuideStepRecord(
                "PRE_CONSULTATION",
                2,
                "진료 전 확인",
                "증상과 질문을 확인합니다."));
        repository.session = Optional.of(session(
                "WAITING",
                2,
                3,
                3,
                hospitalGuideSnapshot(steps),
                false));
        DefaultCompanionSessionService enforcedService =
                new DefaultCompanionSessionService(
                        repository,
                        events::add,
                        properties(true),
                        (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true);

        var blocked = enforcedService.getSession(manager(), SESSION_ID);

        assertThat(blocked.preConsultationConfirmed()).isFalse();
        assertThat(blocked.canAdvance()).isFalse();
        assertThat(blocked.blockedReason()).isEqualTo("STEP_INPUT_REQUIRED");
        assertThatThrownBy(() -> enforcedService.advanceSession(manager(), SESSION_ID, 3))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.advanceCount).isZero();

        var confirmed = enforcedService.updateSession(
                manager(),
                SESSION_ID,
                new CompanionSessionService.UpdateSessionCommand(
                        3,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(confirmed.preConsultationConfirmed()).isTrue();
        assertThat(confirmed.canAdvance()).isTrue();
        assertThat(confirmed.blockedReason()).isNull();

        var advanced = enforcedService.advanceSession(manager(), SESSION_ID, confirmed.version());
        assertThat(advanced.currentStepOrder()).isEqualTo(3);
        assertThat(repository.advanceCount).isEqualTo(1);
    }

    @Test
    void preConsultationConfirmationCannotBeWrittenFromAnotherStep() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, true, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");
    }

    private CompanionSessionProperties properties(boolean preConsultationEnforcement) {
        CompanionSessionProperties properties = new CompanionSessionProperties();
        properties.setPreConsultationEnforcement(preConsultationEnforcement);
        return properties;
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
    void sevenThirteenAndFourteenStepSnapshotsAdvanceToReportCompletion() {
        for (int stepCount : List.of(7, 13, 14)) {
            repository.session = Optional.of(session(
                    "READY",
                    0,
                    stepCount,
                    3,
                    transitionSnapshot(stepCount),
                    stepCount >= 13));
            repository.report = null;
            repository.lastReport = null;
            repository.advanceCount = 0;

            assertReportSubmissionBlocked(3);

            for (int expectedOrder = 1; expectedOrder <= stepCount; expectedOrder++) {
                var beforeAdvance = service.getSession(manager(), SESSION_ID);
                assertThat(beforeAdvance.currentStepOrder()).isEqualTo(expectedOrder - 1);
                assertThat(beforeAdvance.canAdvance()).isTrue();

                var advanced = service.advanceSession(
                        manager(),
                        SESSION_ID,
                        beforeAdvance.version());
                String expectedCode = transitionStepCodes(stepCount).get(expectedOrder - 1);

                assertThat(advanced.currentStepOrder()).isEqualTo(expectedOrder);
                assertThat(advanced.currentStepCode()).isEqualTo(expectedCode);
                assertThat(advanced.version()).isEqualTo(3 + expectedOrder);
                if (expectedOrder < stepCount) {
                    assertThat(advanced.canAdvance()).isTrue();
                    assertThat(advanced.blockedReason()).isNull();
                } else {
                    assertThat(advanced.canAdvance()).isFalse();
                    assertThat(advanced.blockedReason()).isEqualTo("LAST_STEP_REACHED");
                }

                var reloaded = service.getSession(manager(), SESSION_ID);
                assertThat(reloaded.currentStepOrder()).isEqualTo(expectedOrder);
                assertThat(reloaded.currentStepCode()).isEqualTo(expectedCode);
                assertThat(reloaded.version()).isEqualTo(advanced.version());
                assertThat(reloaded.canAdvance()).isEqualTo(advanced.canAdvance());
                assertThat(reloaded.blockedReason()).isEqualTo(advanced.blockedReason());
                if (expectedOrder < stepCount) {
                    assertReportSubmissionBlocked(reloaded.version());
                    assertThat(repository.lastReport).isNull();
                }
            }

            var lastStep = service.getSession(manager(), SESSION_ID);
            assertThatThrownBy(() -> service.advanceSession(
                    manager(),
                    SESSION_ID,
                    lastStep.version()))
                    .isInstanceOf(CompanionSessionException.class)
                    .extracting(exception -> ((CompanionSessionException) exception).error())
                    .isEqualTo("companion_session_state_conflict");
            assertThat(repository.advanceCount).isEqualTo(stepCount);

            var report = service.submitReport(
                    manager(),
                    SESSION_ID,
                    reportCommand(lastStep.version()));
            assertThat(report.summary()).isEqualTo("전체 단계 회귀 검증 완료");
            assertThat(repository.lastReport).isNotNull();
            assertThat(consentAccess.finalizedAppointmentId).isEqualTo(APPOINTMENT_ID);
            assertThat(consentAccess.careEndedAt).isNotNull();

            var completed = service.getSession(manager(), SESSION_ID);
            assertThat(completed.currentStatus()).isEqualTo("COMPLETED");
            assertThat(completed.currentStepOrder()).isEqualTo(stepCount);
            assertThat(completed.currentStepCode())
                    .isEqualTo(transitionStepCodes(stepCount).get(stepCount - 1));
            assertThat(completed.canAdvance()).isFalse();
            assertThat(completed.blockedReason()).isEqualTo("SESSION_TERMINAL");
        }
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
    void careEndIsIdempotentAndPreservesFirstServerTimestamp() {
        repository.session = Optional.of(session(
                "IN_TREATMENT",
                12,
                13,
                3,
                transitionSnapshot(13)));

        var first = service.endCare(manager(), SESSION_ID, 3);
        var retried = service.endCare(manager(), SESSION_ID, 3);

        assertThat(first.currentStatus()).isEqualTo("IN_TREATMENT");
        assertThat(first.currentStepCode()).isEqualTo("MANAGER_JOURNAL");
        assertThat(first.careEndedAt()).isNotBlank();
        assertThat(retried.careEndedAt()).isEqualTo(first.careEndedAt());
        assertThat(retried.version()).isEqualTo(first.version());
        assertThat(retried.blockedReason()).isEqualTo("CARE_ENDED_PENDING_COMPLETION");
    }

    @Test
    void completionEnforcementExposesCareEndedOnlyAfterMixedVersionWindow() {
        repository.session = Optional.of(session(
                "IN_TREATMENT",
                12,
                13,
                3,
                transitionSnapshot(13)));
        CompanionSessionProperties enforcedProperties = properties(false);
        enforcedProperties.setCompletionEnforcement(true);
        DefaultCompanionSessionService enforcedService = new DefaultCompanionSessionService(
                repository,
                events::add,
                enforcedProperties,
                consentAccess);

        var result = enforcedService.endCare(manager(), SESSION_ID, 3);

        assertThat(result.currentStatus()).isEqualTo("CARE_ENDED");
        assertThat(result.blockedReason()).isEqualTo("CARE_ENDED_PENDING_COMPLETION");
    }

    @Test
    void disabledCompletionEnforcementMapsExistingCareEndedToLegacyStatus() {
        repository.session = Optional.of(repository.copyCompletion(
                session("IN_TREATMENT", 12, 13, 4, transitionSnapshot(13)),
                13,
                "CARE_ENDED",
                5,
                Instant.parse("2026-07-18T00:59:00Z"),
                null,
                "",
                "NOT_REQUESTED",
                0,
                ""));

        var result = service.getSession(manager(), SESSION_ID);

        assertThat(result.currentStatus()).isEqualTo("PAYMENT");
        assertThat(result.blockedReason()).isEqualTo("CARE_ENDED_PENDING_COMPLETION");
    }

    @Test
    void reportFailureKeepsCompletedSessionAndRetryDoesNotMoveCareEndTimestamp() {
        repository.session = Optional.of(session(
                "IN_TREATMENT",
                12,
                13,
                3,
                transitionSnapshot(13)));
        var careEnded = service.endCare(manager(), SESSION_ID, 3);
        repository.failNextReportWrite = true;

        assertThatThrownBy(() -> service.submitReport(
                manager(),
                SESSION_ID,
                reportCommand(careEnded.version())))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_report_generation_failed");

        var failed = service.getSession(manager(), SESSION_ID);
        assertThat(failed.currentStatus()).isEqualTo("COMPLETED");
        assertThat(failed.reportGenerationStatus()).isEqualTo("FAILED");
        assertThat(failed.blockedReason()).isEqualTo("REPORT_RETRY_REQUIRED");
        assertThat(failed.careEndedAt()).isEqualTo(careEnded.careEndedAt());

        var report = service.submitReport(
                manager(),
                SESSION_ID,
                reportCommand(careEnded.version()));
        var completed = service.getSession(manager(), SESSION_ID);

        assertThat(report.summary()).isEqualTo("전체 단계 회귀 검증 완료");
        assertThat(completed.reportGenerationStatus()).isEqualTo("READY");
        assertThat(completed.blockedReason()).isEqualTo("SESSION_TERMINAL");
        assertThat(completed.careEndedAt()).isEqualTo(careEnded.careEndedAt());
        assertThat(completed.reportGenerationAttempts()).isEqualTo(2);
    }

    @Test
    void managerJournalIsOptionalAndLimitedToThreeHundredCharacters() {
        repository.session = Optional.of(session(
                "IN_TREATMENT",
                12,
                13,
                3,
                transitionSnapshot(13)));
        var careEnded = service.endCare(manager(), SESSION_ID, 3);
        var blankJournal = new CompanionSessionService.SubmitReportCommand(
                careEnded.version(), "", "", "", "", "", "", "", "", "", "");

        assertThat(service.submitReport(manager(), SESSION_ID, blankJournal).summary()).isEmpty();

        repository.session = Optional.of(session(
                "IN_TREATMENT",
                12,
                13,
                3,
                transitionSnapshot(13)));
        careEnded = service.endCare(manager(), SESSION_ID, 3);
        long version = careEnded.version();
        var tooLong = new CompanionSessionService.SubmitReportCommand(
                version,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "가".repeat(301));

        assertThatThrownBy(() -> service.submitReport(manager(), SESSION_ID, tooLong))
                .isInstanceOf(CompanionSessionException.class)
                .hasMessageContaining("300자 이하");
    }

    @Test
    void completionEnforcementBlocksLegacyDirectCompletionWithoutCareEnd() {
        repository.session = Optional.of(session(
                "IN_TREATMENT",
                13,
                13,
                3,
                transitionSnapshot(13)));
        CompanionSessionProperties enforcedProperties = properties(false);
        enforcedProperties.setCompletionEnforcement(true);
        DefaultCompanionSessionService enforcedService = new DefaultCompanionSessionService(
                repository,
                events::add,
                enforcedProperties,
                consentAccess);

        assertThatThrownBy(() -> enforcedService.submitReport(
                manager(),
                SESSION_ID,
                reportCommand(3)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThat(repository.lastReport).isNull();
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
                4, "변경", null, null, null, null, null, null, null, null, null, null);

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
    void guardianRelationshipWithoutAppointmentConsentIsRejected() {
        var failClosedService = new DefaultCompanionSessionService(
                repository,
                events::add,
                properties(false),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> false);

        assertThatThrownBy(() -> failClosedService.getSession(
                user(GUARDIAN_ID, AppUserRole.GUARDIAN),
                SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void guardianSessionArtifactsFollowConsentAndWithdrawal() {
        repository.session = Optional.of(withArtifact(repository.session.orElseThrow()));
        boolean[] attachmentAllowed = {false};
        var scopedService = new DefaultCompanionSessionService(
                repository,
                events::add,
                properties(false),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) ->
                        scope != InformationScope.ATTACHMENT || attachmentAllowed[0]);
        AppUserRepository.AppUser guardian = user(GUARDIAN_ID, AppUserRole.GUARDIAN);

        assertThat(scopedService.getSession(guardian, SESSION_ID).artifacts()).isEmpty();

        attachmentAllowed[0] = true;
        assertThat(scopedService.getSession(guardian, SESSION_ID).artifacts()).hasSize(1);

        attachmentAllowed[0] = false;
        assertThat(scopedService.getSession(guardian, SESSION_ID).artifacts()).isEmpty();
    }

    @Test
    void invalidLocationAlertStageIsRejected() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, null, null, null, null, null, "unexpected");

        assertThatThrownBy(() -> service.updateSession(manager(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");
    }

    @Test
    void changedLocationAlertPublishesParticipantNotificationAfterWrite() {
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, null, null, null, null, null, "hospital_near");

        service.updateSession(manager(), SESSION_ID, command);

        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(CompanionLocationAlertChangedEvent.class, event -> {
                    assertThat(event.alertStage()).isEqualTo("hospital_near");
                    assertThat(event.recipientUserIds()).containsExactly(PATIENT_ID, GUARDIAN_ID);
                });
    }

    @Test
    void locationAlertDoesNotTargetRelatedGuardianWithoutLocationConsent() {
        var failClosedService = new DefaultCompanionSessionService(
                repository,
                events::add,
                properties(false),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> false);
        var command = new CompanionSessionService.UpdateSessionCommand(
                3, null, null, null, null, null, null, null, null, null, null, "hospital_near");

        failClosedService.updateSession(manager(), SESSION_ID, command);

        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(CompanionLocationAlertChangedEvent.class, event ->
                        assertThat(event.recipientUserIds()).containsExactly(PATIENT_ID));
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
        return session(status, step, totalSteps, version, guideSnapshot, false);
    }

    private CompanionSessionRepository.SessionRecord session(
            String status,
            int step,
            int totalSteps,
            long version,
            CompanionSessionRepository.GuideSnapshotRecord guideSnapshot,
            boolean preConsultationConfirmed) {
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
                preConsultationConfirmed,
                false,
                false,
                false,
                false,
                null,
                "none",
                null,
                version,
                Instant.parse("2026-07-18T00:00:00Z"),
                "COMPLETED".equals(status) ? Instant.parse("2026-07-18T01:00:00Z") : null,
                null,
                "COMPLETED".equals(status) ? Instant.parse("2026-07-18T00:59:00Z") : null,
                "",
                "COMPLETED".equals(status) ? "READY" : "NOT_REQUESTED",
                "COMPLETED".equals(status) ? 1 : 0,
                "",
                "COMPLETED".equals(status) ? Instant.parse("2026-07-18T01:00:00Z") : null,
                List.of());
    }

    private CompanionSessionRepository.SessionRecord withArtifact(
            CompanionSessionRepository.SessionRecord current) {
        return new CompanionSessionRepository.SessionRecord(
                current.id(), current.firestoreId(), current.appointmentRequestId(),
                current.managerUserId(), current.patientUserId(), current.guardianUserId(),
                current.currentStepOrder(), current.totalStepCount(), current.guideSnapshot(),
                current.currentStatus(), current.guardianUpdate(), current.locationSummary(),
                current.fieldPhotoNote(), current.medicationNote(), current.pharmacySummary(),
                current.preConsultationConfirmed(), current.prescriptionCollected(),
                current.pharmacyCompleted(), current.medicationGuidanceCompleted(),
                current.liveLocationSharingActive(), current.liveLocationSharingStartedAt(),
                current.locationAlertStage(), current.locationAlertSentAt(), current.version(),
                current.startedAt(), current.completedAt(), current.canceledAt(),
                current.careEndedAt(), current.managerJournal(),
                current.reportGenerationStatus(), current.reportGenerationAttempts(),
                current.reportGenerationLastError(), current.reportGenerationUpdatedAt(),
                List.of(new CompanionSessionRepository.ArtifactRecord(
                        UUID.randomUUID(), "PAYMENT_EVIDENCE", "영수증.pdf",
                        "application/pdf", 10L, Instant.parse("2026-07-18T00:00:00Z"))));
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

    private CompanionSessionRepository.GuideSnapshotRecord transitionSnapshot(int stepCount) {
        List<CompanionSessionRepository.GuideStepRecord> steps = new ArrayList<>();
        List<String> codes = transitionStepCodes(stepCount);
        for (int index = 0; index < codes.size(); index++) {
            int order = index + 1;
            steps.add(new CompanionSessionRepository.GuideStepRecord(
                    codes.get(index),
                    order,
                    "단계 " + order,
                    "설명 " + order));
        }
        if (stepCount == 7) {
            return new CompanionSessionRepository.GuideSnapshotRecord(
                    null,
                    null,
                    null,
                    "LEGACY_CORE_7_V1",
                    true,
                    steps);
        }
        return hospitalGuideSnapshot(steps);
    }

    private List<String> transitionStepCodes(int stepCount) {
        if (stepCount == 7) {
            return LEGACY_GUIDE_STEP_CODES;
        }
        List<String> codes = new ArrayList<>(PRODUCT_GUIDE_STEP_CODES);
        if (stepCount == 14) {
            codes.add("HOSPITAL_EXTENSION");
        }
        return codes;
    }

    private CompanionSessionService.SubmitReportCommand reportCommand(long version) {
        return new CompanionSessionService.SubmitReportCommand(
                version,
                "전체 단계 회귀 검증 완료",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
    }

    private void assertReportSubmissionBlocked(long version) {
        assertThatThrownBy(() -> service.submitReport(
                manager(),
                SESSION_ID,
                reportCommand(version)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
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
        private boolean failNextReportWrite;

        @Override
        public List<SessionRecord> findAllForUser(UUID userId, AppUserRole role) {
            return session.stream().toList();
        }

        @Override
        public Optional<SessionRecord> findById(UUID sessionId) {
            return session
                    .filter(value -> value.id().equals(sessionId))
                    .map(current -> copy(
                            current,
                            current.currentStepOrder(),
                            current.currentStatus(),
                            current.guardianUpdate(),
                            current.preConsultationConfirmed(),
                            current.prescriptionCollected(),
                            current.locationAlertStage(),
                            current.version()));
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
                    patch.preConsultationConfirmed() == null
                            ? current.preConsultationConfirmed()
                            : patch.preConsultationConfirmed(),
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
            if (session.isEmpty()) {
                return Optional.empty();
            }
            SessionRecord current = session.get();
            if (!current.id().equals(sessionId)
                    || !current.managerUserId().equals(managerUserId)
                    || !current.appointmentRequestId().equals(appointmentRequestId)
                    || current.version() != expectedVersion
                    || current.currentStepOrder() >= current.totalStepCount()) {
                return Optional.empty();
            }
            advanceCount++;
            session = Optional.of(copy(
                    current,
                    current.currentStepOrder() + 1,
                    "IN_TREATMENT",
                    current.guardianUpdate(),
                    current.preConsultationConfirmed(),
                    current.prescriptionCollected(),
                    current.locationAlertStage(),
                    current.version() + 1));
            return session;
        }

        @Override
        public Optional<SessionRecord> endCare(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId,
                boolean exposeCareEndedStatus) {
            if (session.isEmpty()) {
                return Optional.empty();
            }
            SessionRecord current = session.get();
            if (!current.id().equals(sessionId)
                    || !current.managerUserId().equals(managerUserId)
                    || !current.appointmentRequestId().equals(appointmentRequestId)) {
                return Optional.empty();
            }
            if (current.careEndedAt() != null
                    && !"CANCELED".equals(current.currentStatus())) {
                return session;
            }
            if (current.version() != expectedVersion
                    || !"CARE_COMPLETION".equals(
                    current.guideSnapshot().steps().get(current.currentStepOrder() - 1).code())) {
                return Optional.empty();
            }
            session = Optional.of(copyCompletion(
                    current,
                    current.currentStepOrder() + 1,
                    exposeCareEndedStatus ? "CARE_ENDED" : current.currentStatus(),
                    current.version() + 1,
                    Instant.parse("2026-07-18T00:59:00Z"),
                    null,
                    current.managerJournal(),
                    "NOT_REQUESTED",
                    current.reportGenerationAttempts(),
                    ""));
            return session;
        }

        @Override
        public Optional<SessionRecord> finalizeSession(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId,
                String managerJournal,
                boolean allowLegacyCompletion) {
            if (session.isEmpty()) {
                return Optional.empty();
            }
            SessionRecord current = session.get();
            boolean allowedState = "CARE_ENDED".equals(current.currentStatus())
                    || (allowLegacyCompletion
                    && current.currentStepOrder() == current.totalStepCount()
                    && !"COMPLETED".equals(current.currentStatus())
                    && !"CANCELED".equals(current.currentStatus()));
            if (!current.id().equals(sessionId)
                    || !current.managerUserId().equals(managerUserId)
                    || !current.appointmentRequestId().equals(appointmentRequestId)
                    || current.version() != expectedVersion
                    || !allowedState) {
                return Optional.empty();
            }
            session = Optional.of(copyCompletion(
                    current,
                    current.currentStepOrder(),
                    "COMPLETED",
                    current.version() + 1,
                    current.careEndedAt() == null
                            ? Instant.parse("2026-07-18T00:59:00Z")
                            : current.careEndedAt(),
                    Instant.parse("2026-07-18T01:00:00Z"),
                    managerJournal,
                    "PENDING",
                    current.reportGenerationAttempts(),
                    ""));
            return session;
        }

        @Override
        public ReportRecord saveReportAndMarkReady(UUID sessionId, ReportMutation reportMutation) {
            if (session.isEmpty() || !session.get().id().equals(sessionId)) {
                throw new IllegalStateException("완료 세션이 없습니다.");
            }
            if (failNextReportWrite) {
                failNextReportWrite = false;
                throw new IllegalStateException("리포트 저장 실패");
            }
            lastReport = reportMutation;
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
            SessionRecord current = session.get();
            session = Optional.of(copyCompletion(
                    current,
                    current.currentStepOrder(),
                    current.currentStatus(),
                    current.version(),
                    current.careEndedAt(),
                    current.completedAt(),
                    current.managerJournal(),
                    "READY",
                    current.reportGenerationAttempts() + 1,
                    ""));
            return report;
        }

        @Override
        public void markReportGenerationFailed(UUID sessionId, String errorMessage) {
            if (session.isEmpty() || !session.get().id().equals(sessionId)) {
                return;
            }
            SessionRecord current = session.get();
            session = Optional.of(copyCompletion(
                    current,
                    current.currentStepOrder(),
                    current.currentStatus(),
                    current.version(),
                    current.careEndedAt(),
                    current.completedAt(),
                    current.managerJournal(),
                    "FAILED",
                    current.reportGenerationAttempts() + 1,
                    errorMessage));
        }

        @Override
        public Optional<CompletionRecord> completeWithReport(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId,
                ReportMutation reportMutation) {
            if (session.isEmpty()) {
                return Optional.empty();
            }
            SessionRecord current = session.get();
            if (!current.id().equals(sessionId)
                    || !current.managerUserId().equals(managerUserId)
                    || !current.appointmentRequestId().equals(appointmentRequestId)
                    || current.version() != expectedVersion
                    || current.currentStepOrder() != current.totalStepCount()) {
                return Optional.empty();
            }
            lastReport = reportMutation;
            session = Optional.of(copy(
                    current,
                    current.currentStepOrder(),
                    "COMPLETED",
                    current.guardianUpdate(),
                    current.preConsultationConfirmed(),
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
                boolean preConsultationConfirmed,
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
                    preConsultationConfirmed,
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
                    current.canceledAt(),
                    current.careEndedAt(),
                    current.managerJournal(),
                    current.reportGenerationStatus(),
                    current.reportGenerationAttempts(),
                    current.reportGenerationLastError(),
                    current.reportGenerationUpdatedAt(),
                    current.artifacts());
        }

        private SessionRecord copyCompletion(
                SessionRecord current,
                int step,
                String status,
                long version,
                Instant careEndedAt,
                Instant completedAt,
                String managerJournal,
                String reportStatus,
                int reportAttempts,
                String reportError) {
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
                    current.guardianUpdate(),
                    current.locationSummary(),
                    current.fieldPhotoNote(),
                    current.medicationNote(),
                    current.pharmacySummary(),
                    current.preConsultationConfirmed(),
                    current.prescriptionCollected(),
                    current.pharmacyCompleted(),
                    current.medicationGuidanceCompleted(),
                    false,
                    null,
                    current.locationAlertStage(),
                    current.locationAlertSentAt(),
                    version,
                    current.startedAt(),
                    completedAt,
                    current.canceledAt(),
                    careEndedAt,
                    managerJournal,
                    reportStatus,
                    reportAttempts,
                    reportError,
                    Instant.parse("2026-07-18T01:00:00Z"),
                    current.artifacts());
        }
    }

    private static final class RecordingConsentAccess implements GuardianSharingConsentAccess {
        private UUID finalizedAppointmentId;
        private Instant careEndedAt;

        @Override
        public boolean isAllowed(
                AppUserRepository.AppUser appUser,
                UUID appointmentRequestId,
                UUID patientUserId,
                UUID guardianUserId,
                InformationScope scope) {
            return true;
        }

        @Override
        public void finalizeExpiryAfterCareBoundary(
                UUID appointmentRequestId,
                Instant careEndedAt) {
            this.finalizedAppointmentId = appointmentRequestId;
            this.careEndedAt = careEndedAt;
        }
    }
}
