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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.sessions[0].currentStepOrder").value(2))
                .andExpect(jsonPath("$.sessions[0].totalStepCount").value(5))
                .andExpect(jsonPath("$.sessions[0].version").value(3))
                .andExpect(jsonPath("$.sessions[0].guideId")
                        .value("45bd0403-59a7-449a-90f6-fae10c79da30"))
                .andExpect(jsonPath("$.sessions[0].guideRevision").value(4))
                .andExpect(jsonPath("$.sessions[0].steps[1].code").value("STEP_2"))
                .andExpect(jsonPath("$.sessions[0].steps[1].order").value(2))
                .andExpect(jsonPath("$.sessions[0].steps[1].title").value("단계 2"))
                .andExpect(jsonPath("$.sessions[0].steps[1].description").value("설명 2"))
                .andExpect(jsonPath("$.sessions[0].currentStepCode").value("STEP_2"))
                .andExpect(jsonPath("$.sessions[0].canAdvance").value(true))
                .andExpect(jsonPath("$.sessions[0].blockedReason").value(nullValue()));

        assertThat(sessionService.lastUser.id()).isEqualTo(USER_ID);
    }

    @Test
    void authenticatedUserReadsFrozenSessionDetail() throws Exception {
        mockMvc.perform(get("/api/companion-sessions/{sessionId}", SESSION_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.steps.length()").value(5))
                .andExpect(jsonPath("$.currentStepCode").value("STEP_2"))
                .andExpect(jsonPath("$.canAdvance").value(true));

        assertThat(sessionService.lastSessionId).isEqualTo(SESSION_ID);
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
                                  "preConsultationConfirmed": true,
                                  "prescriptionCollected": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));

        assertThat(sessionService.lastSessionId).isEqualTo(SESSION_ID);
        assertThat(sessionService.lastUpdate.version()).isEqualTo(3);
        assertThat(sessionService.lastUpdate.guardianUpdate()).isEqualTo("진료 대기 중");
        assertThat(sessionService.lastUpdate.preConsultationConfirmed()).isTrue();
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
    void assignedManagerAdvancesWithOptimisticVersion() throws Exception {
        mockMvc.perform(post("/api/companion-sessions/{sessionId}/advance", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.canAdvance").value(true));

        assertThat(sessionService.lastSessionId).isEqualTo(SESSION_ID);
        assertThat(sessionService.lastAdvanceVersion).isEqualTo(3);
    }

    @Test
    void advancePermissionAndStateConflictsReturnExpectedStatus() throws Exception {
        sessionService.failure = CompanionSessionException.managerRequired();
        mockMvc.perform(post("/api/companion-sessions/{sessionId}/advance", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("companion_session_manager_required"));

        sessionService.failure = CompanionSessionException.stateConflict();
        mockMvc.perform(post("/api/companion-sessions/{sessionId}/advance", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("companion_session_state_conflict"));

        sessionService.failure = CompanionSessionException.versionConflict();
        mockMvc.perform(post("/api/companion-sessions/{sessionId}/advance", SESSION_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("companion_session_version_conflict"));
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
                false,
                false,
                "",
                "none",
                "",
                3,
                "",
                "",
                "",
                UUID.fromString("45bd0403-59a7-449a-90f6-fae10c79da30"),
                4L,
                guideSteps(),
                "STEP_2",
                true,
                null);
    }

    private static List<CompanionSessionService.GuideStepView> guideSteps() {
        return List.of(
                new CompanionSessionService.GuideStepView("STEP_1", 1, "단계 1", "설명 1"),
                new CompanionSessionService.GuideStepView("STEP_2", 2, "단계 2", "설명 2"),
                new CompanionSessionService.GuideStepView("STEP_3", 3, "단계 3", "설명 3"),
                new CompanionSessionService.GuideStepView("STEP_4", 4, "단계 4", "설명 4"),
                new CompanionSessionService.GuideStepView("STEP_5", 5, "단계 5", "설명 5"));
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
        private long lastAdvanceVersion;
        private RuntimeException failure;

        void reset() {
            lastUser = null;
            lastSessionId = null;
            lastUpdate = null;
            lastAdvanceVersion = -1;
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
            lastUser = appUser;
            lastSessionId = sessionId;
            lastAdvanceVersion = version;
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
