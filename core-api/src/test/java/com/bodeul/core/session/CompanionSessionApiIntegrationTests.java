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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bodeul.app-check.mode=observe")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "companion-session-test"})
@Import(CompanionSessionApiIntegrationTests.ApiTestConfiguration.class)
class CompanionSessionApiIntegrationTests {

    private static final UUID USER_ID = UUID.fromString("4b2e39de-12de-422c-b6a4-c57a805b1666");
    private static final UUID SESSION_ID = UUID.fromString("ae9bcf19-58e4-4e61-8253-06913adbbeb9");
    private static final UUID APPOINTMENT_ID = UUID.fromString("053c5d79-d5e8-4324-9907-a77ead090944");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableCompanionSessionService sessionService;

    @BeforeEach
    void reset() {
        sessionService.reset();
    }

    @Test
    void sessionApiRequiresFirebaseAuthentication() throws Exception {
        mockMvc.perform(get("/api/companion-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void authenticatedUserReadsSessionsWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/companion-sessions")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.sessions[0].id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.sessions[0].version").value(3));

        assertThat(sessionService.lastUser.id()).isEqualTo(USER_ID);
    }

    @Test
    void patchPassesOptimisticVersionAndFields() throws Exception {
        mockMvc.perform(patch("/api/companion-sessions/{sessionId}", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "version": 3,
                                  "guardianUpdate": "진료 대기 중",
                                  "prescriptionCollected": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));

        assertThat(sessionService.lastSessionId).isEqualTo(SESSION_ID);
        assertThat(sessionService.lastUpdate.version()).isEqualTo(3);
        assertThat(sessionService.lastUpdate.guardianUpdate()).isEqualTo("진료 대기 중");
        assertThat(sessionService.lastUpdate.prescriptionCollected()).isTrue();
    }

    @Test
    void servicePermissionFailureReturns403() throws Exception {
        sessionService.failure = CompanionSessionException.managerRequired();

        mockMvc.perform(patch("/api/companion-sessions/{sessionId}", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"version\":3,\"guardianUpdate\":\"변경\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("companion_session_manager_required"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(patch("/api/companion-sessions/{sessionId}", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_companion_session_request"));
    }

    @Test
    void databaseFailureReturns503WithoutInternalDetail() throws Exception {
        sessionService.failure = new DataAccessResourceFailureException("secret database detail");

        mockMvc.perform(get("/api/companion-sessions")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("companion_session_database_failure"))
                .andExpect(jsonPath("$.message")
                        .value("동행 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    private static CompanionSessionService.SessionView session() {
        return new CompanionSessionService.SessionView(
                SESSION_ID,
                "legacy-session",
                APPOINTMENT_ID,
                USER_ID,
                2,
                5,
                "WAITING",
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                3,
                "",
                "",
                "");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ApiTestConfiguration {

        @Bean
        @Primary
        FirebaseTokenVerifier sessionTestFirebaseTokenVerifier() {
            return idToken -> new FirebaseTokenVerifier.VerifiedToken("firebase-manager-1");
        }

        @Bean
        @Primary
        AppCheckTokenVerifier sessionTestAppCheckTokenVerifier() {
            return token -> new AppCheckTokenVerifier.VerifiedToken("test-app");
        }

        @Bean
        AppUserRepository sessionTestAppUserRepository() {
            return firebaseUid -> Optional.of(new AppUserRepository.AppUser(
                    USER_ID,
                    firebaseUid,
                    AppUserRole.MANAGER));
        }

        @Bean
        MutableCompanionSessionService mutableCompanionSessionService() {
            return new MutableCompanionSessionService();
        }
    }

    static final class MutableCompanionSessionService implements CompanionSessionService {
        private AppUserRepository.AppUser lastUser;
        private UUID lastSessionId;
        private UpdateSessionCommand lastUpdate;
        private RuntimeException failure;

        void reset() {
            lastUser = null;
            lastSessionId = null;
            lastUpdate = null;
            failure = null;
        }

        @Override
        public List<SessionView> getMySessions(AppUserRepository.AppUser appUser) {
            failIfNeeded();
            lastUser = appUser;
            return List.of(session());
        }

        @Override
        public SessionView getSession(AppUserRepository.AppUser appUser, UUID sessionId) {
            failIfNeeded();
            lastUser = appUser;
            lastSessionId = sessionId;
            return session();
        }

        @Override
        public SessionView updateSession(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                UpdateSessionCommand command) {
            failIfNeeded();
            lastUser = appUser;
            lastSessionId = sessionId;
            lastUpdate = command;
            return session();
        }

        @Override
        public SessionView advanceSession(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                long version) {
            failIfNeeded();
            return session();
        }

        @Override
        public ReportView getReport(AppUserRepository.AppUser appUser, UUID sessionId) {
            failIfNeeded();
            return report();
        }

        @Override
        public ReportView submitReport(
                AppUserRepository.AppUser appUser,
                UUID sessionId,
                SubmitReportCommand command) {
            failIfNeeded();
            return report();
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }

        private ReportView report() {
            return new ReportView(
                    UUID.randomUUID(),
                    "",
                    SESSION_ID,
                    "완료",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    0);
        }
    }
}
