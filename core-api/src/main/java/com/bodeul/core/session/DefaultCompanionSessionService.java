package com.bodeul.core.session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentAccess;
import com.bodeul.core.session.CompanionSessionRepository.GuideSnapshotRecord;
import com.bodeul.core.session.CompanionSessionRepository.GuideStepRecord;
import com.bodeul.core.session.CompanionSessionRepository.ReportMutation;
import com.bodeul.core.session.CompanionSessionRepository.ReportRecord;
import com.bodeul.core.session.CompanionSessionRepository.SessionPatch;
import com.bodeul.core.session.CompanionSessionRepository.SessionRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultCompanionSessionService implements CompanionSessionService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KOREAN_DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm", Locale.KOREA);
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELED");
    private static final Set<String> DETAIL_MUTATION_BLOCKED_STATUSES = Set.of(
            "CARE_ENDED", "COMPLETED", "CANCELED");
    private static final Pattern STEP_CODE = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");
    private static final String SESSION_TERMINAL = "SESSION_TERMINAL";
    private static final String GUIDE_NOT_READY = "GUIDE_NOT_READY";
    private static final String STEP_CONTRACT_MISMATCH = "STEP_CONTRACT_MISMATCH";
    private static final String STEP_INPUT_REQUIRED = "STEP_INPUT_REQUIRED";
    private static final String LAST_STEP_REACHED = "LAST_STEP_REACHED";
    private static final String CARE_ENDED_PENDING_COMPLETION = "CARE_ENDED_PENDING_COMPLETION";
    private static final String REPORT_RETRY_REQUIRED = "REPORT_RETRY_REQUIRED";
    private static final String PRE_CONSULTATION = "PRE_CONSULTATION";
    private static final String CARE_COMPLETION = "CARE_COMPLETION";
    private static final Set<String> MEDICATION_DECISIONS = Set.of(
            "", "MATCHED", "CHANGED", "RECHECK_REQUIRED");
    private static final Set<String> LOCATION_ALERT_STAGES = Set.of(
            "none", "hospital_near", "pharmacy_near");

    private final CompanionSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GuardianSharingConsentAccess consentAccess;
    private final boolean preConsultationEnforcement;
    private final boolean completionEnforcement;

    DefaultCompanionSessionService(
            CompanionSessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher,
            CompanionSessionProperties properties,
            GuardianSharingConsentAccess consentAccess) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.consentAccess = consentAccess;
        this.preConsultationEnforcement = properties.isPreConsultationEnforcement();
        this.completionEnforcement = properties.isCompletionEnforcement();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionView> getMySessions(AppUserRepository.AppUser appUser) {
        requireReadableRole(appUser);
        return sessionRepository.findAllForUser(appUser.id(), appUser.role())
                .stream()
                .filter(session -> canRead(appUser, session, InformationScope.APPOINTMENT))
                .map(session -> toView(appUser, session))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SessionView getSession(AppUserRepository.AppUser appUser, UUID sessionId) {
        requireReadableRole(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session, InformationScope.APPOINTMENT);
        return toView(appUser, session);
    }

    @Override
    @Transactional
    public SessionView updateSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UpdateSessionCommand command) {
        requireManager(appUser);
        if (command == null || command.version() < 0) {
            throw CompanionSessionException.invalidRequest("동행 세션 버전이 필요합니다.");
        }
        if (isEmptyPatch(command)) {
            throw CompanionSessionException.invalidRequest("변경할 동행 정보가 필요합니다.");
        }

        SessionRecord existing = findSession(sessionId);
        requireManagerAssignment(appUser, existing);
        requireMutable(existing, command.version());
        if (command.preConsultationConfirmed() != null
                && !PRE_CONSULTATION.equals(currentStepCode(existing))) {
            throw CompanionSessionException.invalidRequest(
                    "진료 전 확인 상태는 진료 준비 단계에서만 변경할 수 있습니다.");
        }

        SessionPatch patch = new SessionPatch(
                normalizeOptional(command.guardianUpdate(), 1_000, "보호자 공유 내용"),
                normalizeOptional(command.locationSummary(), 1_000, "위치 요약"),
                normalizeOptional(command.fieldPhotoNote(), 2_000, "현장 확인 메모"),
                normalizeOptional(command.medicationNote(), 4_000, "복약 메모"),
                normalizeOptional(command.pharmacySummary(), 2_000, "약국 처리 요약"),
                command.preConsultationConfirmed(),
                command.prescriptionCollected(),
                command.pharmacyCompleted(),
                command.medicationGuidanceCompleted(),
                command.liveLocationSharingActive(),
                normalizeLocationAlertStage(command.locationAlertStage()));

        SessionRecord updated = sessionRepository
                .updateDetails(sessionId, appUser.id(), command.version(), patch)
                .orElseThrow(CompanionSessionException::versionConflict);
        if (patch.locationAlertStage() != null
                && !Objects.equals(existing.locationAlertStage(), updated.locationAlertStage())) {
            eventPublisher.publishEvent(new CompanionLocationAlertChangedEvent(
                    updated.id(),
                    updated.appointmentRequestId(),
                    updated.locationAlertStage(),
                    Stream.of(
                                    updated.patientUserId(),
                                    guardianHasScope(updated, InformationScope.LOCATION)
                                            ? updated.guardianUserId()
                                            : null)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList()));
        }
        return toView(appUser, updated);
    }

    @Override
    @Transactional
    public SessionView advanceSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            long version) {
        requireManager(appUser);
        if (version < 0) {
            throw CompanionSessionException.invalidRequest("동행 세션 버전이 필요합니다.");
        }

        SessionRecord existing = findSession(sessionId);
        requireManagerAssignment(appUser, existing);
        requireMutable(existing, version);
        if (!progressState(existing).canAdvance()) {
            throw CompanionSessionException.stateConflict();
        }

        return sessionRepository.advance(
                        sessionId,
                        appUser.id(),
                        version,
                        existing.appointmentRequestId())
                .map(session -> toView(appUser, session))
                .orElseThrow(CompanionSessionException::versionConflict);
    }

    @Override
    @Transactional
    public SessionView endCare(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            long version) {
        requireManager(appUser);
        if (version < 0) {
            throw CompanionSessionException.invalidRequest("동행 세션 버전이 필요합니다.");
        }

        SessionRecord existing = findSession(sessionId);
        requireManagerAssignment(appUser, existing);
        if (existing.careEndedAt() != null
                && !"CANCELED".equals(existing.currentStatus())) {
            return toView(appUser, existing);
        }
        requireMutable(existing, version);
        if (!CARE_COMPLETION.equals(currentStepCode(existing))) {
            throw CompanionSessionException.stateConflict();
        }

        return sessionRepository.endCare(
                        sessionId,
                        appUser.id(),
                        version,
                        existing.appointmentRequestId(),
                        completionEnforcement)
                .map(session -> toView(appUser, session))
                .orElseThrow(CompanionSessionException::versionConflict);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportView getReport(AppUserRepository.AppUser appUser, UUID sessionId) {
        requireReadableRole(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session, InformationScope.REPORT);
        return sessionRepository.findReportBySessionId(sessionId)
                .map(this::toView)
                .orElseThrow(CompanionSessionException::reportNotFound);
    }

    @Override
    public ReportView submitReport(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            SubmitReportCommand command) {
        requireManager(appUser);
        if (command == null || command.version() < 0) {
            throw CompanionSessionException.invalidRequest("동행 세션 버전이 필요합니다.");
        }

        SessionRecord existing = findSession(sessionId);
        requireManagerAssignment(appUser, existing);
        boolean retryReport = "COMPLETED".equals(existing.currentStatus())
                && ("FAILED".equals(existing.reportGenerationStatus())
                || "PENDING".equals(existing.reportGenerationStatus()));
        if ("COMPLETED".equals(existing.currentStatus())
                && "READY".equals(existing.reportGenerationStatus())) {
            return sessionRepository.findReportBySessionId(sessionId)
                    .map(this::toView)
                    .orElseThrow(CompanionSessionException::reportNotFound);
        }
        if (!retryReport) {
            if (existing.version() != command.version()) {
                throw CompanionSessionException.versionConflict();
            }
            boolean careEnded = existing.careEndedAt() != null
                    && CARE_ENDED_PENDING_COMPLETION.equals(
                            progressState(existing).blockedReason());
            boolean legacyCompletion = !completionEnforcement
                    && LAST_STEP_REACHED.equals(progressState(existing).blockedReason());
            if (!careEnded && !legacyCompletion) {
                throw CompanionSessionException.stateConflict();
            }
        }

        String summary = normalizeText(command.summary(), 2_000, "동행 요약");
        String managerJournal = normalizeText(command.managerJournal(), 300, "매니저 일지");
        String medicationDecision = normalizeCode(command.medicationComparisonDecisionCode());
        String nextVisitNote = normalizeText(command.nextVisitAt(), 200, "다음 방문 일정");
        ReportMutation report = new ReportMutation(
                summary,
                normalizeText(command.treatmentNotes(), 4_000, "진료 메모"),
                normalizeText(command.medicationNotes(), 4_000, "복약 메모"),
                normalizeText(command.medicationName(), 500, "약 이름"),
                normalizeText(command.medicationChangeSummary(), 2_000, "약 변경 요약"),
                normalizeText(command.medicationScheduleNote(), 2_000, "복약 일정"),
                medicationDecision,
                normalizeText(command.medicationComparisonNote(), 2_000, "복약 비교 메모"),
                parseNextVisitAt(nextVisitNote),
                nextVisitNote);

        if (!retryReport) {
            SessionRecord finalized = sessionRepository.finalizeSession(
                            sessionId,
                            appUser.id(),
                            command.version(),
                            existing.appointmentRequestId(),
                            managerJournal,
                            !completionEnforcement)
                    .orElseThrow(CompanionSessionException::versionConflict);
            consentAccess.finalizeExpiryAfterCareBoundary(
                    existing.appointmentRequestId(),
                    finalized.completedAt());
            if ("READY".equals(finalized.reportGenerationStatus())) {
                return sessionRepository.findReportBySessionId(sessionId)
                        .map(this::toView)
                        .orElseThrow(CompanionSessionException::reportNotFound);
            }
        }
        try {
            return toView(sessionRepository.saveReportAndMarkReady(sessionId, report));
        } catch (RuntimeException exception) {
            try {
                sessionRepository.markReportGenerationFailed(sessionId, "REPORT_WRITE_FAILED");
            } catch (RuntimeException ignored) {
                // 완료 기록은 이미 확정되었으므로 실패 상태 기록 오류로 원래 원인을 덮지 않는다.
            }
            try {
                SessionRecord latest = findSession(sessionId);
                if ("READY".equals(latest.reportGenerationStatus())) {
                    return sessionRepository.findReportBySessionId(sessionId)
                            .map(this::toView)
                            .orElseThrow(CompanionSessionException::reportNotFound);
                }
            } catch (RuntimeException ignored) {
                // 실패 확인 자체가 불가능해도 이미 확정한 세션 완료를 되돌리지 않는다.
            }
            throw CompanionSessionException.reportGenerationFailed();
        }
    }

    private SessionRecord findSession(UUID sessionId) {
        if (sessionId == null) {
            throw CompanionSessionException.invalidRequest("동행 세션 ID가 필요합니다.");
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(CompanionSessionException::notFound);
    }

    private void requireReadableRole(AppUserRepository.AppUser appUser) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT
                && appUser.role() != AppUserRole.GUARDIAN
                && appUser.role() != AppUserRole.MANAGER)) {
            throw CompanionSessionException.roleNotSupported();
        }
    }

    private void requireManager(AppUserRepository.AppUser appUser) {
        if (appUser == null || appUser.role() != AppUserRole.MANAGER) {
            throw CompanionSessionException.managerRequired();
        }
    }

    private void requireReader(
            AppUserRepository.AppUser appUser,
            SessionRecord session,
            InformationScope scope) {
        UUID allowedUserId = switch (appUser.role()) {
            case PATIENT -> session.patientUserId();
            case GUARDIAN -> session.guardianUserId();
            case MANAGER -> session.managerUserId();
            default -> null;
        };
        if (!appUser.id().equals(allowedUserId)) {
            throw CompanionSessionException.permissionDenied();
        }
        if (!canRead(appUser, session, scope)) {
            throw CompanionSessionException.permissionDenied();
        }
    }

    private boolean canRead(
            AppUserRepository.AppUser appUser,
            SessionRecord session,
            InformationScope scope) {
        return appUser.role() != AppUserRole.GUARDIAN
                || consentAccess.isAllowed(
                appUser,
                session.appointmentRequestId(),
                session.patientUserId(),
                session.guardianUserId(),
                scope);
    }

    private boolean guardianHasScope(
            SessionRecord session,
            InformationScope scope) {
        if (session.guardianUserId() == null) {
            return false;
        }
        AppUserRepository.AppUser guardian = new AppUserRepository.AppUser(
                session.guardianUserId(),
                "",
                AppUserRole.GUARDIAN);
        return consentAccess.isAllowed(
                guardian,
                session.appointmentRequestId(),
                session.patientUserId(),
                session.guardianUserId(),
                scope);
    }

    private void requireManagerAssignment(
            AppUserRepository.AppUser appUser,
            SessionRecord session) {
        if (!appUser.id().equals(session.managerUserId())) {
            throw CompanionSessionException.permissionDenied();
        }
    }

    private void requireMutable(SessionRecord session, long expectedVersion) {
        if (DETAIL_MUTATION_BLOCKED_STATUSES.contains(session.currentStatus())
                || session.careEndedAt() != null) {
            throw CompanionSessionException.stateConflict();
        }
        if (session.version() != expectedVersion) {
            throw CompanionSessionException.versionConflict();
        }
    }

    private boolean isEmptyPatch(UpdateSessionCommand command) {
        return command.guardianUpdate() == null
                && command.locationSummary() == null
                && command.fieldPhotoNote() == null
                && command.medicationNote() == null
                && command.pharmacySummary() == null
                && command.preConsultationConfirmed() == null
                && command.prescriptionCollected() == null
                && command.pharmacyCompleted() == null
                && command.medicationGuidanceCompleted() == null
                && command.liveLocationSharingActive() == null
                && command.locationAlertStage() == null;
    }

    private String normalizeLocationAlertStage(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!LOCATION_ALERT_STAGES.contains(normalized)) {
            throw CompanionSessionException.invalidRequest("위치 알림 단계를 확인해 주세요.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String label) {
        return value == null ? null : normalizeText(value, maxLength, label);
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = normalizeText(value, maxLength, label);
        if (normalized.isBlank()) {
            throw CompanionSessionException.invalidRequest(label + "이(가) 필요합니다.");
        }
        return normalized;
    }

    private String normalizeText(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw CompanionSessionException.invalidRequest(
                    label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MEDICATION_DECISIONS.contains(normalized)) {
            throw CompanionSessionException.invalidRequest("복약 비교 결과 값을 확인해 주세요.");
        }
        return normalized;
    }

    private Instant parseNextVisitAt(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 기존 앱의 날짜 또는 자유 텍스트를 보존하면서 파싱 가능한 값만 별도 시각으로 저장한다.
        }
        try {
            return LocalDate.parse(value).atStartOfDay(SEOUL).toInstant();
        } catch (DateTimeParseException ignored) {
            // 날짜와 시간이 함께 온 경우를 다음 형식에서 확인한다.
        }
        try {
            return LocalDateTime.parse(value, KOREAN_DATE_TIME).atZone(SEOUL).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private SessionView toView(
            AppUserRepository.AppUser appUser,
            SessionRecord session) {
        GuideSnapshotRecord snapshot = session.guideSnapshot();
        List<CompanionSessionService.GuideStepView> steps = snapshot == null
                ? List.of()
                : snapshot.steps().stream()
                        .map(step -> new CompanionSessionService.GuideStepView(
                                step.code(),
                                step.order(),
                                step.title(),
                                step.description()))
                        .toList();
        ProgressState progress = progressState(session);
        Set<InformationScope> allowedScopes = appUser.role() == AppUserRole.GUARDIAN
                ? consentAccess.allowedScopes(
                appUser,
                session.appointmentRequestId(),
                session.patientUserId(),
                session.guardianUserId())
                : Set.of(InformationScope.values());
        boolean chatAllowed = allowedScopes.contains(InformationScope.CHAT);
        boolean locationAllowed = allowedScopes.contains(InformationScope.LOCATION);
        boolean attachmentAllowed = allowedScopes.contains(InformationScope.ATTACHMENT);
        boolean reportAllowed = allowedScopes.contains(InformationScope.REPORT);
        String exposedStatus = !completionEnforcement
                && "CARE_ENDED".equals(session.currentStatus())
                ? "PAYMENT"
                : session.currentStatus();
        return new SessionView(
                session.id(),
                session.firestoreId() == null ? "" : session.firestoreId(),
                session.appointmentRequestId(),
                session.managerUserId(),
                session.currentStepOrder(),
                session.totalStepCount(),
                exposedStatus,
                chatAllowed ? session.guardianUpdate() : "",
                locationAllowed ? session.locationSummary() : "",
                attachmentAllowed ? session.fieldPhotoNote() : "",
                reportAllowed ? session.medicationNote() : "",
                reportAllowed ? session.pharmacySummary() : "",
                reportAllowed && session.preConsultationConfirmed(),
                reportAllowed && session.prescriptionCollected(),
                reportAllowed && session.pharmacyCompleted(),
                reportAllowed && session.medicationGuidanceCompleted(),
                locationAllowed && session.liveLocationSharingActive(),
                locationAllowed ? format(session.liveLocationSharingStartedAt()) : "",
                locationAllowed ? session.locationAlertStage() : "",
                locationAllowed ? format(session.locationAlertSentAt()) : "",
                session.version(),
                format(session.startedAt()),
                format(session.completedAt()),
                format(session.canceledAt()),
                snapshot == null ? null : snapshot.guideId(),
                snapshot == null ? null : snapshot.guideRevision(),
                steps,
                progress.currentStepCode(),
                progress.canAdvance(),
                progress.blockedReason(),
                format(session.careEndedAt()),
                reportAllowed ? session.managerJournal() : "",
                reportAllowed ? session.reportGenerationStatus() : "",
                reportAllowed ? session.reportGenerationAttempts() : 0,
                reportAllowed ? session.reportGenerationLastError() : "",
                reportAllowed ? format(session.reportGenerationUpdatedAt()) : "",
                (attachmentAllowed ? session.artifacts() : List.<CompanionSessionRepository.ArtifactRecord>of()).stream()
                        .map(artifact -> new ArtifactView(
                                artifact.id(),
                                artifact.purpose(),
                                artifact.fileName(),
                                artifact.contentType(),
                                artifact.sizeBytes(),
                                format(artifact.createdAt())))
                        .toList());
    }

    private ProgressState progressState(SessionRecord session) {
        String contractBlock = guideContractBlock(session);
        String currentStepCode = contractBlock == null ? currentStepCode(session) : null;

        if (session.careEndedAt() != null
                && !"COMPLETED".equals(session.currentStatus())
                && !"CANCELED".equals(session.currentStatus())) {
            return new ProgressState(currentStepCode, false, CARE_ENDED_PENDING_COMPLETION);
        }
        if ("COMPLETED".equals(session.currentStatus())
                && !"READY".equals(session.reportGenerationStatus())) {
            return new ProgressState(currentStepCode, false, REPORT_RETRY_REQUIRED);
        }
        if (TERMINAL_STATUSES.contains(session.currentStatus())) {
            return new ProgressState(currentStepCode, false, SESSION_TERMINAL);
        }
        if (contractBlock != null) {
            return new ProgressState(null, false, contractBlock);
        }
        if (preConsultationEnforcement
                && PRE_CONSULTATION.equals(currentStepCode)
                && !session.preConsultationConfirmed()) {
            return new ProgressState(currentStepCode, false, STEP_INPUT_REQUIRED);
        }
        if (session.currentStepOrder() == session.totalStepCount()) {
            return new ProgressState(currentStepCode, false, LAST_STEP_REACHED);
        }
        return new ProgressState(currentStepCode, true, null);
    }

    private String currentStepCode(SessionRecord session) {
        if (guideContractBlock(session) != null || session.currentStepOrder() <= 0) {
            return null;
        }
        return session.guideSnapshot().steps().get(session.currentStepOrder() - 1).code();
    }

    private String guideContractBlock(SessionRecord session) {
        GuideSnapshotRecord snapshot = session.guideSnapshot();
        if (snapshot == null
                || !snapshot.present()
                || snapshot.steps().isEmpty()
                || "GUIDE_NOT_FOUND".equals(snapshot.source())
                || "UNRESOLVED_LEGACY".equals(snapshot.source())) {
            return GUIDE_NOT_READY;
        }

        boolean supportedSource = "LEGACY_CORE_7_V1".equals(snapshot.source())
                || ("HOSPITAL_GUIDE_STEP_CODE_V1".equals(snapshot.source())
                && Integer.valueOf(1).equals(snapshot.stepContractVersion())
                && snapshot.guideId() != null
                && snapshot.guideRevision() != null
                && snapshot.guideRevision() > 0);
        if (!supportedSource || session.totalStepCount() != snapshot.steps().size()) {
            return STEP_CONTRACT_MISMATCH;
        }

        Set<String> codes = new HashSet<>();
        for (int index = 0; index < snapshot.steps().size(); index++) {
            GuideStepRecord step = snapshot.steps().get(index);
            if (step == null
                    || step.order() != index + 1
                    || step.code() == null
                    || !STEP_CODE.matcher(step.code()).matches()
                    || !codes.add(step.code())
                    || step.title() == null
                    || step.title().isBlank()
                    || step.description() == null) {
                return STEP_CONTRACT_MISMATCH;
            }
        }

        if (session.currentStepOrder() < 0
                || session.currentStepOrder() > snapshot.steps().size()) {
            return STEP_CONTRACT_MISMATCH;
        }
        return null;
    }

    private record ProgressState(
            String currentStepCode,
            boolean canAdvance,
            String blockedReason) {
    }

    private ReportView toView(ReportRecord report) {
        String nextVisit = report.nextVisitNote().isBlank()
                ? format(report.nextVisitAt())
                : report.nextVisitNote();
        return new ReportView(
                report.id(),
                report.firestoreId() == null ? "" : report.firestoreId(),
                report.companionSessionId(),
                report.summary(),
                report.treatmentNotes(),
                report.medicationNotes(),
                report.medicationName(),
                report.medicationChangeSummary(),
                report.medicationScheduleNote(),
                report.medicationComparisonDecisionCode(),
                report.medicationComparisonNote(),
                nextVisit,
                report.version());
    }

    private String format(Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}
