package com.bodeul.core.auth;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.bodeul.core.account.FirebaseAccountDeletionImpactRepository;
import com.bodeul.core.place.PlaceSearchCategory;
import com.bodeul.core.place.PlaceSearchResult;
import com.bodeul.core.place.PlaceSearchService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bodeul.app-check.mode=observe")
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
    private MutableAppCheckTokenVerifier appCheckTokenVerifier;

    @Autowired
    private MutableAppUserRepository appUserRepository;

    @Autowired
    private MutablePlaceSearchService placeSearchService;

    @Autowired
    private MutableFirebaseAccountDeletionImpactRepository firebaseImpactRepository;

    @BeforeEach
    void resetFakes() {
        tokenVerifier.reset();
        appCheckTokenVerifier.reset();
        appUserRepository.reset();
        placeSearchService.reset();
        firebaseImpactRepository.reset();
    }

    @Test
    void missingAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void missingAuthorizationForAccountDeletionReadinessReturns401() throws Exception {
        mockMvc.perform(get("/api/account/deletion-readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void missingAuthorizationForPlaceSearchReturns401() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .queryParam("query", "서울대병원")
                        .queryParam("category", "HOSPITAL"))
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
    void accountDeletionReadinessIsReadOnlyAndFailsClosedWhenSourcesAreIncomplete() throws Exception {
        mockMvc.perform(get("/api/account/deletion-readiness")
                        .queryParam("firebaseUid", "other-firebase-user")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.deletionExecuted").value(false))
                .andExpect(jsonPath("$.decision").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.sources[0].source").value("POSTGRESQL"))
                .andExpect(jsonPath("$.sources[0].status").value("ERROR"))
                .andExpect(jsonPath("$.sources[1].source").value("FIRESTORE"))
                .andExpect(jsonPath("$.sources[1].status").value("PARTIAL"))
                .andExpect(jsonPath("$.sources[1].counts.userDocuments").value(1))
                .andExpect(jsonPath("$.sources[1].counts.notificationTokens").value(2))
                .andExpect(jsonPath("$.sources[1].counts.notificationTokenEntryMismatches").value(3))
                .andExpect(jsonPath("$.sources[1].counts.clientSupportRequests").value(4))
                .andExpect(jsonPath("$.sources[1].counts.supportInquiries").value(1))
                .andExpect(jsonPath("$.observationCodes").isEmpty())
                .andExpect(jsonPath("$.blockerCodes[0]").value("SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.blockerCodes[1]").value("INVENTORY_INCOMPLETE"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(not(containsString(APP_USER_ID.toString()))))
                .andExpect(content().string(not(containsString("firebase-user-1"))))
                .andExpect(content().string(not(containsString("other-firebase-user"))));

        assertThat(firebaseImpactRepository.lastFirebaseUid).isEqualTo("firebase-user-1");
    }

    @Test
    void verifiesAppCheckHeaderSeparatelyFromFirebaseIdToken() throws Exception {
        String appCheckToken = "valid-app-check-token";

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid-firebase-token")
                        .header("X-Firebase-AppCheck", appCheckToken))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(appCheckToken))));

        assertThat(appCheckTokenVerifier.lastToken).isEqualTo(appCheckToken);
        assertThat(tokenVerifier.lastToken).isEqualTo("valid-firebase-token");
    }

    @Test
    void authenticatedUserCanSearchHospitalThroughCoreApi() throws Exception {
        placeSearchService.results = List.of(
                new PlaceSearchResult("서울대학교병원", 37.5796d, 126.9990d));

        mockMvc.perform(get("/api/places/search")
                        .queryParam("query", "서울대병원")
                        .queryParam("category", "HOSPITAL")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("서울대학교병원"))
                .andExpect(jsonPath("$.places[0].latitude").value(37.5796d))
                .andExpect(jsonPath("$.places[0].longitude").value(126.9990d));

        assertThat(placeSearchService.lastUserId).isEqualTo(APP_USER_ID);
        assertThat(placeSearchService.lastQuery).isEqualTo("서울대병원");
        assertThat(placeSearchService.lastCategory).isEqualTo(PlaceSearchCategory.HOSPITAL);
    }

    @Test
    void invalidPlaceCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .queryParam("query", "서울대병원")
                        .queryParam("category", "CAFE")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_place_search_request"));
    }

    @Test
    void missingPlaceSearchParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .queryParam("category", "HOSPITAL")
                        .header("Authorization", "Bearer valid-firebase-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_place_search_request"));
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
        @Primary
        MutableAppCheckTokenVerifier mutableAppCheckTokenVerifier() {
            return new MutableAppCheckTokenVerifier();
        }

        @Bean
        MutableAppUserRepository mutableAppUserRepository() {
            return new MutableAppUserRepository();
        }

        @Bean
        @Primary
        MutablePlaceSearchService mutablePlaceSearchService() {
            return new MutablePlaceSearchService();
        }

        @Bean
        @Primary
        MutableFirebaseAccountDeletionImpactRepository mutableFirebaseAccountDeletionImpactRepository() {
            return new MutableFirebaseAccountDeletionImpactRepository();
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

    static class MutableAppCheckTokenVerifier implements AppCheckTokenVerifier {
        private RuntimeException failure;
        private String lastToken;

        @Override
        public VerifiedToken verify(String appCheckToken) {
            lastToken = appCheckToken;
            if (failure != null) {
                throw failure;
            }
            return new VerifiedToken("1:533563500316:android:registered-app");
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

    static class MutablePlaceSearchService implements PlaceSearchService {
        private List<PlaceSearchResult> results;
        private UUID lastUserId;
        private String lastQuery;
        private PlaceSearchCategory lastCategory;

        @Override
        public List<PlaceSearchResult> search(
                UUID userId,
                String query,
                PlaceSearchCategory category) {
            lastUserId = userId;
            lastQuery = query;
            lastCategory = category;
            return results;
        }

        void reset() {
            results = List.of();
            lastUserId = null;
            lastQuery = null;
            lastCategory = null;
        }
    }

    static class MutableFirebaseAccountDeletionImpactRepository
            implements FirebaseAccountDeletionImpactRepository {
        private String lastFirebaseUid;

        @Override
        public FirestoreImpact inspect(String firebaseUid) {
            lastFirebaseUid = firebaseUid;
            return new FirestoreImpact(1, 2, 2, 3, 0, 0, 4, 1);
        }

        void reset() {
            lastFirebaseUid = null;
        }
    }
}
