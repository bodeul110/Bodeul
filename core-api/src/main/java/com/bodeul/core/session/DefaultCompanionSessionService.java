package com.bodeul.core.session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
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
    private static final Set<String> MEDICATION_DECISIONS = Set.of(
            "", "MATCHED", "CHANGED", "RECHECK_REQUIRED");
    private static final Set<String> LOCATION_ALERT_STAGES = Set.of(
            "none", "hospital_near", "pharmacy_near");

    private final CompanionSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    DefaultCompanionSessionService(
            CompanionSessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionView> getMySessions(AppUserRepository.AppUser appUser) {
        requireReadableRole(appUser);
        return sessionRepository.findAllForUser(appUser.id(), appUser.role())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SessionView getSession(AppUserRepository.AppUser appUser, UUID sessionId) {
        requireReadableRole(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session);
        return toView(session);
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

        SessionPatch patch = new SessionPatch(
                normalizeOptional(command.guardianUpdate(), 1_000, "보호자 공유 내용"),
                normalizeOptional(command.locationSummary(), 1_000, "위치 요약"),
                normalizeOptional(command.fieldPhotoNote(), 2_000, "현장 확인 메모"),
                normalizeOptional(command.medicationNote(), 4_000, "복약 메모"),
                normalizeOptional(command.pharmacySummary(), 2_000, "약국 처리 요약"),
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
                    Stream.of(updated.patientUserId(), updated.guardianUserId())
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList()));
        }
        return toView(updated);
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
        if (existing.totalStepCount() <= 0 || existing.currentStepOrder() >= existing.totalStepCount()) {
            throw CompanionSessionException.stateConflict();
        }

        return sessionRepository.advance(
                        sessionId,
                        appUser.id(),
                        version,
                        existing.appointmentRequestId())
                .map(this::toView)
                .orElseThrow(CompanionSessionException::versionConflict);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportView getReport(AppUserRepository.AppUser appUser, UUID sessionId) {
        requireReadableRole(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session);
        return sessionRepository.findReportBySessionId(sessionId)
                .map(this::toView)
                .orElseThrow(CompanionSessionException::reportNotFound);
    }

    @Override
    @Transactional
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
        requireMutable(existing, command.version());

        String summary = normalizeRequired(command.summary(), 2_000, "동행 요약");
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

        return sessionRepository.completeWithReport(
                        sessionId,
                        appUser.id(),
                        command.version(),
                        existing.appointmentRequestId(),
                        report)
                .map(completion -> toView(completion.report()))
                .orElseThrow(CompanionSessionException::versionConflict);
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

    private void requireReader(AppUserRepository.AppUser appUser, SessionRecord session) {
        UUID allowedUserId = switch (appUser.role()) {
            case PATIENT -> session.patientUserId();
            case GUARDIAN -> session.guardianUserId();
            case MANAGER -> session.managerUserId();
            default -> null;
        };
        if (!appUser.id().equals(allowedUserId)) {
            throw CompanionSessionException.permissionDenied();
        }
    }

    private void requireManagerAssignment(
            AppUserRepository.AppUser appUser,
            SessionRecord session) {
        if (!appUser.id().equals(session.managerUserId())) {
            throw CompanionSessionException.permissionDenied();
        }
    }

    private void requireMutable(SessionRecord session, long expectedVersion) {
        if (TERMINAL_STATUSES.contains(session.currentStatus())) {
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

    private SessionView toView(SessionRecord session) {
        return new SessionView(
                session.id(),
                session.firestoreId() == null ? "" : session.firestoreId(),
                session.appointmentRequestId(),
                session.managerUserId(),
                session.currentStepOrder(),
                session.totalStepCount(),
                session.currentStatus(),
                session.guardianUpdate(),
                session.locationSummary(),
                session.fieldPhotoNote(),
                session.medicationNote(),
                session.pharmacySummary(),
                session.prescriptionCollected(),
                session.pharmacyCompleted(),
                session.medicationGuidanceCompleted(),
                session.liveLocationSharingActive(),
                format(session.liveLocationSharingStartedAt()),
                session.locationAlertStage(),
                format(session.locationAlertSentAt()),
                session.version(),
                format(session.startedAt()),
                format(session.completedAt()),
                format(session.canceledAt()));
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
