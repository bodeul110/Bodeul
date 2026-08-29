package com.bodeul.core.consent;

import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;

public interface GuardianSharingConsentAccess {

    boolean isAllowed(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId,
            InformationScope scope);

    default Set<InformationScope> allowedScopes(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId) {
        EnumSet<InformationScope> allowed = EnumSet.noneOf(InformationScope.class);
        for (InformationScope scope : InformationScope.values()) {
            if (isAllowed(appUser, appointmentRequestId, patientUserId, guardianUserId, scope)) {
                allowed.add(scope);
            }
        }
        return Set.copyOf(allowed);
    }

    default boolean canReceiveCombinedRealtimeTopic(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId) {
        return appUser != null && appUser.role() != com.bodeul.core.auth.AppUserRole.GUARDIAN;
    }

    default void finalizeExpiryAfterCareBoundary(
            UUID appointmentRequestId,
            java.time.Instant careEndedAt) {
    }
}
