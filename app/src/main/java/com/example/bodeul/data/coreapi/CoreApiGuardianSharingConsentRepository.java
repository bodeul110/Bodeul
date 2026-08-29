package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.GuardianSharingConsentRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianSharingConsent;
import com.example.bodeul.domain.model.GuardianSharingConsentScope;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumSet;
import java.util.Set;

public final class CoreApiGuardianSharingConsentRepository
        implements GuardianSharingConsentRepository {
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiAuthenticatedClient authenticatedClient;

    public CoreApiGuardianSharingConsentRepository(Context context) {
        Context appContext = context.getApplicationContext();
        appointmentClient = new CoreApiAppointmentClient(appContext);
        authenticatedClient = new CoreApiAuthenticatedClient(appContext);
    }

    @Override
    public void getConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        execute(
                appointmentRequestId,
                "GET",
                null,
                callback,
                "저장된 보호자 정보공유 동의를 불러오지 못했습니다."
        );
    }

    @Override
    public void grantConsent(
            String appointmentRequestId,
            Set<GuardianSharingConsentScope> scopes,
            boolean adultPatientConfirmed,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        JSONObject body = new JSONObject();
        try {
            JSONArray values = new JSONArray();
            if (scopes != null) {
                scopes.stream().map(Enum::name).sorted().forEach(values::put);
            }
            body.put("scopes", values);
            body.put("adultPatientConfirmed", adultPatientConfirmed);
        } catch (JSONException exception) {
            authenticatedClient.postError(callback, "정보공유 범위를 확인해 주세요.");
            return;
        }
        execute(
                appointmentRequestId,
                "PUT",
                body,
                callback,
                "보호자 정보공유 동의를 저장하지 못했습니다."
        );
    }

    @Override
    public void revokeConsent(
            String appointmentRequestId,
            RepositoryCallback<GuardianSharingConsent> callback
    ) {
        execute(
                appointmentRequestId,
                "DELETE",
                null,
                callback,
                "보호자 정보공유 동의를 철회하지 못했습니다."
        );
    }

    private void execute(
            String appointmentRequestId,
            String method,
            @Nullable JSONObject body,
            RepositoryCallback<GuardianSharingConsent> callback,
            String fallbackMessage
    ) {
        appointmentClient.resolveCoreId(
                appointmentRequestId,
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String coreId) {
                        authenticatedClient.execute(
                                (idToken, appCheckToken) -> parse(authenticatedClient.requestJson(
                                        method,
                                        "/api/appointments/" + coreId
                                                + "/guardian-sharing-consent",
                                        body,
                                        idToken,
                                        appCheckToken)),
                                callback,
                                fallbackMessage,
                                "보호자 정보공유 동의 API"
                        );
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }

    private GuardianSharingConsent parse(JSONObject response) throws JSONException {
        EnumSet<GuardianSharingConsentScope> scopes = EnumSet.noneOf(
                GuardianSharingConsentScope.class);
        JSONArray values = response.optJSONArray("scopes");
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                scopes.add(GuardianSharingConsentScope.valueOf(values.getString(index)));
            }
        }
        return new GuardianSharingConsent(
                response.optString("appointmentRequestId", ""),
                scopes,
                response.optString("policyVersion", ""),
                response.optString("grantedAt", ""),
                response.optString("expiresAt", ""),
                response.optString("revokedAt", ""),
                response.optBoolean("active", false),
                response.optBoolean("expiryFinalized", false),
                response.optBoolean("locationSharingAvailable", false)
        );
    }
}
