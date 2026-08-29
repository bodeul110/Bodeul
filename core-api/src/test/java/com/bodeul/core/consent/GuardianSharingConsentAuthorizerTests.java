package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.Test;

import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.APPOINTMENT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.CHAT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.LOCATION;
import static org.assertj.core.api.Assertions.assertThat;

class GuardianSharingConsentAuthorizerTests {

    private static final UUID APPOINTMENT_ID = UUID.fromString("22a4d3a8-90e4-4b1d-80fd-7e1b16833a45");
    private static final UUID PATIENT_ID = UUID.fromString("82c6937d-4556-4c8d-a596-3e44ba2fb7d5");
    private static final UUID GUARDIAN_ID = UUID.fromString("bd03c078-242d-4437-90dd-0f77c500fa1b");
    private static final Instant NOW = Instant.parse("2026-08-29T02:00:00Z");

    @Test
    void appointmentRelationshipWithoutGrantIsDenied() {
        GuardianSharingConsentAuthorizer authorizer = authorizer(Optional.empty(), false);

        assertThat(authorizer.isAllowed(
                guardian(),
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                APPOINTMENT)).isFalse();
    }

    @Test
    void onlyExplicitScopeIsAllowedAndDisabledLocationStaysClosed() {
        GuardianSharingConsentAuthorizer authorizer = authorizer(
                Optional.of(grant(Set.of(APPOINTMENT, CHAT))),
                false);

        assertThat(authorizer.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isTrue();
        assertThat(authorizer.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, CHAT)).isTrue();
        assertThat(authorizer.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, LOCATION)).isFalse();
        assertThat(authorizer.canReceiveCombinedRealtimeTopic(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID)).isFalse();
    }

    @Test
    void policyMismatchExpiryAndRevocationFailClosed() {
        var mismatch = new GuardianSharingConsentAuthorizer(
                repository(
                        Optional.of(grant(Set.of(APPOINTMENT))),
                        "adult-guardian-sharing-v2",
                        false),
                fixedClock());
        var expired = new GuardianSharingConsentAuthorizer(
                repository(Optional.of(new AdultPatientGuardianSharingPolicy.Grant(
                        UUID.randomUUID(),
                        APPOINTMENT_ID,
                        PATIENT_ID,
                        GUARDIAN_ID,
                        Set.of(APPOINTMENT),
                        "adult-guardian-sharing-v1",
                        PATIENT_ID,
                        NOW.minus(8, ChronoUnit.DAYS),
                        NOW,
                        null,
                        null,
                        0))),
                fixedClock());
        var revokedGrant = AdultPatientGuardianSharingPolicy.revokeByPatient(
                grant(Set.of(APPOINTMENT)),
                PATIENT_ID,
                AppUserRole.PATIENT,
                NOW.minusSeconds(1));
        var revoked = authorizer(Optional.of(revokedGrant), false);

        assertThat(mismatch.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isFalse();
        assertThat(expired.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isFalse();
        assertThat(revoked.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isFalse();
    }

    @Test
    void provisionalExpiryDoesNotBlockDelayedCareButFinalizedExpiryDoes() {
        var expiredGrant = new AdultPatientGuardianSharingPolicy.Grant(
                UUID.randomUUID(),
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                Set.of(APPOINTMENT),
                "adult-guardian-sharing-v1",
                PATIENT_ID,
                NOW.minus(9, ChronoUnit.DAYS),
                NOW.minus(1, ChronoUnit.DAYS),
                null,
                null,
                0);
        var provisional = new GuardianSharingConsentAuthorizer(
                repository(Optional.of(expiredGrant), "adult-guardian-sharing-v1", false, false),
                fixedClock());
        var finalized = new GuardianSharingConsentAuthorizer(
                repository(Optional.of(expiredGrant), "adult-guardian-sharing-v1", false, true),
                fixedClock());

        assertThat(provisional.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isTrue();
        assertThat(finalized.isAllowed(
                guardian(), APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID, APPOINTMENT)).isFalse();
    }

    private GuardianSharingConsentAuthorizer authorizer(
            Optional<AdultPatientGuardianSharingPolicy.Grant> grant,
            boolean locationEnabled) {
        return new GuardianSharingConsentAuthorizer(
                repository(grant, "adult-guardian-sharing-v1", locationEnabled),
                fixedClock());
    }

    private GuardianSharingConsentRepository repository(
            Optional<AdultPatientGuardianSharingPolicy.Grant> grant) {
        return repository(grant, "adult-guardian-sharing-v1", false);
    }

    private GuardianSharingConsentRepository repository(
            Optional<AdultPatientGuardianSharingPolicy.Grant> grant,
            String policyVersion,
            boolean locationEnabled) {
        return repository(grant, policyVersion, locationEnabled, true);
    }

    private GuardianSharingConsentRepository repository(
            Optional<AdultPatientGuardianSharingPolicy.Grant> grant,
            String policyVersion,
            boolean locationEnabled,
            boolean expiryFinalized) {
        return new GuardianSharingConsentRepository() {
            @Override
            public ConsentSettings getSettings() {
                return new ConsentSettings(policyVersion, locationEnabled);
            }

            @Override
            public Optional<AppointmentContext> findAppointment(UUID appointmentRequestId) {
                return Optional.empty();
            }

            @Override
            public Optional<AdultPatientGuardianSharingPolicy.Grant> findByAppointmentId(
                    UUID appointmentRequestId) {
                return grant;
            }

            @Override
            public boolean isExpiryFinalized(UUID appointmentRequestId) {
                return expiryFinalized;
            }

            @Override
            public AdultPatientGuardianSharingPolicy.Grant grant(
                    AdultPatientGuardianSharingPolicy.Grant requestedGrant) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<AdultPatientGuardianSharingPolicy.Grant> revoke(
                    UUID appointmentRequestId,
                    UUID actorUserId,
                    Instant revokedAt,
                    long expectedVersion) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void appendEvent(
                    AdultPatientGuardianSharingPolicy.Grant savedGrant,
                    EventAction action,
                    UUID actorUserId,
                    Instant occurredAt) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AdultPatientGuardianSharingPolicy.Grant grant(
            Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes) {
        return AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                scopes,
                NOW.minus(1, ChronoUnit.DAYS),
                NOW.plus(6, ChronoUnit.DAYS),
                "adult-guardian-sharing-v1");
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private AppUserRepository.AppUser guardian() {
        return new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);
    }
}
