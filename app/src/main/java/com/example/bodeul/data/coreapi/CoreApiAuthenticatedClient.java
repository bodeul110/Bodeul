package com.example.bodeul.data.coreapi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firebase ID token과 App Check token을 붙여 Core API JSON 요청을 실행한다.
 */
final class CoreApiAuthenticatedClient {
    private static final String TAG = "BodeulCoreApi";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String baseUrl;

    CoreApiAuthenticatedClient(Context context) {
        baseUrl = normalizeBaseUrl(
                context.getApplicationContext().getString(R.string.bodeul_core_api_base_url));
    }

    <T> void execute(
            Operation<T> operation,
            RepositoryCallback<T> callback,
            String fallbackMessage,
            String logContext
    ) {
        if (baseUrl.isEmpty()) {
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
                        Log.w(TAG, logContext + " 요청 실패: HTTP " + exception.statusCode);
                        postError(callback, exception.userMessage);
                    } catch (Exception exception) {
                        Log.w(TAG, logContext + " 요청 실패: " + exception.getClass().getSimpleName());
                        postError(callback, fallbackMessage);
                    }
                });
            });
        });
    }

    JSONObject requestJson(
            String method,
            String path,
            @Nullable JSONObject body,
            String idToken,
            @Nullable String appCheckToken
    ) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
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

    <T> void postError(RepositoryCallback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
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
            return "이 정보에 접근할 권한이 없습니다.";
        }
        if (statusCode == 404) {
            return "요청한 정보를 찾지 못했습니다.";
        }
        if (statusCode == 409) {
            return "다른 변경이 먼저 반영되었습니다. 다시 불러온 뒤 시도해 주세요.";
        }
        return "서버에 일시적으로 연결하지 못했습니다.";
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (InputStream stream = new BufferedInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (outputStream.size() + read > MAX_RESPONSE_BYTES) {
                    throw new IOException("Core API response is too large");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
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

    interface Operation<T> {
        T run(String idToken, @Nullable String appCheckToken) throws Exception;
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
