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
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompanionRealtimeServiceTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID PATIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");
    private static final UUID GUARDIAN_ID = UUID.fromString("6b82d10f-8f20-4a77-b9b4-055a346b689d");
    private static final UUID MANAGER_ID = UUID.fromString("fdb39fea-f2da-408e-bf46-77dbf2265a73");
    private static final UUID MESSAGE_ID = UUID.fromString("318a7261-bbb4-40db-aa91-4a92edbd3da3");
    private static final UUID ATTACHMENT_ID = UUID.fromString("cfc32ab6-66e8-4fd3-bf0e-4a15ea5d3d78");

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
                events::add,
                properties(true),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true);
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
    void careEndedPatientKeepsRetainedChatButCannotSeeLocation() {
        sessionRepository.session = Optional.of(session("CARE_ENDED"));
        realtimeRepository.messages = List.of(message("보관 중인 메시지"));
        realtimeRepository.locations = List.of(location());
        realtimeRepository.attachment = Optional.of(attachment());

        var snapshot = service.getSnapshot(patient(), SESSION_ID);
        var guardianSnapshot = service.getSnapshot(guardian(), SESSION_ID);

        assertThat(snapshot.messages()).singleElement()
                .extracting("body")
                .isEqualTo("보관 중인 메시지");
        assertThat(snapshot.locations()).isEmpty();
        assertThat(guardianSnapshot.messages()).hasSize(1);
        assertThat(guardianSnapshot.locations()).isEmpty();
        assertThat(service.getAttachment(patient(), SESSION_ID, ATTACHMENT_ID).id())
                .isEqualTo(ATTACHMENT_ID);
        assertThat(service.getAttachment(guardian(), SESSION_ID, ATTACHMENT_ID).id())
                .isEqualTo(ATTACHMENT_ID);
    }

    @Test
    void careEndedManagerCannotReadRetainedRealtimeData() {
        sessionRepository.session = Optional.of(session("CARE_ENDED"));
        realtimeRepository.messages = List.of(message("환자와 보호자에게만 보이는 메시지"));
        realtimeRepository.attachment = Optional.of(attachment());

        assertThatThrownBy(() -> service.getSnapshot(manager(), SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
        assertThatThrownBy(() -> service.getAttachment(manager(), SESSION_ID, ATTACHMENT_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void careEndedSessionRejectsNewChatAttachmentAndLocationWrites() {
        sessionRepository.session = Optional.of(session("CARE_ENDED"));
        var message = new CompanionRealtimeService.PostMessageCommand(
                UUID.randomUUID(),
                "종료 후 메시지",
                List.of());

        assertThatThrownBy(() -> service.postMessage(patient(), SESSION_ID, message))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThatThrownBy(() -> service.validateAttachmentWrite(patient(), SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThatThrownBy(() -> service.postLocation(manager(), SESSION_ID, locationCommand()))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
    }

    @Test
    void careEndedTimestampClosesRealtimeWhileLegacyStatusIsStillActive() {
        sessionRepository.session = Optional.of(withCareEndedAt(session("IN_TREATMENT")));
        realtimeRepository.locations = List.of(location());
        var message = new CompanionRealtimeService.PostMessageCommand(
                UUID.randomUUID(),
                "호환 모드 종료 후 메시지",
                List.of());

        assertThat(service.getSnapshot(patient(), SESSION_ID).locations()).isEmpty();
        assertThatThrownBy(() -> service.postMessage(patient(), SESSION_ID, message))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
        assertThatThrownBy(() -> service.getSnapshot(manager(), SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
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
    void chatNotificationDoesNotTargetRelatedGuardianWithoutChatConsent() {
        var failClosedService = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add,
                properties(true),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) ->
                        appUser.role() != AppUserRole.GUARDIAN);

        failClosedService.postMessage(
                patient(),
                SESSION_ID,
                new CompanionRealtimeService.PostMessageCommand(
                        UUID.randomUUID(),
                        "확인 메시지",
                        List.of()));

        assertThat(events)
                .singleElement()
                .isInstanceOfSatisfying(CompanionChatMessageCreatedEvent.class, event ->
                        assertThat(event.recipientUserIds()).containsExactly(MANAGER_ID));
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
    void disabledLegacyManagerLocationMasksCoordinatesWithoutRepositoryRead() {
        realtimeRepository.locations = List.of(location());
        var disabledService = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add,
                properties(false),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true);

        var snapshot = disabledService.getSnapshot(patient(), SESSION_ID);

        assertThat(snapshot.locations()).isEmpty();
        assertThat(realtimeRepository.locationReadCount).isZero();
    }

    @Test
    void disabledLegacyManagerLocationRejectsManagerWriteBeforeRepository() {
        var disabledService = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add,
                properties(false),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true);

        assertThatThrownBy(() -> disabledService.postLocation(
                        manager(), SESSION_ID, locationCommand()))
                .isInstanceOfSatisfying(CompanionSessionException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.error()).isEqualTo("companion_location_sharing_disabled");
                });
        assertThat(realtimeRepository.lastLocation).isNull();
    }

    @Test
    void missingReadMessageIsReportedAsNotFound() {
        realtimeRepository.receipt = Optional.empty();

        assertThatThrownBy(() -> service.updateReadReceipt(patient(), SESSION_ID, UUID.randomUUID()))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_chat_message_not_found");
    }

    @Test
    void participantGetsAuthorizedAttachmentDownloadPath() {
        realtimeRepository.attachment = Optional.of(attachment());

        var result = service.getAttachment(patient(), SESSION_ID, ATTACHMENT_ID);

        assertThat(result.id()).isEqualTo(ATTACHMENT_ID);
        assertThat(result.downloadPath()).isEqualTo(
                "/api/companion-sessions/" + SESSION_ID + "/attachments/" + ATTACHMENT_ID);
    }

    @Test
    void unrelatedParticipantCannotReadAttachmentMetadata() {
        realtimeRepository.attachment = Optional.of(attachment());
        var unrelatedPatient = user(UUID.randomUUID(), AppUserRole.PATIENT);

        assertThatThrownBy(() -> service.getAttachment(unrelatedPatient, SESSION_ID, ATTACHMENT_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void unavailableAttachmentIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.getAttachment(patient(), SESSION_ID, ATTACHMENT_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_chat_attachment_not_found");
    }

    @Test
    void guardianRelationshipWithoutChatOrLocationConsentIsDenied() {
        var failClosedService = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add,
                properties(true),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> false);

        assertThatThrownBy(() -> failClosedService.getSnapshot(guardian(), SESSION_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void chatOnlyGuardianGetsPollingDataWithoutLocationTopicOrAttachmentMetadata() {
        realtimeRepository.messages = List.of(new CompanionRealtimeRepository.ChatMessageRecord(
                MESSAGE_ID,
                SESSION_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "PATIENT",
                "확인 메시지",
                Instant.parse("2026-07-18T00:10:00Z"),
                List.of(attachment())));
        realtimeRepository.locations = List.of(location());
        var chatOnlyService = new DefaultCompanionRealtimeService(
                sessionRepository,
                realtimeRepository,
                events::add,
                properties(true),
                (appUser, appointmentId, patientUserId, guardianUserId, scope) ->
                        scope == com.bodeul.core.consent.AdultPatientGuardianSharingPolicy
                                .InformationScope.CHAT);

        var snapshot = chatOnlyService.getSnapshot(guardian(), SESSION_ID);

        assertThat(snapshot.realtimeTopic()).isEmpty();
        assertThat(snapshot.messages()).singleElement()
                .satisfies(message -> assertThat(message.attachments()).isEmpty());
        assertThat(snapshot.locations()).isEmpty();
    }

    private CompanionRealtimeService.PostLocationCommand locationCommand() {
        return new CompanionRealtimeService.PostLocationCommand(
                UUID.randomUUID(),
                37.5665,
                126.9780,
                Instant.now().toString());
    }

    private CompanionSessionProperties properties(boolean legacyManagerLocationEnabled) {
        CompanionSessionProperties properties = new CompanionSessionProperties();
        properties.setLegacyManagerLocationEnabled(legacyManagerLocationEnabled);
        return properties;
    }

    private AppUserRepository.AppUser patient() {
        return user(PATIENT_ID, AppUserRole.PATIENT);
    }

    private AppUserRepository.AppUser manager() {
        return user(MANAGER_ID, AppUserRole.MANAGER);
    }

    private AppUserRepository.AppUser guardian() {
        return user(GUARDIAN_ID, AppUserRole.GUARDIAN);
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
                guideSnapshot(),
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
                false,
                null,
                "none",
                null,
                3,
                Instant.parse("2026-07-18T00:00:00Z"),
                status.equals("COMPLETED") ? Instant.parse("2026-07-18T01:00:00Z") : null,
                null);
    }

    private CompanionSessionRepository.GuideSnapshotRecord guideSnapshot() {
        List<CompanionSessionRepository.GuideStepRecord> steps = new ArrayList<>();
        for (int order = 1; order <= 5; order++) {
            steps.add(new CompanionSessionRepository.GuideStepRecord(
                    "STEP_" + order,
                    order,
                    "단계 " + order,
                    "설명 " + order));
        }
        return new CompanionSessionRepository.GuideSnapshotRecord(
                UUID.fromString("45bd0403-59a7-449a-90f6-fae10c79da30"),
                2L,
                1,
                "HOSPITAL_GUIDE_STEP_CODE_V1",
                true,
                steps);
    }

    private CompanionSessionRepository.SessionRecord withCareEndedAt(
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
                Instant.parse("2026-07-18T01:00:00Z"), "", "NOT_REQUESTED", 0, "", null,
                List.of());
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

    private CompanionRealtimeRepository.AttachmentRecord attachment() {
        return new CompanionRealtimeRepository.AttachmentRecord(
                ATTACHMENT_ID,
                MESSAGE_ID,
                "companion-chat-attachments/" + SESSION_ID + "/attachment.pdf",
                "attachment.pdf",
                "application/pdf",
                1_024L);
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
        private Optional<AttachmentRecord> attachment = Optional.empty();
        private MessageMutation lastMessage;
        private LocationMutation lastLocation;
        private int locationReadCount;
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
            locationReadCount++;
            return locations;
        }

        @Override
        public Optional<AttachmentRecord> findAttachment(UUID sessionId, UUID attachmentId) {
            return attachment.filter(value -> value.id().equals(attachmentId));
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
