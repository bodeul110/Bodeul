package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("database")
public class GuardianSharingConsentAuthorizer implements GuardianSharingConsentAccess {

    private final GuardianSharingConsentRepository consentRepository;
    private final Clock clock;

    @Autowired
    GuardianSharingConsentAuthorizer(
            GuardianSharingConsentRepository consentRepository) {
        this(consentRepository, Clock.systemUTC());
    }

    GuardianSharingConsentAuthorizer(
            GuardianSharingConsentRepository consentRepository,
            Clock clock) {
        this.consentRepository = consentRepository;
        this.clock = clock;
    }

    @Override
    public boolean isAllowed(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId,
            InformationScope scope) {
        return scope != null && allowedScopes(
                appUser,
                appointmentRequestId,
                patientUserId,
                guardianUserId).contains(scope);
    }

    @Override
    public Set<InformationScope> allowedScopes(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId) {
        if (appUser == null) {
            return Set.of();
        }
        if (appUser.role() != AppUserRole.GUARDIAN) {
            return Set.of(InformationScope.values());
        }
        if (guardianUserId == null || !appUser.id().equals(guardianUserId)) {
            return Set.of();
        }
        Optional<AdultPatientGuardianSharingPolicy.Grant> grant =
                consentRepository.findByAppointmentId(appointmentRequestId);
        Instant requestedAt = clock.instant();
        if (grant.isPresent()
                && !consentRepository.isExpiryFinalized(appointmentRequestId)) {
            grant = Optional.of(withProvisionalExpiry(grant.orElseThrow(), requestedAt));
        }
        GuardianSharingConsentRepository.ConsentSettings settings =
                consentRepository.getSettings();
        EnumSet<InformationScope> allowed = EnumSet.noneOf(InformationScope.class);
        for (InformationScope scope : InformationScope.values()) {
            if (scope == InformationScope.LOCATION && !settings.locationSharingEnabled()) {
                continue;
            }
            if (AdultPatientGuardianSharingPolicy.evaluate(
                            grant,
                            appUser.id(),
                            appUser.role(),
                            appointmentRequestId,
                            patientUserId,
                            scope,
                            settings.policyVersion(),
                            requestedAt)
                    .allowed()) {
                allowed.add(scope);
            }
        }
        return Set.copyOf(allowed);
    }

    @Override
    public void finalizeExpiryAfterCareBoundary(
            UUID appointmentRequestId,
            Instant careEndedAt) {
        if (appointmentRequestId == null || careEndedAt == null) {
            return;
        }
        consentRepository.finalizeExpiryAfterCareBoundary(appointmentRequestId, careEndedAt);
    }

    private AdultPatientGuardianSharingPolicy.Grant withProvisionalExpiry(
            AdultPatientGuardianSharingPolicy.Grant grant,
            Instant requestedAt) {
        Instant effectiveExpiry = grant.expiresAt().isAfter(requestedAt)
                ? grant.expiresAt()
                : requestedAt.plusSeconds(1);
        return new AdultPatientGuardianSharingPolicy.Grant(
                grant.id(),
                grant.appointmentRequestId(),
                grant.patientUserId(),
                grant.guardianUserId(),
                grant.scopes(),
                grant.policyVersion(),
                grant.grantedByUserId(),
                grant.grantedAt(),
                effectiveExpiry,
                grant.revokedByUserId(),
                grant.revokedAt(),
                grant.version());
    }

}
