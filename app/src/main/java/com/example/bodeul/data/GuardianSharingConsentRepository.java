package com.example.bodeul.data;

import com.example.bodeul.domain.model.GuardianSharingConsent;
import com.example.bodeul.domain.model.GuardianSharingConsentScope;

import java.util.Set;

public interface GuardianSharingConsentRepository {
    void getConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    );

    void grantConsent(
            String appointmentRequestId,
            Set<GuardianSharingConsentScope> scopes,
            boolean adultPatientConfirmed,
            RepositoryCallback<GuardianSharingConsent> callback
    );

    void revokeConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    );
}
