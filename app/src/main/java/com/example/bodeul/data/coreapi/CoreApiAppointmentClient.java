package com.example.bodeul.data.coreapi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firebase 인증 토큰으로 Core API 예약 경계를 호출한다.
 */
final class CoreApiAppointmentClient {
    private static final String TAG = "BodeulAppointmentApi";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String coreApiBaseUrl;
    private final Map<String, AppointmentReference> references = new ConcurrentHashMap<>();
    private final Map<String, UUID> pendingCreateRequestIds = new ConcurrentHashMap<>();

    CoreApiAppointmentClient(Context context) {
        coreApiBaseUrl = normalizeBaseUrl(
                context.getApplicationContext().getString(R.string.bodeul_core_api_base_url));
    }

    void getAppointments(RepositoryCallback<List<AppointmentRequest>> callback) {
        executeAuthenticated(
                (idToken, appCheckToken) -> {
                    JSONObject response = requestJson(
                            "GET",
                            "/api/appointments",
                            null,
                            idToken,
                            appCheckToken);
                    JSONArray appointments = response.optJSONArray("appointments");
                    List<AppointmentRequest> result = new ArrayList<>();
                    if (appointments == null) {
                        return result;
                    }
                    for (int index = 0; index < appointments.length(); index++) {
                        JSONObject item = appointments.optJSONObject(index);
                        if (item != null) {
                            result.add(parseAndRemember(item).request);
                        }
                    }
                    return result;
                },
                callback,
                "예약 목록을 불러오지 못했습니다."
        );
    }

    void getAppointment(
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> parseAndRemember(requestJson(
                                "GET",
                                "/api/appointments/" + reference.coreId,
                                null,
                                idToken,
                                appCheckToken)).request,
                        callback,
                        "예약 상세 정보를 불러오지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void createAppointment(
            BookingRequestDraft draft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        String draftPayload;
        try {
            draftPayload = buildDraftBody(draft).toString();
        } catch (JSONException exception) {
            postError(callback, "예약 입력값을 확인해 주세요.");
            return;
        }
        UUID clientRequestId = pendingCreateRequestIds.computeIfAbsent(
                draftPayload,
                ignored -> UUID.randomUUID());
        executeAuthenticated(
                (idToken, appCheckToken) -> {
                    JSONObject body = new JSONObject(draftPayload);
                    body.put("clientRequestId", clientRequestId.toString());
                    JSONObject response;
                    try {
                        response = requestJson(
                                "POST",
                                "/api/appointments",
                                body,
                                idToken,
                                appCheckToken);
                    } catch (IOException firstFailure) {
                        response = requestJson(
                                "POST",
                                "/api/appointments",
                                body,
                                idToken,
                                appCheckToken);
                    }
                    return parseAndRemember(response).request;
                },
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        pendingCreateRequestIds.remove(draftPayload, clientRequestId);
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                },
                "동행 요청을 저장하지 못했습니다."
        );
    }

    void updateAppointment(
            String requestId,
            BookingRequestDraft draft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> {
                            ApiAppointment current = parseAndRemember(requestJson(
                                    "GET",
                                    "/api/appointments/" + reference.coreId,
                                    null,
                                    idToken,
                                    appCheckToken));
                            JSONObject body = buildDraftBody(draft);
                            body.put("version", current.reference.version);
                            return parseAndRemember(requestJson(
                                    "PUT",
                                    "/api/appointments/" + current.reference.coreId,
                                    body,
                                    idToken,
                                    appCheckToken)).request;
                        },
                        callback,
                        "동행 요청을 수정하지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void cancelAppointment(
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> {
                            ApiAppointment current = parseAndRemember(requestJson(
                                    "GET",
                                    "/api/appointments/" + reference.coreId,
                                    null,
                                    idToken,
                                    appCheckToken));
                            JSONObject body = new JSONObject();
                            body.put("version", current.reference.version);
                            return parseAndRemember(requestJson(
                                    "POST",
                                    "/api/appointments/" + current.reference.coreId + "/cancel",
                                    body,
                                    idToken,
                                    appCheckToken)).request;
                        },
                        callback,
                        "동행 요청을 취소하지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void resolveLegacyFirestoreId(
            String requestId,
            RepositoryCallback<String> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                callback.onSuccess(reference.legacyFirestoreId);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    String getKnownLegacyFirestoreId(String requestId) {
        AppointmentReference reference = references.get(normalizeText(requestId));
        return reference == null ? "" : reference.legacyFirestoreId;
    }

    private void resolveReference(
            String requestId,
            RepositoryCallback<AppointmentReference> callback
    ) {
        String normalizedRequestId = normalizeText(requestId);
        AppointmentReference known = references.get(normalizedRequestId);
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        if (isUuid(normalizedRequestId)) {
            AppointmentReference direct = new AppointmentReference(
                    normalizedRequestId,
                    "",
                    0L);
            references.put(normalizedRequestId, direct);
            callback.onSuccess(direct);
            return;
        }

        getAppointments(new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                AppointmentReference resolved = references.get(normalizedRequestId);
                if (resolved == null) {
                    callback.onError("예약 정보를 찾지 못했습니다.");
                    return;
                }
                callback.onSuccess(resolved);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private JSONObject buildDraftBody(BookingRequestDraft draft) throws JSONException {
        if (draft == null) {
            throw new JSONException("예약 입력값이 없습니다.");
        }
        JSONObject body = new JSONObject();
        body.put("linkedParticipantName", draft.getLinkedParticipantName());
        body.put("linkedParticipantPhone", draft.getLinkedParticipantPhone());
        body.put("linkedParticipantEmail", draft.getLinkedParticipantEmail());
        body.put("patientConditionSummary", draft.getPatientConditionSummary());
        body.put("medicationSummary", draft.getMedicationSummary());
        body.put("hospitalName", draft.getHospitalName());
        body.put("departmentName", draft.getDepartmentName());
        body.put("hospitalLatitude", draft.getHospitalLatitude());
        body.put("hospitalLongitude", draft.getHospitalLongitude());
        body.put("appointmentAt", draft.getAppointmentAt());
        body.put("meetingPlace", draft.getMeetingPlace());
        body.put("specialNotes", draft.getSpecialNotes());
        body.put("mobilitySupportCode", draft.getMobilitySupport().name());
        body.put("tripTypeCode", draft.getTripType().name());
        body.put("managerGenderPreferenceCode", draft.getManagerGenderPreference().name());
        body.put("paymentMethodCode", draft.getPaymentMethod().name());
        body.put("couponCode", draft.getCouponType().name());
        return body;
    }

    private ApiAppointment parseAndRemember(JSONObject item) throws JSONException {
        String coreId = requireText(item, "id");
        String legacyFirestoreId = optText(item, "legacyFirestoreId");
        long version = item.getLong("version");
        String externalId = legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId;
        AppointmentStatus status;
        try {
            status = AppointmentStatus.valueOf(requireText(item, "status"));
        } catch (IllegalArgumentException exception) {
            throw new JSONException("알 수 없는 예약 상태입니다.");
        }

        AppointmentRequest request = new AppointmentRequest(
                externalId,
                optText(item, "patientUserId"),
                optText(item, "guardianUserId"),
                optText(item, "hospitalName"),
                optText(item, "departmentName"),
                item.optDouble("hospitalLatitude", 0d),
                item.optDouble("hospitalLongitude", 0d),
                optText(item, "appointmentAt"),
                optText(item, "meetingPlace"),
                optText(item, "specialNotes"),
                status,
                optText(item, "managerUserId"),
                optText(item, "patientName"),
                optText(item, "patientPhone"),
                optText(item, "patientEmail"),
                optText(item, "guardianName"),
                optText(item, "guardianPhone"),
                optText(item, "guardianEmail"),
                optText(item, "patientConditionSummary"),
                optText(item, "medicationSummary"),
                optText(item, "mobilitySupportCode"),
                optText(item, "tripTypeCode"),
                optText(item, "managerGenderPreferenceCode"),
                optText(item, "paymentMethodCode"),
                optText(item, "couponCode"),
                item.optInt("basePrice", 0),
                item.optInt("optionSurchargePrice", 0),
                item.optInt("couponDiscountPrice", 0),
                item.optInt("finalPrice", 0),
                optText(item, "paymentStatusCode"),
                optText(item, "paymentApprovalCode"),
                optText(item, "paymentApprovedAt"),
                optText(item, "paymentProviderLabel")
        );
        AppointmentReference reference = new AppointmentReference(
                coreId,
                legacyFirestoreId,
                version);
        references.put(coreId, reference);
        references.put(externalId, reference);
        if (!legacyFirestoreId.isEmpty()) {
            references.put(legacyFirestoreId, reference);
        }
        return new ApiAppointment(request, reference);
    }

    private <T> void executeAuthenticated(
            AuthenticatedOperation<T> operation,
            RepositoryCallback<T> callback,
            String fallbackMessage
    ) {
        if (coreApiBaseUrl.isEmpty()) {
            postError(callback, "Core API 주소가 설정되지 않았습니다.");
            return;
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            postError(callback, "로그인 정보가 없습니다. 다시 로그인해 주세요.");
            return;
        }

        currentUser.getIdToken(false).addOnCompleteListener(idTokenTask -> {
            String idToken = idTokenTask.isSuccessful() && idTokenTask.getResult() != null
                    ? idTokenTask.getResult().getToken()
                    : "";
            if (TextUtils.isEmpty(idToken)) {
                postError(callback, "인증 정보를 가져오지 못했습니다. 다시 로그인해 주세요.");
                return;
            }

            FirebaseAppCheck.getInstance().getAppCheckToken(false).addOnCompleteListener(appCheckTask -> {
                AppCheckToken tokenResult = appCheckTask.isSuccessful()
                        ? appCheckTask.getResult()
                        : null;
                String appCheckToken = tokenResult == null ? "" : tokenResult.getToken();
                EXECUTOR.execute(() -> {
                    try {
                        T result = operation.run(idToken, appCheckToken);
                        mainHandler.post(() -> callback.onSuccess(result));
                    } catch (ApiException exception) {
                        Log.w(TAG, "Core API 예약 요청 실패: HTTP " + exception.statusCode);
                        postError(callback, exception.userMessage);
                    } catch (Exception exception) {
                        Log.w(TAG, "Core API 예약 요청 실패: " + exception.getClass().getSimpleName());
                        postError(callback, fallbackMessage);
                    }
                });
            });
        });
    }

    private JSONObject requestJson(
            String method,
            String path,
            @Nullable JSONObject body,
            String idToken,
            @Nullable String appCheckToken
    ) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(coreApiBaseUrl + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
            connection.setRequestProperty("Accept", "application/json");
            if (!TextUtils.isEmpty(appCheckToken)) {
                connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken);
            }
            if (body != null) {
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload);
                }
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = responseStream == null ? "" : readAll(responseStream);
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiException(statusCode, resolveErrorMessage(statusCode, responseBody));
            }
            return new JSONObject(responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveErrorMessage(int statusCode, String responseBody) {
        try {
            String message = optText(new JSONObject(responseBody), "message");
            if (!message.isEmpty()) {
                return message;
            }
        } catch (JSONException ignored) {
            // 서버 오류 본문이 JSON이 아니면 상태 코드 기준 안내를 사용한다.
        }
        if (statusCode == 401) {
            return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
        }
        if (statusCode == 403) {
            return "이 예약에 접근할 권한이 없습니다.";
        }
        if (statusCode == 404) {
            return "예약 정보를 찾지 못했습니다.";
        }
        if (statusCode == 409) {
            return "예약 상태가 변경되었습니다. 다시 불러온 뒤 시도해 주세요.";
        }
        return "예약 서버에 일시적으로 연결하지 못했습니다.";
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (InputStream stream = new BufferedInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (outputStream.size() + read > MAX_RESPONSE_BYTES) {
                    throw new IOException("Appointment response is too large");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String requireText(JSONObject object, String key) throws JSONException {
        String value = optText(object, key);
        if (value.isEmpty()) {
            throw new JSONException(key + " 값이 없습니다.");
        }
        return value;
    }

    private String optText(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return normalizeText(object.optString(key, ""));
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalizeText(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private interface AuthenticatedOperation<T> {
        T run(String idToken, @Nullable String appCheckToken) throws Exception;
    }

    private static final class AppointmentReference {
        private final String coreId;
        private final String legacyFirestoreId;
        private final long version;

        private AppointmentReference(String coreId, String legacyFirestoreId, long version) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.version = version;
        }
    }

    private static final class ApiAppointment {
        private final AppointmentRequest request;
        private final AppointmentReference reference;

        private ApiAppointment(AppointmentRequest request, AppointmentReference reference) {
            this.request = request;
            this.reference = reference;
        }
    }

    private static final class ApiException extends Exception {
        private final int statusCode;
        private final String userMessage;

        private ApiException(int statusCode, String userMessage) {
            super("Core API request failed: " + statusCode);
            this.statusCode = statusCode;
            this.userMessage = userMessage;
        }
    }
}
