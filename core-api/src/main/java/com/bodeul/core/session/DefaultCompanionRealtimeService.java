package com.bodeul.core.session;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.session.CompanionRealtimeRepository.AttachmentMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.AttachmentRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.ChatMessageRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.LocationMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.LocationRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.MessageMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.ReadReceiptRecord;
import com.bodeul.core.session.CompanionSessionRepository.SessionRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultCompanionRealtimeService implements CompanionRealtimeService {

    private static final int MESSAGE_LIMIT = 100;
    private static final int LOCATION_LIMIT = 10;
    private static final int MAX_ATTACHMENTS = 3;
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELED");
    private static final Set<String> ATTACHMENT_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    private final CompanionSessionRepository sessionRepository;
    private final CompanionRealtimeRepository realtimeRepository;
    private final ApplicationEventPublisher eventPublisher;

    DefaultCompanionRealtimeService(
            CompanionSessionRepository sessionRepository,
            CompanionRealtimeRepository realtimeRepository,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.realtimeRepository = realtimeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public RealtimeSnapshotView getSnapshot(
            AppUserRepository.AppUser appUser,
            UUID sessionId) {
        SessionRecord session = requireReadableSession(appUser, sessionId);
        List<LocationView> locations = isTerminal(session)
                ? List.of()
                : realtimeRepository.findRecentLocations(sessionId, LOCATION_LIMIT)
                        .stream()
                        .map(this::toView)
                        .toList();
        return new RealtimeSnapshotView(
                realtimeTopic(sessionId),
                realtimeRepository.findRecentMessages(sessionId, MESSAGE_LIMIT)
                        .stream()
                        .map(this::toView)
                        .toList(),
                realtimeRepository.findReadReceipts(sessionId)
                        .stream()
                        .map(receipt -> toView(receipt, participantRole(receipt.userId(), session)))
                        .toList(),
                locations);
    }

    @Override
    @Transactional
    public ChatMessageView postMessage(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            PostMessageCommand command) {
        SessionRecord session = requireReadableSession(appUser, sessionId);
        requireActive(session);
        MessageMutation mutation = normalizeMessage(appUser, session, command);
        CompanionRealtimeRepository.MessageSaveResult saved = realtimeRepository.saveMessage(mutation);
        if (!messageMatches(saved.message(), mutation)) {
            throw CompanionSessionException.idempotencyConflict();
        }
        if (saved.created()) {
            eventPublisher.publishEvent(new CompanionChatMessageCreatedEvent(
                    session.id(),
                    session.appointmentRequestId(),
                    saved.message().senderRole(),
                    saved.message().sentAt(),
                    recipientUserIds(session, saved.message().senderUserId())));
        }
        return toView(saved.message());
    }

    @Override
    @Transactional
    public ReadReceiptView updateReadReceipt(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID lastReadMessageId) {
        requireReadableSession(appUser, sessionId);
        if (lastReadMessageId == null) {
            throw CompanionSessionException.invalidRequest("마지막으로 읽은 메시지 ID가 필요합니다.");
        }
        return realtimeRepository.upsertReadReceipt(sessionId, appUser.id(), lastReadMessageId)
                .map(receipt -> toView(receipt, appUser.role().name()))
                .orElseThrow(CompanionSessionException::chatMessageNotFound);
    }

    @Override
    @Transactional
    public LocationView postLocation(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            PostLocationCommand command) {
        requireManager(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session);
        requireActive(session);
        LocationMutation mutation = normalizeLocation(appUser.id(), sessionId, command);
        return realtimeRepository.saveLocation(mutation)
                .map(this::toView)
                .orElseThrow(CompanionSessionException::stateConflict);
    }

    private MessageMutation normalizeMessage(
            AppUserRepository.AppUser appUser,
            SessionRecord session,
            PostMessageCommand command) {
        if (command == null || command.clientMessageId() == null) {
            throw CompanionSessionException.invalidRequest("메시지 재시도 식별자가 필요합니다.");
        }
        String body = normalizeText(command.body(), 2_000, "메시지");
        List<AttachmentCommand> commands = command.attachments() == null
                ? List.of()
                : command.attachments();
        if (commands.size() > MAX_ATTACHMENTS) {
            throw CompanionSessionException.invalidRequest("첨부 파일은 메시지당 3개까지 등록할 수 있습니다.");
        }
        List<AttachmentMutation> attachments = commands.stream()
                .map(commandItem -> normalizeAttachment(session, commandItem))
                .toList();
        if (body.isBlank() && attachments.isEmpty()) {
            throw CompanionSessionException.invalidRequest("메시지 또는 첨부 파일이 필요합니다.");
        }
        long uniquePaths = attachments.stream().map(AttachmentMutation::storagePath).distinct().count();
        if (uniquePaths != attachments.size()) {
            throw CompanionSessionException.invalidRequest("같은 첨부 파일 경로를 중복 등록할 수 없습니다.");
        }
        return new MessageMutation(
                session.id(),
                command.clientMessageId(),
                appUser.id(),
                appUser.role().name(),
                body,
                attachments);
    }

    private AttachmentMutation normalizeAttachment(SessionRecord session, AttachmentCommand command) {
        if (command == null) {
            throw CompanionSessionException.invalidRequest("첨부 파일 정보를 확인해 주세요.");
        }
        String storagePath = normalizeRequired(command.storagePath(), 1_024, "첨부 파일 경로");
        String corePrefix = "companion-chat-attachments/" + session.id() + "/";
        String legacyPrefix = session.firestoreId() == null || session.firestoreId().isBlank()
                ? ""
                : "companion-chat-attachments/" + session.firestoreId() + "/";
        String suffix = storagePath.startsWith(corePrefix)
                ? storagePath.substring(corePrefix.length())
                : (!legacyPrefix.isEmpty() && storagePath.startsWith(legacyPrefix)
                        ? storagePath.substring(legacyPrefix.length())
                        : "");
        boolean invalidSegment = suffix.isBlank()
                || storagePath.indexOf('\\') >= 0
                || storagePath.contains("//")
                || storagePath.chars().anyMatch(Character::isISOControl)
                || List.of(suffix.split("/", -1)).stream()
                        .anyMatch(segment -> segment.isBlank()
                                || segment.equals(".")
                                || segment.equals(".."));
        if (invalidSegment) {
            throw CompanionSessionException.invalidRequest("첨부 파일 경로가 세션 경계와 맞지 않습니다.");
        }
        String fileName = normalizeRequired(command.fileName(), 255, "첨부 파일 이름");
        String contentType = normalizeRequired(command.contentType(), 100, "첨부 파일 형식")
                .toLowerCase(Locale.ROOT);
        if (!ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw CompanionSessionException.invalidRequest("JPEG, PNG 또는 PDF 파일만 첨부할 수 있습니다.");
        }
        if (command.sizeBytes() == null
                || command.sizeBytes() <= 0
                || command.sizeBytes() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw CompanionSessionException.invalidRequest("첨부 파일 크기는 10MiB 이하여야 합니다.");
        }
        return new AttachmentMutation(
                storagePath,
                fileName,
                contentType,
                command.sizeBytes());
    }

    private LocationMutation normalizeLocation(
            UUID managerUserId,
            UUID sessionId,
            PostLocationCommand command) {
        if (command == null || command.clientLocationId() == null) {
            throw CompanionSessionException.invalidRequest("위치 재시도 식별자가 필요합니다.");
        }
        if (command.latitude() == null
                || !Double.isFinite(command.latitude())
                || command.latitude() < -90.0
                || command.latitude() > 90.0) {
            throw CompanionSessionException.invalidRequest("위도를 확인해 주세요.");
        }
        if (command.longitude() == null
                || !Double.isFinite(command.longitude())
                || command.longitude() < -180.0
                || command.longitude() > 180.0) {
            throw CompanionSessionException.invalidRequest("경도를 확인해 주세요.");
        }
        Instant capturedAt;
        try {
            capturedAt = Instant.parse(command.capturedAt());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw CompanionSessionException.invalidRequest("위치 수집 시각을 ISO-8601 형식으로 입력해 주세요.");
        }
        Instant now = Instant.now();
        if (capturedAt.isBefore(now.minusSeconds(15 * 60L))
                || capturedAt.isAfter(now.plusSeconds(5 * 60L))) {
            throw CompanionSessionException.invalidRequest("위치 수집 시각이 허용 범위를 벗어났습니다.");
        }
        return new LocationMutation(
                sessionId,
                command.clientLocationId(),
                managerUserId,
                command.latitude(),
                command.longitude(),
                capturedAt);
    }

    private SessionRecord requireReadableSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId) {
        requireReadableRole(appUser);
        SessionRecord session = findSession(sessionId);
        requireReader(appUser, session);
        return session;
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

    private void requireActive(SessionRecord session) {
        if (isTerminal(session)) {
            throw CompanionSessionException.stateConflict();
        }
    }

    private boolean isTerminal(SessionRecord session) {
        return TERMINAL_STATUSES.contains(session.currentStatus());
    }

    private String normalizeText(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw CompanionSessionException.invalidRequest(
                    label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = normalizeText(value, maxLength, label);
        if (normalized.isBlank()) {
            throw CompanionSessionException.invalidRequest(label + "이(가) 필요합니다.");
        }
        return normalized;
    }

    private boolean messageMatches(ChatMessageRecord saved, MessageMutation requested) {
        if (!saved.sessionId().equals(requested.sessionId())
                || !saved.clientMessageId().equals(requested.clientMessageId())
                || !saved.senderUserId().equals(requested.senderUserId())
                || !saved.senderRole().equals(requested.senderRole())
                || !saved.body().equals(requested.body())) {
            return false;
        }
        List<AttachmentFingerprint> savedAttachments = saved.attachments().stream()
                .map(AttachmentFingerprint::from)
                .sorted(Comparator.comparing(AttachmentFingerprint::storagePath))
                .toList();
        List<AttachmentFingerprint> requestedAttachments = requested.attachments().stream()
                .map(AttachmentFingerprint::from)
                .sorted(Comparator.comparing(AttachmentFingerprint::storagePath))
                .toList();
        return savedAttachments.equals(requestedAttachments);
    }

    private ChatMessageView toView(ChatMessageRecord message) {
        return new ChatMessageView(
                message.id(),
                message.clientMessageId(),
                message.senderUserId(),
                message.senderRole(),
                message.body(),
                format(message.sentAt()),
                message.attachments().stream().map(this::toView).toList());
    }

    private AttachmentView toView(AttachmentRecord attachment) {
        return new AttachmentView(
                attachment.id(),
                attachment.storagePath(),
                attachment.fileName(),
                attachment.contentType(),
                attachment.sizeBytes());
    }

    private ReadReceiptView toView(ReadReceiptRecord receipt, String userRole) {
        return new ReadReceiptView(
                receipt.userId(),
                userRole,
                receipt.lastReadMessageId(),
                format(receipt.lastReadAt()));
    }

    private String participantRole(UUID userId, SessionRecord session) {
        if (userId.equals(session.patientUserId())) {
            return AppUserRole.PATIENT.name();
        }
        if (userId.equals(session.guardianUserId())) {
            return AppUserRole.GUARDIAN.name();
        }
        if (userId.equals(session.managerUserId())) {
            return AppUserRole.MANAGER.name();
        }
        throw CompanionSessionException.permissionDenied();
    }

    private List<UUID> recipientUserIds(SessionRecord session, UUID senderUserId) {
        return Stream.of(
                        session.patientUserId(),
                        session.guardianUserId(),
                        session.managerUserId())
                .filter(Objects::nonNull)
                .filter(userId -> !userId.equals(senderUserId))
                .distinct()
                .toList();
    }

    private LocationView toView(LocationRecord location) {
        return new LocationView(
                location.id(),
                location.clientLocationId(),
                location.managerUserId(),
                location.latitude(),
                location.longitude(),
                format(location.capturedAt()));
    }

    private String realtimeTopic(UUID sessionId) {
        return "companion-session:" + sessionId;
    }

    private String format(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private record AttachmentFingerprint(
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes) {

        static AttachmentFingerprint from(AttachmentRecord attachment) {
            return new AttachmentFingerprint(
                    attachment.storagePath(),
                    attachment.fileName(),
                    attachment.contentType(),
                    attachment.sizeBytes());
        }

        static AttachmentFingerprint from(AttachmentMutation attachment) {
            return new AttachmentFingerprint(
                    attachment.storagePath(),
                    attachment.fileName(),
                    attachment.contentType(),
                    attachment.sizeBytes());
        }
    }
}
