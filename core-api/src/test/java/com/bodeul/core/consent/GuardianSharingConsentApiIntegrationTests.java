package com.bodeul.core.consent;

import java.util.Optional;
import java.util.Set;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "guardian-sharing-consent-test"})
@Import(GuardianSharingConsentApiIntegrationTests.ApiTestConfiguration.class)
class GuardianSharingConsentApiIntegrationTests {

    private static final UUID USER_ID = UUID.fromString("cdf61a9e-13cd-4bd0-88b9-617a9dd24d80");
    private static final UUID GUARDIAN_ID = UUID.fromString("3c109825-d466-4484-849f-c1dd15175683");
    private static final UUID APPOINTMENT_ID = UUID.fromString("cdebe2c1-c282-41af-963e-dea38c18f3ad");
    private static final UUID CONSENT_ID = UUID.fromString("446b0892-8841-4687-a667-a33ba3ac608e");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableService consentService;

    @BeforeEach
    void setUp() {
        consentService.lastScopes = null;
    }

    @Test
    void grantReadAndRevokeUseAuthenticatedPatientAndNoStore() throws Exception {
        mockMvc.perform(put("/api/appointments/{appointmentId}/guardian-sharing-consent", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopes":["APPOINTMENT","CHAT","REPORT"],
                                 "adultPatientConfirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointmentRequestId").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.scopes[0]").exists());

        mockMvc.perform(get("/api/appointments/{appointmentId}/guardian-sharing-consent", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-id-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(delete("/api/appointments/{appointmentId}/guardian-sharing-consent", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-id-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownScopeIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(put("/api/appointments/{appointmentId}/guardian-sharing-consent", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopes":["EVERYTHING"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_guardian_sharing_consent_request"));
    }

    @Test
    void adultConfirmationValueIsForwardedToService() throws Exception {
        mockMvc.perform(put("/api/appointments/{appointmentId}/guardian-sharing-consent", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopes":["APPOINTMENT"],"adultPatientConfirmed":false}
                                """))
                .andExpect(status().isOk());

        assertThat(consentService.lastAdultPatientConfirmed).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ApiTestConfiguration {

        @Bean
        @Primary
        FirebaseTokenVerifier consentTestFirebaseTokenVerifier() {
            return idToken -> new FirebaseTokenVerifier.VerifiedToken("firebase-patient");
        }

        @Bean
        @Primary
        AppCheckTokenVerifier consentTestAppCheckTokenVerifier() {
            return token -> new AppCheckTokenVerifier.VerifiedToken("test-app");
        }

        @Bean
        AppUserRepository consentTestAppUserRepository() {
            return firebaseUid -> Optional.of(new AppUserRepository.AppUser(
                    USER_ID,
                    firebaseUid,
                    AppUserRole.PATIENT));
        }

        @Bean
        MutableService guardianSharingConsentService() {
            return new MutableService();
        }
    }

    static final class MutableService implements GuardianSharingConsentService {
        private Set<AdultPatientGuardianSharingPolicy.InformationScope> lastScopes;
        private boolean lastAdultPatientConfirmed;

        @Override
        public ConsentView get(AppUserRepository.AppUser appUser, UUID appointmentRequestId) {
            return view();
        }

        @Override
        public ConsentView grant(
                AppUserRepository.AppUser appUser,
                UUID appointmentRequestId,
                Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes,
                boolean adultPatientConfirmed) {
            lastScopes = scopes;
            lastAdultPatientConfirmed = adultPatientConfirmed;
            return view();
        }

        @Override
        public ConsentView revoke(
                AppUserRepository.AppUser appUser,
                UUID appointmentRequestId) {
            return view();
        }

        private ConsentView view() {
            Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes = lastScopes == null
                    ? Set.of(AdultPatientGuardianSharingPolicy.InformationScope.APPOINTMENT)
                    : lastScopes;
            return new ConsentView(
                    CONSENT_ID,
                    APPOINTMENT_ID,
                    USER_ID,
                    GUARDIAN_ID,
                    scopes,
                    "adult-guardian-sharing-v1",
                    "2026-08-29T01:00:00Z",
                    "2026-08-29T01:00:00Z",
                    "2026-09-08T01:00:00Z",
                    "",
                    true,
                    false,
                    false,
                    0);
        }
    }
}
