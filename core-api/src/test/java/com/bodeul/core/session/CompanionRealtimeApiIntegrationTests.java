package com.bodeul.core.session;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppCheckTokenVerifier;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bodeul.app-check.mode=observe")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "companion-realtime-test"})
@Import(CompanionRealtimeApiIntegrationTests.ApiTestConfiguration.class)
class CompanionRealtimeApiIntegrationTests {

    private static final UUID USER_ID = UUID.fromString("4b2e39de-12de-422c-b6a4-c57a805b1666");
    private static final UUID SESSION_ID = UUID.fromString("ae9bcf19-58e4-4e61-8253-06913adbbeb9");
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("9b047d55-e774-4c20-bb09-5eb31008e920");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableCompanionRealtimeService realtimeService;

    @Autowired
    private MutableCompanionAttachmentService attachmentService;

    @BeforeEach
    void reset() {
        realtimeService.reset();
        attachmentService.reset();
    }

    @Test
    void realtimeApiRequiresFirebaseAuthentication() throws Exception {
        mockMvc.perform(get("/api/companion-sessions/{sessionId}/realtime", SESSION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void snapshotIsReturnedWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/companion-sessions/{sessionId}/realtime", SESSION_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.realtimeTopic")
                        .value("companion-session:" + SESSION_ID))
                .andExpect(jsonPath("$.messages").isArray());

        assertThat(realtimeService.lastUser.id()).isEqualTo(USER_ID);
        assertThat(realtimeService.lastSessionId).isEqualTo(SESSION_ID);
    }

    @Test
    void messageRequestMapsIdempotencyAndAttachmentMetadata() throws Exception {
        mockMvc.perform(post("/api/companion-sessions/{sessionId}/messages", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "clientMessageId": "%s",
                                  "body": "검사실로 이동합니다",
                                  "attachments": [{
                                    "storagePath": "companion-chat-attachments/%s/photo.jpg",
                                    "fileName": "photo.jpg",
                                    "contentType": "image/jpeg",
                                    "sizeBytes": 1024
                                  }]
                                }
                                """.formatted(CLIENT_MESSAGE_ID, SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.clientMessageId").value(CLIENT_MESSAGE_ID.toString()));

        assertThat(realtimeService.lastMessage.clientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(realtimeService.lastMessage.attachments()).singleElement()
                .extracting("sizeBytes")
                .isEqualTo(1_024L);
    }

    @Test
    void domainFailureKeepsStableErrorContract() throws Exception {
        realtimeService.failure = CompanionSessionException.idempotencyConflict();

        mockMvc.perform(post("/api/companion-sessions/{sessionId}/messages", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {"clientMessageId":"%s","body":"충돌"}
                                """.formatted(CLIENT_MESSAGE_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("companion_message_idempotency_conflict"));
    }

    @Test
    void multipartMessageDelegatesAttachmentBytesToAttachmentService() throws Exception {
        MockMultipartFile attachment = new MockMultipartFile(
                "attachments",
                "검사결과.png",
                "image/png",
                "image-bytes".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/companion-sessions/{sessionId}/messages", SESSION_ID)
                        .file(attachment)
                        .param("clientMessageId", CLIENT_MESSAGE_ID.toString())
                        .param("body", "검사 결과를 확인해 주세요")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.clientMessageId").value(CLIENT_MESSAGE_ID.toString()));

        assertThat(attachmentService.lastUser.id()).isEqualTo(USER_ID);
        assertThat(attachmentService.lastSessionId).isEqualTo(SESSION_ID);
        assertThat(attachmentService.lastClientMessageId).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(attachmentService.lastBody).isEqualTo("검사 결과를 확인해 주세요");
        assertThat(attachmentService.lastAttachments).singleElement()
                .extracting("originalFilename", "contentType", "size")
                .containsExactly("검사결과.png", "image/png", 11L);
    }

    @Test
    void authenticatedParticipantDownloadsAttachmentWithoutCaching() throws Exception {
        UUID attachmentId = UUID.randomUUID();

        mockMvc.perform(get(
                            "/api/companion-sessions/{sessionId}/attachments/{attachmentId}",
                            SESSION_ID,
                            attachmentId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes("attachment-content".getBytes(StandardCharsets.UTF_8)));

        assertThat(attachmentService.lastUser.id()).isEqualTo(USER_ID);
        assertThat(attachmentService.lastSessionId).isEqualTo(SESSION_ID);
        assertThat(attachmentService.lastAttachmentId).isEqualTo(attachmentId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ApiTestConfiguration {

        @Bean
        @Primary
        FirebaseTokenVerifier realtimeTestFirebaseTokenVerifier() {
            return idToken -> new FirebaseTokenVerifier.VerifiedToken("firebase-patient-1");
        }

        @Bean
        @Primary
        AppCheckTokenVerifier realtimeTestAppCheckTokenVerifier() {
            return token -> new AppCheckTokenVerifier.VerifiedToken("test-app");
        }

        @Bean
        AppUserRepository realtimeTestAppUserRepository() {
            return firebaseUid -> Optional.of(new AppUserRepository.AppUser(
                    USER_ID,
                    firebaseUid,
                    AppUserRole.PATIENT));
        }

        @Bean
        MutableCompanionRealtimeService mutableCompanionRealtimeService() {
            return new MutableCompanionRealtimeService();
        }

        @Bean
        MutableCompanionAttachmentService mutableCompanionAttachmentService() {
            return new MutableCompanionAttachmentService();
        }
    }

    static final class MutableCompanionRealtimeService implements CompanionRealtimeService {
        private AppUserRepository.AppUser lastUser;
        private UUID lastSessionId;
        private PostMessageCommand lastMessage;
        private RuntimeException failure;

        void reset() {
            lastUser = null;
            lastSessionId = null;
            lastMessage = null;
            failure = null;
        }

        @Override
        public RealtimeSnapshotView getSnapshot(
                AppUserRepository.AppUser appUser,
                UUID sessionId) {
            failIfNeeded();
            lastUser = appUser;
            lastSessionId = sessionId;
            return new RealtimeSnapshotView(
                    "companion-session:" + sessionId,
                    List.of(),
                    List.of(),
                    List.of());
        }

        @Override
        public ChatMessageView postMessage(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                PostMessageCommand command) {
            failIfNeeded();
            lastUser = appUser;
            lastSessionId = sessionId;
            lastMessage = command;
            return new ChatMessageView(
                    UUID.randomUUID(),
                    command.clientMessageId(),
                    appUser.id(),
                    appUser.role().name(),
                    command.body(),
                    "2026-07-18T00:00:00Z",
                    List.of());
        }

        @Override
        public AttachmentView getAttachment(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID attachmentId) {
            failIfNeeded();
            lastUser = appUser;
            lastSessionId = sessionId;
            return new AttachmentView(
                    attachmentId,
                    "companion-chat-attachments/" + sessionId + "/attachment.pdf",
                    "attachment.pdf",
                    "application/pdf",
                    18L,
                    "/api/companion-sessions/" + sessionId + "/attachments/" + attachmentId);
        }

        @Override
        public ReadReceiptView updateReadReceipt(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID lastReadMessageId) {
            failIfNeeded();
            return new ReadReceiptView(
                    appUser.id(),
                    appUser.role().name(),
                    lastReadMessageId,
                    "2026-07-18T00:00:00Z");
        }

        @Override
        public LocationView postLocation(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                PostLocationCommand command) {
            failIfNeeded();
            return new LocationView(
                    UUID.randomUUID(),
                    command.clientLocationId(),
                    appUser.id(),
                    command.latitude(),
                    command.longitude(),
                    command.capturedAt());
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    static final class MutableCompanionAttachmentService implements CompanionAttachmentService {
        private AppUserRepository.AppUser lastUser;
        private UUID lastSessionId;
        private UUID lastClientMessageId;
        private String lastBody;
        private List<MultipartFile> lastAttachments;
        private UUID lastAttachmentId;

        void reset() {
            lastUser = null;
            lastSessionId = null;
            lastClientMessageId = null;
            lastBody = null;
            lastAttachments = null;
            lastAttachmentId = null;
        }

        @Override
        public CompanionRealtimeService.ChatMessageView postMessage(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID clientMessageId,
                String body,
                List<MultipartFile> attachments) {
            lastUser = appUser;
            lastSessionId = sessionId;
            lastClientMessageId = clientMessageId;
            lastBody = body;
            lastAttachments = attachments;
            return new CompanionRealtimeService.ChatMessageView(
                    UUID.randomUUID(),
                    clientMessageId,
                    appUser.id(),
                    appUser.role().name(),
                    body,
                    "2026-07-18T00:00:00Z",
                    List.of());
        }

        @Override
        public DownloadedAttachment download(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UUID attachmentId) {
            lastUser = appUser;
            lastSessionId = sessionId;
            lastAttachmentId = attachmentId;
            return new DownloadedAttachment(
                    "검사결과.pdf",
                    "application/pdf",
                    "attachment-content".getBytes(StandardCharsets.UTF_8));
        }
    }
}
