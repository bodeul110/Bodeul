package com.bodeul.core.consent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface GuardianSharingConsentRepository {

    ConsentSettings getSettings();

    Optional<AppointmentContext> findAppointment(UUID appointmentRequestId);

    Optional<AdultPatientGuardianSharingPolicy.Grant> findByAppointmentId(
            UUID appointmentRequestId);

    default boolean isExpiryFinalized(UUID appointmentRequestId) {
        return true;
    }

    AdultPatientGuardianSharingPolicy.Grant grant(
            AdultPatientGuardianSharingPolicy.Grant requestedGrant);

    Optional<AdultPatientGuardianSharingPolicy.Grant> revoke(
            UUID appointmentRequestId,
            UUID actorUserId,
            Instant revokedAt,
            long expectedVersion);

    default void finalizeExpiryAfterCareBoundary(
            UUID appointmentRequestId,
            Instant careEndedAt) {
    }

    void appendEvent(
            AdultPatientGuardianSharingPolicy.Grant grant,
            EventAction action,
            UUID actorUserId,
            Instant occurredAt);

    enum EventAction {
        GRANTED,
        REVOKED
    }

    record ConsentSettings(String policyVersion, boolean locationSharingEnabled) {
    }

    record AppointmentContext(
            UUID id,
            UUID patientUserId,
            UUID guardianUserId,
            Instant appointmentAt,
            String status) {
    }
}
