package com.example.bodeul.data.mock;

import android.os.Handler;
import android.os.Looper;

import com.example.bodeul.data.GuardianSharingConsentRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianSharingConsent;
import com.example.bodeul.domain.model.GuardianSharingConsentScope;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MockGuardianSharingConsentRepository
        implements GuardianSharingConsentRepository {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, GuardianSharingConsent> consents =
            new ConcurrentHashMap<>();

    @Override
    public void getConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        GuardianSharingConsent consent = consents.get(normalize(appointmentRequestId));
        if (consent == null) {
            mainHandler.post(() -> callback.onError("아직 저장된 정보공유 동의가 없습니다."));
            return;
        }
        mainHandler.post(() -> callback.onSuccess(consent));
    }

    @Override
    public void grantConsent(
            String appointmentRequestId,
            Set<GuardianSharingConsentScope> scopes,
            boolean adultPatientConfirmed,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        if (!adultPatientConfirmed) {
            callback.onError("성인 환자 본인 확인이 필요합니다.");
            return;
        }
        String id = normalize(appointmentRequestId);
        GuardianSharingConsent consent = new GuardianSharingConsent(
                id,
                scopes,
                "mock-adult-guardian-sharing-v1",
                "데모 동의 시각",
                "예약 예정 시각 또는 동의 시각 중 늦은 시점부터 7일 후",
                "",
                true,
                false,
                false
        );
        consents.put(id, consent);
        mainHandler.post(() -> callback.onSuccess(consent));
    }

    @Override
    public void revokeConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        String id = normalize(appointmentRequestId);
        GuardianSharingConsent current = consents.get(id);
        if (current == null) {
            mainHandler.post(() -> callback.onError("철회할 정보공유 동의가 없습니다."));
            return;
        }
        GuardianSharingConsent revoked = new GuardianSharingConsent(
                id,
                current.getScopes(),
                current.getPolicyVersion(),
                current.getGrantedAt(),
                current.getExpiresAt(),
                "데모 철회 시각",
                false,
                current.isExpiryFinalized(),
                false
        );
        consents.put(id, revoked);
        mainHandler.post(() -> callback.onSuccess(revoked));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
