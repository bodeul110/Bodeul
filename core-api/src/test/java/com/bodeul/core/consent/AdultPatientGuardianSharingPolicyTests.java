package com.bodeul.core.consent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.Test;

import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.ALLOWED;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.APPOINTMENT_MISMATCH;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.EXPIRED;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.GRANT_MISSING;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.GUARDIAN_MISMATCH;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.NOT_YET_ACTIVE;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.PATIENT_MISMATCH;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.POLICY_VERSION_MISMATCH;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.REQUESTER_NOT_GUARDIAN;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.REVOKED;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.DecisionReason.SCOPE_NOT_GRANTED;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.APPOINTMENT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.ATTACHMENT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.CHAT;
import static com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope.REPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdultPatientGuardianSharingPolicyTests {

    private static final UUID PATIENT_ID = UUID.fromString("13aff496-9fd7-4d37-89ef-cd71582e16c9");
    private static final UUID GUARDIAN_ID = UUID.fromString("b99a3e7a-4819-4c70-846d-620f548bf4e1");
    private static final UUID OTHER_USER_ID = UUID.fromString("c56e5a24-c8c1-45e5-8991-d72068691ca7");
    private static final UUID APPOINTMENT_ID = UUID.fromString("92b1b160-e805-4c22-b5b4-d6427259e446");
    private static final Instant GRANTED_AT = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant EXPIRES_AT = GRANTED_AT.plus(30, ChronoUnit.DAYS);
    private static final String CURRENT_POLICY_VERSION = "test-policy-v1";

    @Test
    void patientCreatesExplicitScopedGrant() {
        var grant = grant();

        assertThat(grant.patientUserId()).isEqualTo(PATIENT_ID);
        assertThat(grant.guardianUserId()).isEqualTo(GUARDIAN_ID);
        assertThat(grant.scopes()).containsExactlyInAnyOrder(APPOINTMENT, REPORT);
        assertThat(grant.policyVersion()).isEqualTo("test-policy-v1");
        assertThat(grant.grantedByUserId()).isEqualTo(PATIENT_ID);
        assertThat(grant.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(grant.revokedAt()).isNull();
        assertThat(grant.version()).isZero();
    }

    @Test
    void grantRequiresPatientSelfGuardianRoleScopeWindowAndPolicyVersion() {
        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                OTHER_USER_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(APPOINTMENT),
                GRANTED_AT,
                EXPIRES_AT,
                "p0-01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(ATTACHMENT),
                GRANTED_AT,
                EXPIRES_AT,
                "p0-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("채팅 공유 동의");

        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.PATIENT,
                Set.of(APPOINTMENT),
                GRANTED_AT,
                EXPIRES_AT,
                "p0-01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(),
                GRANTED_AT,
                EXPIRES_AT,
                "p0-01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(APPOINTMENT),
                GRANTED_AT,
                GRANTED_AT,
                "p0-01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(APPOINTMENT),
                GRANTED_AT,
                EXPIRES_AT,
                "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchingGuardianCanReadOnlyGrantedScopeInsideWindow() {
        var decision = evaluate(grant(), GUARDIAN_ID, AppUserRole.GUARDIAN, PATIENT_ID, REPORT,
                GRANTED_AT.plusSeconds(1));
        var missingScope = evaluate(grant(), GUARDIAN_ID, AppUserRole.GUARDIAN, PATIENT_ID, CHAT,
                GRANTED_AT.plusSeconds(1));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(ALLOWED);
        assertThat(missingScope.allowed()).isFalse();
        assertThat(missingScope.reason()).isEqualTo(SCOPE_NOT_GRANTED);
    }

    @Test
    void missingOrMismatchedGrantFailsClosed() {
        var missing = AdultPatientGuardianSharingPolicy.evaluate(
                Optional.empty(),
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                APPOINTMENT_ID,
                PATIENT_ID,
                REPORT,
                CURRENT_POLICY_VERSION,
                GRANTED_AT.plusSeconds(1));
        var wrongRole = evaluate(grant(), GUARDIAN_ID, AppUserRole.PATIENT, PATIENT_ID, REPORT,
                GRANTED_AT.plusSeconds(1));
        var wrongAppointment = AdultPatientGuardianSharingPolicy.evaluate(
                Optional.of(grant()),
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                UUID.randomUUID(),
                PATIENT_ID,
                REPORT,
                CURRENT_POLICY_VERSION,
                GRANTED_AT.plusSeconds(1));
        var wrongPatient = evaluate(grant(), GUARDIAN_ID, AppUserRole.GUARDIAN, OTHER_USER_ID, REPORT,
                GRANTED_AT.plusSeconds(1));
        var wrongGuardian = evaluate(grant(), OTHER_USER_ID, AppUserRole.GUARDIAN, PATIENT_ID, REPORT,
                GRANTED_AT.plusSeconds(1));

        assertThat(missing.reason()).isEqualTo(GRANT_MISSING);
        assertThat(wrongRole.reason()).isEqualTo(REQUESTER_NOT_GUARDIAN);
        assertThat(wrongAppointment.reason()).isEqualTo(APPOINTMENT_MISMATCH);
        assertThat(wrongPatient.reason()).isEqualTo(PATIENT_MISMATCH);
        assertThat(wrongGuardian.reason()).isEqualTo(GUARDIAN_MISMATCH);
    }

    @Test
    void grantPolicyVersionMustMatchCurrentPolicyVersion() {
        var mismatch = AdultPatientGuardianSharingPolicy.evaluate(
                Optional.of(grant()),
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                APPOINTMENT_ID,
                PATIENT_ID,
                REPORT,
                "test-policy-v2",
                GRANTED_AT.plusSeconds(1));

        assertThat(mismatch.allowed()).isFalse();
        assertThat(mismatch.reason()).isEqualTo(POLICY_VERSION_MISMATCH);
    }

    @Test
    void evaluationRequiresCurrentPolicyVersion() {
        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.evaluate(
                Optional.of(grant()),
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                APPOINTMENT_ID,
                PATIENT_ID,
                REPORT,
                "  ",
                GRANTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("동의 정책 버전이 필요합니다.");
    }

    @Test
    void accessIsDeniedBeforeStartAndAtExpiry() {
        var before = evaluate(grant(), GUARDIAN_ID, AppUserRole.GUARDIAN, PATIENT_ID, REPORT,
                GRANTED_AT.minusMillis(1));
        var expired = evaluate(grant(), GUARDIAN_ID, AppUserRole.GUARDIAN, PATIENT_ID, REPORT,
                EXPIRES_AT);

        assertThat(before.reason()).isEqualTo(NOT_YET_ACTIVE);
        assertThat(expired.reason()).isEqualTo(EXPIRED);
    }

    @Test
    void patientRevocationIsImmediateAndIdempotent() {
        Instant revokedAt = GRANTED_AT.plus(1, ChronoUnit.DAYS);
        var revoked = AdultPatientGuardianSharingPolicy.revokeByPatient(
                grant(),
                PATIENT_ID,
                AppUserRole.PATIENT,
                revokedAt);
        var repeated = AdultPatientGuardianSharingPolicy.revokeByPatient(
                revoked,
                PATIENT_ID,
                AppUserRole.PATIENT,
                revokedAt.plusSeconds(10));

        assertThat(revoked.revokedByUserId()).isEqualTo(PATIENT_ID);
        assertThat(revoked.revokedAt()).isEqualTo(revokedAt);
        assertThat(revoked.version()).isEqualTo(1);
        assertThat(repeated).isSameAs(revoked);
        assertThat(evaluate(revoked, GUARDIAN_ID, AppUserRole.GUARDIAN, PATIENT_ID, REPORT, revokedAt)
                .reason()).isEqualTo(REVOKED);
    }

    @Test
    void guardianCannotRevokePatientConsent() {
        assertThatThrownBy(() -> AdultPatientGuardianSharingPolicy.revokeByPatient(
                grant(),
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                GRANTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AdultPatientGuardianSharingPolicy.Grant grant() {
        return AdultPatientGuardianSharingPolicy.grantByPatient(
                PATIENT_ID,
                AppUserRole.PATIENT,
                APPOINTMENT_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                Set.of(APPOINTMENT, REPORT),
                GRANTED_AT,
                EXPIRES_AT,
                CURRENT_POLICY_VERSION);
    }

    private AdultPatientGuardianSharingPolicy.Decision evaluate(
            AdultPatientGuardianSharingPolicy.Grant grant,
            UUID requesterUserId,
            AppUserRole requesterRole,
            UUID patientUserId,
            AdultPatientGuardianSharingPolicy.InformationScope scope,
            Instant requestedAt) {
        return AdultPatientGuardianSharingPolicy.evaluate(
                Optional.of(grant),
                requesterUserId,
                requesterRole,
                APPOINTMENT_ID,
                patientUserId,
                scope,
                CURRENT_POLICY_VERSION,
                requestedAt);
    }
}
