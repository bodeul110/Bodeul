package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.APPOINTMENT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.ATTACHMENT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.CHAT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.LOCATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultGuardianSharingConsentServiceTests {

    private static final UUID APPOINTMENT_ID = UUID.fromString("3f9cda70-1c36-4554-827b-ce62561a80bb");
    private static final UUID PATIENT_ID = UUID.fromString("f4a6797c-a3f7-4db4-be20-ee5736514275");
    private static final UUID GUARDIAN_ID = UUID.fromString("6d28a448-048a-4893-a7f1-e536f29fa11f");
    private static final Instant NOW = Instant.parse("2026-08-29T01:00:00Z");
    private static final Instant APPOINTMENT_AT = Instant.parse("2026-09-01T01:00:00Z");

    private FakeRepository repository;
    private DefaultGuardianSharingConsentService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new DefaultGuardianSharingConsentService(
                repository,
                appUsers(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void patientGrantsAppointmentScopedConsentWithSevenDayExpiryAndAuditEvent() {
        var view = service.grant(patient(), APPOINTMENT_ID, Set.of(APPOINTMENT, CHAT), true);

        assertThat(view.appointmentRequestId()).isEqualTo(APPOINTMENT_ID);
        assertThat(view.scopes()).containsExactlyInAnyOrder(APPOINTMENT, CHAT);
        assertThat(view.grantedAt()).isEqualTo(NOW.toString());
        assertThat(view.expiresAt()).isEqualTo("2026-09-08T01:00:00Z");
        assertThat(view.active()).isTrue();
        assertThat(view.expiryFinalized()).isFalse();
        assertThat(repository.events).containsExactly(GuardianSharingConsentRepository.EventAction.GRANTED);
    }

    @Test
    void locationScopeIsRejectedWhileFeatureIsDisabled() {
        assertThatThrownBy(() -> service.grant(
                patient(),
                APPOINTMENT_ID,
                Set.of(APPOINTMENT, LOCATION),
                true))
                .isInstanceOf(GuardianSharingConsentException.class)
                .hasMessageContaining("비활성화");
    }

    @Test
    void attachmentScopeRequiresChatScope() {
        assertThatThrownBy(() -> service.grant(
                patient(),
                APPOINTMENT_ID,
                Set.of(APPOINTMENT, ATTACHMENT),
                true))
                .isInstanceOf(GuardianSharingConsentException.class)
                .hasMessageContaining("채팅 범위");
    }

    @Test
    void guardianCannotCreateOrRevokeConsent() {
        assertThatThrownBy(() -> service.grant(
                guardian(),
                APPOINTMENT_ID,
                Set.of(APPOINTMENT),
                true))
                .isInstanceOf(GuardianSharingConsentException.class)
                .extracting(exception -> ((GuardianSharingConsentException) exception).error())
                .isEqualTo("guardian_sharing_consent_patient_required");
    }

    @Test
    void patientRevocationIsImmediateIdempotentAndAuditedOnce() {
        service.grant(patient(), APPOINTMENT_ID, Set.of(APPOINTMENT), true);

        var revoked = service.revoke(patient(), APPOINTMENT_ID);
        var repeated = service.revoke(patient(), APPOINTMENT_ID);

        assertThat(revoked.active()).isFalse();
        assertThat(revoked.revokedAt()).isEqualTo(NOW.toString());
        assertThat(repeated.version()).isEqualTo(revoked.version());
        assertThat(repository.events).containsExactly(
                GuardianSharingConsentRepository.EventAction.GRANTED,
                GuardianSharingConsentRepository.EventAction.REVOKED);
    }

    @Test
    void adultPatientConfirmationIsRequiredBeforeConsentIsStored() {
        assertThatThrownBy(() -> service.grant(
                patient(),
                APPOINTMENT_ID,
                Set.of(APPOINTMENT),
                false))
                .isInstanceOf(GuardianSharingConsentException.class)
                .hasMessageContaining("성인 환자 본인 확인");

        assertThat(repository.current).isEmpty();
    }

    @Test
    void consentForPreviousGuardianIsNotReturnedAsCurrentConsent() {
        repository.current = Optional.of(new AdultPatientGuardianSharingPolicy.Grant(
                UUID.randomUUID(),
                APPOINTMENT_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                Set.of(APPOINTMENT),
                "adult-guardian-sharing-v1",
                PATIENT_ID,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                null,
                null,
                0));

        assertThatThrownBy(() -> service.get(patient(), APPOINTMENT_ID))
                .isInstanceOf(GuardianSharingConsentException.class)
                .extracting(exception -> ((GuardianSharingConsentException) exception).error())
                .isEqualTo("guardian_sharing_consent_not_found");
    }

    private AppUserRepository appUsers() {
        return new AppUserRepository() {
            @Override
            public Optional<AppUser> findByFirebaseUid(String firebaseUid) {
                return Optional.empty();
            }

            @Override
            public Optional<AppUser> findById(UUID id) {
                if (GUARDIAN_ID.equals(id)) {
                    return Optional.of(guardian());
                }
                if (PATIENT_ID.equals(id)) {
                    return Optional.of(patient());
                }
                return Optional.empty();
            }
        };
    }

    private AppUserRepository.AppUser patient() {
        return new AppUserRepository.AppUser(PATIENT_ID, "patient", AppUserRole.PATIENT);
    }

    private AppUserRepository.AppUser guardian() {
        return new AppUserRepository.AppUser(GUARDIAN_ID, "guardian", AppUserRole.GUARDIAN);
    }

    private static final class FakeRepository implements GuardianSharingConsentRepository {
        private Optional<AdultPatientGuardianSharingPolicy.Grant> current = Optional.empty();
        private final List<EventAction> events = new ArrayList<>();
        private boolean expiryFinalized;

        @Override
        public ConsentSettings getSettings() {
            return new ConsentSettings("adult-guardian-sharing-v1", false);
        }

        @Override
        public Optional<AppointmentContext> findAppointment(UUID appointmentRequestId) {
            if (!APPOINTMENT_ID.equals(appointmentRequestId)) {
                return Optional.empty();
            }
            return Optional.of(new AppointmentContext(
                    APPOINTMENT_ID,
                    PATIENT_ID,
                    GUARDIAN_ID,
                    APPOINTMENT_AT,
                    "REQUESTED"));
        }

        @Override
        public Optional<AdultPatientGuardianSharingPolicy.Grant> findByAppointmentId(
                UUID appointmentRequestId) {
            return current;
        }

        @Override
        public boolean isExpiryFinalized(UUID appointmentRequestId) {
            return expiryFinalized;
        }

        @Override
        public AdultPatientGuardianSharingPolicy.Grant grant(
                AdultPatientGuardianSharingPolicy.Grant requestedGrant) {
            AdultPatientGuardianSharingPolicy.Grant stored = current
                    .map(existing -> new AdultPatientGuardianSharingPolicy.Grant(
                            existing.id(),
                            requestedGrant.appointmentRequestId(),
                            requestedGrant.patientUserId(),
                            requestedGrant.guardianUserId(),
                            requestedGrant.scopes(),
                            requestedGrant.policyVersion(),
                            requestedGrant.grantedByUserId(),
                            requestedGrant.grantedAt(),
                            requestedGrant.expiresAt(),
                            null,
                            null,
                            existing.version() + 1))
                    .orElse(requestedGrant);
            current = Optional.of(stored);
            expiryFinalized = false;
            return stored;
        }

        @Override
        public Optional<AdultPatientGuardianSharingPolicy.Grant> revoke(
                UUID appointmentRequestId,
                UUID actorUserId,
                Instant revokedAt,
                long expectedVersion) {
            if (current.isEmpty() || current.orElseThrow().version() != expectedVersion) {
                return Optional.empty();
            }
            AdultPatientGuardianSharingPolicy.Grant revoked =
                    AdultPatientGuardianSharingPolicy.revokeByPatient(
                            current.orElseThrow(),
                            actorUserId,
                            AppUserRole.PATIENT,
                            revokedAt);
            current = Optional.of(revoked);
            return current;
        }

        @Override
        public void appendEvent(
                AdultPatientGuardianSharingPolicy.Grant grant,
                EventAction action,
                UUID actorUserId,
                Instant occurredAt) {
            events.add(action);
        }
    }
}
