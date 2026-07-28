package com.bodeul.core.session;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompanionAttachmentServiceTests {

    private static final UUID USER_ID = UUID.fromString("6d17caaa-4754-46f6-b394-9b357fca54ec");
    private static final UUID SESSION_ID = UUID.fromString("d67f6424-8d71-48fa-88c0-403cc11bcbdb");
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("9c8cc2e1-8ee2-4739-b20a-0b58a42e25f1");
    private static final UUID ATTACHMENT_ID = UUID.fromString("599b25fb-f2e6-4f31-a2b1-4888c4bda2ac");

    private FakeRealtimeService realtimeService;
    private FakeAttachmentStorage attachmentStorage;
    private DefaultCompanionAttachmentService service;

    @BeforeEach
    void setUp() {
        realtimeService = new FakeRealtimeService();
        attachmentStorage = new FakeAttachmentStorage();
        service = new DefaultCompanionAttachmentService(realtimeService, attachmentStorage);
    }

    @Test
    void multipartAttachmentIsStoredBeforeMessageMetadata() {
        byte[] content = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01};
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "../검사결과.png",
                "IMAGE/PNG",
                content);

        service.postMessage(
                patient(),
                SESSION_ID,
                CLIENT_MESSAGE_ID,
                "검사 결과입니다",
                List.of(attachment));

        assertThat(attachmentStorage.storedPaths).singleElement()
                .asString()
                .matches("companion-chat-attachments/" + SESSION_ID + "/" + CLIENT_MESSAGE_ID
                        + "/0-[0-9a-f]{64}\\.png");
        assertThat(realtimeService.lastCommand.attachments()).singleElement()
                .satisfies(saved -> {
                    assertThat(saved.fileName()).isEqualTo("검사결과.png");
                    assertThat(saved.contentType()).isEqualTo("image/png");
                    assertThat(saved.sizeBytes()).isEqualTo(content.length);
                    assertThat(saved.storagePath()).isEqualTo(attachmentStorage.storedPaths.get(0));
                });
    }

    @Test
    void unsupportedAttachmentTypeIsRejectedBeforeStorageWrite() {
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "animation.gif",
                "image/gif",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.postMessage(
                        patient(),
                        SESSION_ID,
                        CLIENT_MESSAGE_ID,
                        "",
                        List.of(attachment)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");

        assertThat(attachmentStorage.storedPaths).isEmpty();
    }

    @Test
    void declaredContentTypeMustMatchFileSignature() {
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "not-an-image.png",
                "image/png",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.postMessage(
                        patient(),
                        SESSION_ID,
                        CLIENT_MESSAGE_ID,
                        "",
                        List.of(attachment)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("invalid_companion_session_request");

        assertThat(attachmentStorage.storedPaths).isEmpty();
    }

    @Test
    void databaseFailureDeletesOnlyObjectsCreatedByCurrentRequest() {
        realtimeService.failure = CompanionSessionException.idempotencyConflict();
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "document.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.postMessage(
                        patient(),
                        SESSION_ID,
                        CLIENT_MESSAGE_ID,
                        "",
                        List.of(attachment)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_message_idempotency_conflict");

        assertThat(attachmentStorage.deletedPaths)
                .containsExactlyElementsOf(attachmentStorage.storedPaths);
    }

    @Test
    void authorizationFailureHappensBeforeStorageWrite() {
        realtimeService.validationFailure = CompanionSessionException.permissionDenied();
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "document.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.postMessage(
                        patient(),
                        SESSION_ID,
                        CLIENT_MESSAGE_ID,
                        "",
                        List.of(attachment)))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");

        assertThat(attachmentStorage.storedPaths).isEmpty();
        assertThat(realtimeService.lastCommand).isNull();
    }

    @Test
    void retryDoesNotDeletePreexistingIdempotentObject() {
        attachmentStorage.created = false;
        realtimeService.failure = CompanionSessionException.idempotencyConflict();
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "document.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.postMessage(
                patient(),
                SESSION_ID,
                CLIENT_MESSAGE_ID,
                "",
                List.of(attachment))).isInstanceOf(CompanionSessionException.class);

        assertThat(attachmentStorage.storedPaths).hasSize(1);
        assertThat(attachmentStorage.deletedPaths).isEmpty();
    }

    @Test
    void authorizedDownloadUsesStoredMetadataAndExactSize() {
        byte[] content = "pdf-content".getBytes(StandardCharsets.UTF_8);
        attachmentStorage.readContent = content;
        realtimeService.attachmentView = new CompanionRealtimeService.AttachmentView(
                ATTACHMENT_ID,
                "companion-chat-attachments/" + SESSION_ID + "/attachment.pdf",
                "attachment.pdf",
                "application/pdf",
                content.length,
                "/api/companion-sessions/" + SESSION_ID + "/attachments/" + ATTACHMENT_ID);

        var result = service.download(patient(), SESSION_ID, ATTACHMENT_ID);

        assertThat(realtimeService.lastAttachmentId).isEqualTo(ATTACHMENT_ID);
        assertThat(attachmentStorage.readPath).isEqualTo(realtimeService.attachmentView.storagePath());
        assertThat(result.content()).containsExactly(content);
        assertThat(result.fileName()).isEqualTo("attachment.pdf");
    }

    @Test
    void changedStoredObjectSizeIsUnavailable() {
        attachmentStorage.readContent = new byte[]{1, 2};
        realtimeService.attachmentView = new CompanionRealtimeService.AttachmentView(
                ATTACHMENT_ID,
                "companion-chat-attachments/" + SESSION_ID + "/attachment.pdf",
                "attachment.pdf",
                "application/pdf",
                3L,
                "/api/companion-sessions/" + SESSION_ID + "/attachments/" + ATTACHMENT_ID);

        assertThatThrownBy(() -> service.download(patient(), SESSION_ID, ATTACHMENT_ID))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_chat_attachment_unavailable");
    }

    private AppUserRepository.AppUser patient() {
        return new AppUserRepository.AppUser(USER_ID, "firebase-patient", AppUserRole.PATIENT);
    }

    private static final class FakeRealtimeService implements CompanionRealtimeService {
        private PostMessageCommand lastCommand;
        private UUID lastAttachmentId;
        private AttachmentView attachmentView;
        private RuntimeException failure;
        private RuntimeException validationFailure;

        @Override
        public RealtimeSnapshotView getSnapshot(AppUserRepository.AppUser appUser, UUID sessionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatMessageView postMessage(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                PostMessageCommand command) {
            lastCommand = command;
            if (failure != null) {
                throw failure;
            }
            return new ChatMessageView(
                    UUID.randomUUID(),
                    command.clientMessageId(),
                    appUser.id(),
                    appUser.role().name(),
                    command.body(),
                    "2026-07-28T00:00:00Z",
                    List.of());
        }

        @Override
        public void validateMessageWrite(
                AppUserRepository.AppUser appUser,
                UUID sessionId) {
            if (validationFailure != null) {
                throw validationFailure;
            }
        }

        @Override
        public AttachmentView getAttachment(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID attachmentId) {
            lastAttachmentId = attachmentId;
            return attachmentView;
        }

        @Override
        public ReadReceiptView updateReadReceipt(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID lastReadMessageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocationView postLocation(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                PostLocationCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeAttachmentStorage implements CompanionAttachmentStorage {
        private final List<String> storedPaths = new ArrayList<>();
        private final List<String> deletedPaths = new ArrayList<>();
        private boolean created = true;
        private byte[] readContent = new byte[0];
        private String readPath;

        @Override
        public StoreResult store(
                String storagePath,
                byte[] content,
                String contentType,
                String sha256) {
            storedPaths.add(storagePath);
            return new StoreResult(created);
        }

        @Override
        public byte[] read(String storagePath, long maxBytes) {
            readPath = storagePath;
            return readContent;
        }

        @Override
        public void delete(String storagePath) {
            deletedPaths.add(storagePath);
        }
    }
}
