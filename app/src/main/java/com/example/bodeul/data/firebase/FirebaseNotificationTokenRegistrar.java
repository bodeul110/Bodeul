package com.example.bodeul.data.firebase;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.data.NotificationTokenRegistrar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * FCM 등록 토큰을 현재 사용자 문서와 동기화한다.
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

        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationTokens", FieldValue.arrayUnion(token.trim()));
        updates.put("notificationTokenUpdatedAt", FieldValue.serverTimestamp());
        updates.put("notificationTokenPlatform", "android");

        firestore.collection("users")
                .document(currentUserId)
                .set(updates, SetOptions.merge())
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

        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationTokens", FieldValue.arrayRemove(token.trim()));
        updates.put("notificationTokenUpdatedAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(currentUserId)
                .set(updates, SetOptions.merge())
                .addOnFailureListener(error -> Log.w(TAG, "FCM 토큰 정리에 실패했습니다.", error));
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
