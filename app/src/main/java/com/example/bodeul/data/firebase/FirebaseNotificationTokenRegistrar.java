package com.example.bodeul.data.firebase;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.data.NotificationTokenRegistrar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * FCM 등록 토큰을 현재 사용자 문서에 동기화한다.
 */
public final class FirebaseNotificationTokenRegistrar implements NotificationTokenRegistrar {
    private static final String TAG = "PushTokenRegistrar";

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public FirebaseNotificationTokenRegistrar(
            FirebaseAuth firebaseAuth,
            FirebaseFirestore firestore
    ) {
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
    }

    @Override
    public void syncCurrentUserToken() {
        String currentUserId = getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(this::syncCurrentUserToken)
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰을 읽지 못했습니다.", error));
    }

    @Override
    public void syncCurrentUserToken(String token) {
        String currentUserId = getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(token)) {
            return;
        }

        String normalizedToken = token.trim();
        long updatedAtMillis = System.currentTimeMillis();
        String tokenEntryFieldPath = buildTokenEntryFieldPath(normalizedToken);

        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationTokens", FieldValue.arrayUnion(normalizedToken));
        updates.put("notificationTokenUpdatedAt", FieldValue.serverTimestamp());
        updates.put("notificationTokenPlatform", "android");

        firestore.collection("users")
                .document(currentUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        syncTokenEntry(currentUserId, tokenEntryFieldPath, normalizedToken, updatedAtMillis))
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 저장에 실패했습니다.", error));
    }

    @Override
    public void clearCurrentUserToken() {
        String currentUserId = getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> clearCurrentUserToken(currentUserId, token))
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 정리를 위해 토큰을 읽지 못했습니다.", error));
    }

    private void clearCurrentUserToken(@NonNull String currentUserId, @Nullable String token) {
        if (TextUtils.isEmpty(token)) {
            return;
        }

        String normalizedToken = token.trim();
        String tokenEntryFieldPath = buildTokenEntryFieldPath(normalizedToken);

        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationTokens", FieldValue.arrayRemove(normalizedToken));
        updates.put("notificationTokenUpdatedAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(currentUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> clearTokenEntry(currentUserId, tokenEntryFieldPath))
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 정리에 실패했습니다.", error));
    }

    /**
     * 토큰별 마지막 동기화 시각을 별도 메타데이터로 함께 남긴다.
     */
    private void syncTokenEntry(
            @NonNull String currentUserId,
            @NonNull String tokenEntryFieldPath,
            @NonNull String token,
            long updatedAtMillis
    ) {
        Map<String, Object> tokenEntry = new HashMap<>();
        tokenEntry.put("token", token);
        tokenEntry.put("platform", "android");
        tokenEntry.put("updatedAtMillis", updatedAtMillis);

        firestore.collection("users")
                .document(currentUserId)
                .update(tokenEntryFieldPath, tokenEntry)
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 메타데이터 저장에 실패했습니다.", error));
    }

    /**
     * 토큰 배열에서 제거할 때 메타데이터도 같이 지운다.
     */
    private void clearTokenEntry(
            @NonNull String currentUserId,
            @NonNull String tokenEntryFieldPath
    ) {
        firestore.collection("users")
                .document(currentUserId)
                .update(tokenEntryFieldPath, FieldValue.delete())
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 메타데이터 정리에 실패했습니다.", error));
    }

    @NonNull
    private String buildTokenEntryFieldPath(@NonNull String token) {
        return "notificationTokenEntries." + encodeTokenKey(token);
    }

    @NonNull
    private String encodeTokenKey(@NonNull String token) {
        return Base64.encodeToString(
                token.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE
        );
    }

    @NonNull
    private String getCurrentUserId() {
        if (firebaseAuth.getCurrentUser() == null) {
            return "";
        }
        String uid = firebaseAuth.getCurrentUser().getUid();
        return uid == null ? "" : uid.trim();
    }
}
