package com.bodeul.core.auth;

import java.util.Optional;
import java.util.UUID;

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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FirebaseAuthenticationIntegrationTests.AuthTestConfiguration.class)
class FirebaseAuthenticationIntegrationTests {

    private static final UUID APP_USER_ID = UUID.fromString("0f47cb7f-1db7-4eca-875a-dbf6115a37ea");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableFirebaseTokenVerifier tokenVerifier;

    @Autowired
    private MutableAppUserRepository appUserRepository;

    @BeforeEach
    void resetFakes() {
        tokenVerifier.reset();
        appUserRepository.reset();
    }

    @Test
    void missingAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void malformedBearerHeaderReturns401WithoutRawValue() throws Exception {
        String rawValue = "Basic raw-credential-value";

        mockMvc.perform(get("/api/auth/me").header("Authorization", rawValue))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_authorization"))
                .andExpect(content().string(not(containsString(rawValue))));
    }

    @Test
    void duplicateAuthorizationHeadersReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer first-token", "Bearer second-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_authorization"))
                .andExpect(content().string(not(containsString("first-token"))))
                .andExpect(content().string(not(containsString("second-token"))));
    }

    @Test
    void expiredTokenReturns401WithoutRawValue() throws Exception {
        assertInvalidFirebaseToken("expired-firebase-token");
    }

    @Test
    void tamperedTokenReturns401WithoutRawValue() throws Exception {
        assertInvalidFirebaseToken("tampered-firebase-token");
    }

    @Test
    void tokenFromAnotherFirebaseProjectReturns401WithoutRawValue() throws Exception {
        assertInvalidFirebaseToken("other-project-firebase-token");
    }

    @Test
    void unavailableFirebaseVerifierReturns503() throws Exception {
        tokenVerifier.failure = new FirebaseTokenVerifier.UnavailableException();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer firebase-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("auth_not_configured"));
    }

    @Test
    void mapsVerifiedUidAndPostgresRoleToAuthenticatedUser() throws Exception {
        String rawToken = "valid-firebase-token";
        appUserRepository.result = Optional.of(new AppUserRepository.AppUser(
                APP_USER_ID,
                "firebase-user-1",
                AppUserRole.GUARDIAN));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(APP_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("GUARDIAN"))
                .andExpect(content().string(not(containsString(rawToken))));

        assertThat(tokenVerifier.lastToken).isEqualTo(rawToken);
        assertThat(appUserRepository.lastFirebaseUid).isEqualTo("firebase-user-1");
    }

    @Test
    void missingPostgresRoleReturns403() throws Exception {
        appUserRepository.result = Optional.empty();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("role_not_found"));
    }

    @Test
    void authenticatedUserWithoutPermissionReturns403() throws Exception {
        appUserRepository.result = Optional.of(new AppUserRepository.AppUser(
                APP_USER_ID,
                "firebase-user-1",
                AppUserRole.MANAGER));

        mockMvc.perform(get("/restricted-test-path")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("permission_denied"));
    }

    @Test
    void roleLookupDatabaseFailureReturns503() throws Exception {
        appUserRepository.failure = new DataAccessResourceFailureException("database unavailable");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("role_lookup_failed"))
                .andExpect(content().string(not(containsString("database unavailable"))));
    }

    private void assertInvalidFirebaseToken(String rawToken) throws Exception {
        tokenVerifier.failure = new FirebaseTokenVerifier.InvalidTokenException();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_firebase_token"))
                .andExpect(content().string(not(containsString(rawToken))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthTestConfiguration {

        @Bean
        @Primary
        MutableFirebaseTokenVerifier mutableFirebaseTokenVerifier() {
            return new MutableFirebaseTokenVerifier();
        }

        @Bean
        MutableAppUserRepository mutableAppUserRepository() {
            return new MutableAppUserRepository();
        }
    }

    static class MutableFirebaseTokenVerifier implements FirebaseTokenVerifier {
        private RuntimeException failure;
        private String lastToken;

        @Override
        public VerifiedToken verify(String idToken) {
            lastToken = idToken;
            if (failure != null) {
                throw failure;
            }
            return new VerifiedToken("firebase-user-1");
        }

        void reset() {
            failure = null;
            lastToken = null;
        }
    }

    static class MutableAppUserRepository implements AppUserRepository {
        private Optional<AppUser> result;
        private DataAccessResourceFailureException failure;
        private String lastFirebaseUid;

        @Override
        public Optional<AppUser> findByFirebaseUid(String firebaseUid) {
            lastFirebaseUid = firebaseUid;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        void reset() {
            result = Optional.of(new AppUser(
                    APP_USER_ID,
                    "firebase-user-1",
                    AppUserRole.PATIENT));
            failure = null;
            lastFirebaseUid = null;
        }
    }
}
