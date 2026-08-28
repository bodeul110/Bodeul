package com.bodeul.core.consent;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

/**
 * 성인 환자가 직접 부여한 보호자 정보공유 동의를 판정하는 순수 도메인 계약이다.
 * 호출 계층은 이 계약을 사용하기 전에 환자의 성인 여부를 별도로 확인해야 한다.
 */
public final class AdultPatientGuardianSharingPolicy {

    private AdultPatientGuardianSharingPolicy() {
    }

    public static Grant grantByPatient(
            UUID actorUserId,
            AppUserRole actorRole,
            UUID patientUserId,
            UUID guardianUserId,
            AppUserRole guardianRole,
            Set<InformationScope> scopes,
            Instant grantedAt,
            Instant expiresAt,
            String policyVersion) {
        requirePatientActor(actorUserId, actorRole, patientUserId);
        Objects.requireNonNull(guardianUserId, "보호자 식별자가 필요합니다.");
        if (guardianRole != AppUserRole.GUARDIAN) {
            throw new IllegalArgumentException("보호자 역할 계정만 정보공유 대상으로 지정할 수 있습니다.");
        }
        if (patientUserId.equals(guardianUserId)) {
            throw new IllegalArgumentException("환자 본인을 보호자로 지정할 수 없습니다.");
        }

        return new Grant(
                UUID.randomUUID(),
                patientUserId,
                guardianUserId,
                scopes,
                normalizePolicyVersion(policyVersion),
                actorUserId,
                grantedAt,
                expiresAt,
                null,
                null,
                0);
    }

    public static Grant revokeByPatient(
            Grant grant,
            UUID actorUserId,
            AppUserRole actorRole,
            Instant revokedAt) {
        Objects.requireNonNull(grant, "철회할 동의가 필요합니다.");
        requirePatientActor(actorUserId, actorRole, grant.patientUserId());
        Objects.requireNonNull(revokedAt, "철회 시각이 필요합니다.");
        if (revokedAt.isBefore(grant.grantedAt())) {
            throw new IllegalArgumentException("철회 시각은 동의 시각보다 빠를 수 없습니다.");
        }
        if (grant.revokedAt() != null) {
            return grant;
        }

        return new Grant(
                grant.id(),
                grant.patientUserId(),
                grant.guardianUserId(),
                grant.scopes(),
                grant.policyVersion(),
                grant.grantedByUserId(),
                grant.grantedAt(),
                grant.expiresAt(),
                actorUserId,
                revokedAt,
                grant.version() + 1);
    }

    public static Decision evaluate(
            Optional<Grant> candidate,
            UUID requesterUserId,
            AppUserRole requesterRole,
            UUID patientUserId,
            InformationScope scope,
            Instant requestedAt) {
        Objects.requireNonNull(candidate, "동의 조회 결과가 필요합니다.");
        Objects.requireNonNull(requesterUserId, "요청자 식별자가 필요합니다.");
        Objects.requireNonNull(requesterRole, "요청자 역할이 필요합니다.");
        Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
        Objects.requireNonNull(scope, "정보 범위가 필요합니다.");
        Objects.requireNonNull(requestedAt, "조회 시각이 필요합니다.");

        if (candidate.isEmpty()) {
            return Decision.denied(DecisionReason.GRANT_MISSING);
        }

        Grant grant = candidate.orElseThrow();
        if (requesterRole != AppUserRole.GUARDIAN) {
            return Decision.denied(DecisionReason.REQUESTER_NOT_GUARDIAN);
        }
        if (!grant.patientUserId().equals(patientUserId)) {
            return Decision.denied(DecisionReason.PATIENT_MISMATCH);
        }
        if (!grant.guardianUserId().equals(requesterUserId)) {
            return Decision.denied(DecisionReason.GUARDIAN_MISMATCH);
        }
        if (!grant.scopes().contains(scope)) {
            return Decision.denied(DecisionReason.SCOPE_NOT_GRANTED);
        }
        if (requestedAt.isBefore(grant.grantedAt())) {
            return Decision.denied(DecisionReason.NOT_YET_ACTIVE);
        }
        if (grant.revokedAt() != null && !requestedAt.isBefore(grant.revokedAt())) {
            return Decision.denied(DecisionReason.REVOKED);
        }
        if (!requestedAt.isBefore(grant.expiresAt())) {
            return Decision.denied(DecisionReason.EXPIRED);
        }
        return Decision.allow();
    }

    private static void requirePatientActor(
            UUID actorUserId,
            AppUserRole actorRole,
            UUID patientUserId) {
        Objects.requireNonNull(actorUserId, "행위자 식별자가 필요합니다.");
        Objects.requireNonNull(actorRole, "행위자 역할이 필요합니다.");
        Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
        if (actorRole != AppUserRole.PATIENT || !actorUserId.equals(patientUserId)) {
            throw new IllegalArgumentException("성인 환자 본인만 정보공유 동의를 변경할 수 있습니다.");
        }
    }

    private static String normalizePolicyVersion(String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("동의 정책 버전이 필요합니다.");
        }
        return policyVersion.trim();
    }

    public enum InformationScope {
        APPOINTMENT,
        LOCATION,
        CHAT,
        ATTACHMENT,
        REPORT
    }

    public enum DecisionReason {
        ALLOWED,
        GRANT_MISSING,
        REQUESTER_NOT_GUARDIAN,
        PATIENT_MISMATCH,
        GUARDIAN_MISMATCH,
        SCOPE_NOT_GRANTED,
        NOT_YET_ACTIVE,
        EXPIRED,
        REVOKED
    }

    public record Decision(boolean allowed, DecisionReason reason) {

        public Decision {
            Objects.requireNonNull(reason, "판정 사유가 필요합니다.");
            if (allowed != (reason == DecisionReason.ALLOWED)) {
                throw new IllegalArgumentException("허용 여부와 판정 사유가 일치하지 않습니다.");
            }
        }

        private static Decision allow() {
            return new Decision(true, DecisionReason.ALLOWED);
        }

        private static Decision denied(DecisionReason reason) {
            return new Decision(false, reason);
        }
    }

    public record Grant(
            UUID id,
            UUID patientUserId,
            UUID guardianUserId,
            Set<InformationScope> scopes,
            String policyVersion,
            UUID grantedByUserId,
            Instant grantedAt,
            Instant expiresAt,
            UUID revokedByUserId,
            Instant revokedAt,
            long version) {

        public Grant {
            Objects.requireNonNull(id, "동의 식별자가 필요합니다.");
            Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
            Objects.requireNonNull(guardianUserId, "보호자 식별자가 필요합니다.");
            if (patientUserId.equals(guardianUserId)) {
                throw new IllegalArgumentException("환자 본인을 보호자로 지정할 수 없습니다.");
            }
            Objects.requireNonNull(scopes, "정보 범위가 필요합니다.");
            scopes = Set.copyOf(scopes);
            if (scopes.isEmpty()) {
                throw new IllegalArgumentException("하나 이상의 정보 범위를 선택해야 합니다.");
            }
            policyVersion = normalizePolicyVersion(policyVersion);
            Objects.requireNonNull(grantedByUserId, "동의 행위자 식별자가 필요합니다.");
            if (!patientUserId.equals(grantedByUserId)) {
                throw new IllegalArgumentException("환자 본인의 동의만 기록할 수 있습니다.");
            }
            Objects.requireNonNull(grantedAt, "동의 시각이 필요합니다.");
            Objects.requireNonNull(expiresAt, "동의 만료 시각이 필요합니다.");
            if (!expiresAt.isAfter(grantedAt)) {
                throw new IllegalArgumentException("동의 만료 시각은 동의 시각보다 늦어야 합니다.");
            }
            if ((revokedByUserId == null) != (revokedAt == null)) {
                throw new IllegalArgumentException("철회 행위자와 철회 시각은 함께 기록해야 합니다.");
            }
            if (revokedByUserId != null && !patientUserId.equals(revokedByUserId)) {
                throw new IllegalArgumentException("환자 본인의 철회만 기록할 수 있습니다.");
            }
            if (revokedAt != null && revokedAt.isBefore(grantedAt)) {
                throw new IllegalArgumentException("철회 시각은 동의 시각보다 빠를 수 없습니다.");
            }
            if (version < 0) {
                throw new IllegalArgumentException("동의 버전은 0 이상이어야 합니다.");
            }
        }
    }
}
