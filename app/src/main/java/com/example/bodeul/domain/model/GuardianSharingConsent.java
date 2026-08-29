package com.example.bodeul.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class GuardianSharingConsent {
    private final String appointmentRequestId;
    private final Set<GuardianSharingConsentScope> scopes;
    private final String policyVersion;
    private final String grantedAt;
    private final String expiresAt;
    private final String revokedAt;
    private final boolean active;
    private final boolean expiryFinalized;
    private final boolean locationSharingAvailable;

    public GuardianSharingConsent(
            String appointmentRequestId,
            Set<GuardianSharingConsentScope> scopes,
            String policyVersion,
            String grantedAt,
            String expiresAt,
            String revokedAt,
            boolean active,
            boolean expiryFinalized,
            boolean locationSharingAvailable
    ) {
        this.appointmentRequestId = appointmentRequestId == null ? "" : appointmentRequestId;
        this.scopes = scopes == null || scopes.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(scopes));
        this.policyVersion = policyVersion == null ? "" : policyVersion;
        this.grantedAt = grantedAt == null ? "" : grantedAt;
        this.expiresAt = expiresAt == null ? "" : expiresAt;
        this.revokedAt = revokedAt == null ? "" : revokedAt;
        this.active = active;
        this.expiryFinalized = expiryFinalized;
        this.locationSharingAvailable = locationSharingAvailable;
    }

    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    public Set<GuardianSharingConsentScope> getScopes() {
        return scopes;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getGrantedAt() {
        return grantedAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public String getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isExpiryFinalized() {
        return expiryFinalized;
    }

    public boolean isLocationSharingAvailable() {
        return locationSharingAvailable;
    }
}
