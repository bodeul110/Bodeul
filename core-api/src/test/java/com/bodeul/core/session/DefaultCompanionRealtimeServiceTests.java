package com.bodeul.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompanionRealtimeServiceTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID PATIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");
    private static final UUID GUARDIAN_ID = UUID.fromString("6b82d10f-8f20-4a77-b9b4-055a346b689d");
    private static final UUID MANAGER_ID = UUID.fromString("fdb39fea-f2da-408e-bf46-77dbf2265a73");
    private static final UUID MESSAGE_ID = UUID.fromString("318a7261-bbb4-40db-aa91-4a92edbd3da3");

    private FakeSessionRepository sessionRepository;
    private FakeRealtimeRepository realtimeRepository;
    private DefaultCompanionRealtimeService service;
    private List<Object> events;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        realtimeRepository = new FakeRealtimeRepository();
        events = new ArrayList<>();
        sessionRepository.session = Optional.of(session("IN_TREATMENT"));
        service = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add);
    }

    @Test
    void participantReadsChronologicalSnapshotAndPrivateTopic() {
        realtimeRepository.messages = List.of(message("안녕하세요"));
        realtimeRepository.locations = List.of(location());

        var snapshot = service.getSnapshot(patient(), SESSION_ID);

        assertThat(snapshot.realtimeTopic()).isEqualTo("companion-session:" + SESSION_ID);
        assertThat(snapshot.messages()).singleElement().extracting("body").isEqualTo("안녕하세요");
        assertThat(snapshot.locations()).hasSize(1);
    }

    @Test
    void terminalSessionDoesNotExposeStoredCoordinates() {
        sessionRepository.session = Optional.of(session("COMPLETED"));
        realtimeRepository.locations = List.of(location());

        var snapshot = service.getSnapshot(patient(), SESSION_ID);

        assertThat(snapshot.locations()).isEmpty();
    }

    @Test
    void unrelatedParticipantCannotReadSnapshot() {
        var unrelatedPatient = user(UUID.randomUUID(), AppUserRole.PATIENT);

        assertThatThrownBy(() -> service.getSnapshot(unrelatedPatient, SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void participantPostsMessageWithValidatedStorageMetadata() {
        UUID clientMessageId = UUID.randomUUID();
        var command = new CompanionRealtimeService.PostMessageCommand(
                clientMessageId,
                "  검사실로 이동합니다.  ",
                List.of(new CompanionRealtimeService.AttachmentCommand(
                        "companion-chat-attachments/" + SESSION_ID + "/photo.jpg",
                        "photo.jpg",
                        "IMAGE/JPEG",
                        1_024L)));

        var result = service.postMessage(patient(), SESSION_ID, command);

        assertThat(result.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(realtimeRepository.lastMessage.body()).isEqualTo("검사실로 이동합니다.");
        assertThat(realtimeRepository.lastMessage.attachments())
                .singleElement()
                .extracting("contentType")
                .isEqualTo("image/jpeg");
        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(CompanionChatMessageCreatedEvent.class, event -> {
                    assertThat(event.sessionId()).isEqualTo(SESSION_ID);
                    assertThat(event.recipientUserIds()).containsExactly(GUARDIAN_ID, MANAGER_ID);
                });
    }

    @Test
    void attachmentOutsideSessionPrefixIsRejected() {
        var command = new CompanionRealtimeService.PostMessageCommand(
                UUID.randomUUID(),
                "첨부",
                List.of(new CompanionRealtimeService.AttachmentCommand(
                        "companion-chat-attachments/another-session/photo.jpg",
                        "photo.jpg",
                        "image/jpeg",
                        1_024L)));

        assertThatThrownBy(() -> service.postMessage(patient(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");
    }

    @Test
    void reusedClientMessageIdWithDifferentPayloadIsRejected() {
        realtimeRepository.forceDifferentPayload = true;
        var command = new CompanionRealtimeService.PostMessageCommand(
                UUID.randomUUID(),
                "원래 내용",
                List.of());

        assertThatThrownBy(() -> service.postMessage(patient(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_message_idempotency_conflict");
    }

    @Test
    void patientCannotPublishManagerLocation() {
        var command = locationCommand();

        assertThatThrownBy(() -> service.postLocation(patient(), SESSION_ID, command))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_manager_required");
    }

    @Test
    void assignedManagerPublishesCurrentLocation() {
        var result = service.postLocation(manager(), SESSION_ID, locationCommand());

        assertThat(result.managerUserId()).isEqualTo(MANAGER_ID);
        assertThat(realtimeRepository.lastLocation.sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void missingReadMessageIsReportedAsNotFound() {
        realtimeRepository.receipt = Optional.empty();

        assertThatThrownBy(() -> service.updateReadReceipt(patient(), SESSION_ID, UUID.randomUUID()))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_chat_message_not_found");
    }

    private CompanionRealtimeService.PostLocationCommand locationCommand() {
        return new CompanionRealtimeService.PostLocationCommand(
                UUID.randomUUID(),
                37.5665,
                126.9780,
                Instant.now().toString());
    }

    private AppUserRepository.AppUser patient() {
        return user(PATIENT_ID, AppUserRole.PATIENT);
    }

    private AppUserRepository.AppUser manager() {
        return user(MANAGER_ID, AppUserRole.MANAGER);
    }

    private AppUserRepository.AppUser user(UUID id, AppUserRole role) {
        return new AppUserRepository.AppUser(id, "firebase-" + id, role);
    }

    private CompanionSessionRepository.SessionRecord session(String status) {
        return new CompanionSessionRepository.SessionRecord(
                SESSION_ID,
                "legacy-session",
                APPOINTMENT_ID,
                MANAGER_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                2,
                5,
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
                3,
                Instant.parse("2026-07-18T00:00:00Z"),
                status.equals("COMPLETED") ? Instant.parse("2026-07-18T01:00:00Z") : null,
                null);
    }

    private CompanionRealtimeRepository.ChatMessageRecord message(String body) {
        return new CompanionRealtimeRepository.ChatMessageRecord(
                MESSAGE_ID,
                SESSION_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "PATIENT",
                body,
                Instant.parse("2026-07-18T00:10:00Z"),
                List.of());
    }

    private CompanionRealtimeRepository.LocationRecord location() {
        return new CompanionRealtimeRepository.LocationRecord(
                UUID.randomUUID(),
                SESSION_ID,
                UUID.randomUUID(),
                MANAGER_ID,
                37.5665,
                126.9780,
                Instant.parse("2026-07-18T00:10:00Z"));
    }

    private final class FakeSessionRepository implements CompanionSessionRepository {
        private Optional<SessionRecord> session = Optional.empty();

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
            return Optional.empty();
        }

        @Override
        public Optional<SessionRecord> updateDetails(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                SessionPatch patch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SessionRecord> advance(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CompletionRecord> completeWithReport(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId,
                ReportMutation report) {
            throw new UnsupportedOperationException();
        }
    }

    private final class FakeRealtimeRepository implements CompanionRealtimeRepository {
        private List<ChatMessageRecord> messages = List.of();
        private List<ReadReceiptRecord> receipts = List.of();
        private List<LocationRecord> locations = List.of();
        private Optional<ReadReceiptRecord> receipt = Optional.of(new ReadReceiptRecord(
                SESSION_ID,
                PATIENT_ID,
                MESSAGE_ID,
                Instant.parse("2026-07-18T00:11:00Z")));
        private MessageMutation lastMessage;
        private LocationMutation lastLocation;
        private boolean forceDifferentPayload;

        @Override
        public List<ChatMessageRecord> findRecentMessages(UUID sessionId, int limit) {
            return messages;
        }

        @Override
        public List<ReadReceiptRecord> findReadReceipts(UUID sessionId) {
            return receipts;
        }

        @Override
        public List<LocationRecord> findRecentLocations(UUID sessionId, int limit) {
            return locations;
        }

        @Override
        public MessageSaveResult saveMessage(MessageMutation mutation) {
            lastMessage = mutation;
            List<AttachmentRecord> attachments = mutation.attachments().stream()
                    .map(attachment -> new AttachmentRecord(
                            UUID.randomUUID(),
                            MESSAGE_ID,
                            attachment.storagePath(),
                            attachment.fileName(),
                            attachment.contentType(),
                            attachment.sizeBytes()))
                    .toList();
            return new MessageSaveResult(new ChatMessageRecord(
                    MESSAGE_ID,
                    mutation.sessionId(),
                    mutation.clientMessageId(),
                    mutation.senderUserId(),
                    mutation.senderRole(),
                    forceDifferentPayload ? "다른 내용" : mutation.body(),
                    Instant.parse("2026-07-18T00:10:00Z"),
                    attachments),
                    !forceDifferentPayload);
        }

        @Override
        public Optional<ReadReceiptRecord> upsertReadReceipt(
                UUID sessionId,
                UUID userId,
                UUID lastReadMessageId) {
            return receipt;
        }

        @Override
        public Optional<LocationRecord> saveLocation(LocationMutation mutation) {
            lastLocation = mutation;
            return Optional.of(new LocationRecord(
                    UUID.randomUUID(),
                    mutation.sessionId(),
                    mutation.clientLocationId(),
                    mutation.managerUserId(),
                    mutation.latitude(),
                    mutation.longitude(),
                    mutation.capturedAt()));
        }
    }
}
