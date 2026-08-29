package com.bodeul.core.consent;

import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;

interface GuardianSharingConsentService {

    ConsentView get(AppUserRepository.AppUser appUser, UUID appointmentRequestId);

    ConsentView grant(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes,
            boolean adultPatientConfirmed);

    ConsentView revoke(AppUserRepository.AppUser appUser, UUID appointmentRequestId);

    record ConsentView(
            UUID id,
            UUID appointmentRequestId,
            UUID patientUserId,
            UUID guardianUserId,
            Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes,
            String policyVersion,
            String adultSelfDeclaredAt,
            String grantedAt,
            String expiresAt,
            String revokedAt,
            boolean active,
            boolean expiryFinalized,
            boolean locationSharingAvailable,
            long version) {
    }
}
