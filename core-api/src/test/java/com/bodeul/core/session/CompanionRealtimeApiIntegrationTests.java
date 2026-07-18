package com.bodeul.core.session;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @BeforeEach
    void reset() {
        realtimeService.reset();
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
                                  "body": "검사실로 이동합니다.",
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
}
