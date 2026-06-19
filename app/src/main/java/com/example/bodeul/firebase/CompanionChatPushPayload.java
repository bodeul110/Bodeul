package com.example.bodeul.firebase;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * 안심 채팅 푸시 payload를 화면 갱신과 시스템 알림에 공통으로 전달한다.
 */
public final class CompanionChatPushPayload {
    public static final String TYPE_COMPANION_CHAT_MESSAGE = "companion_chat_message";

    @Nullable
    public static CompanionChatPushPayload from(@Nullable RemoteMessage remoteMessage) {
        if (remoteMessage == null) {
            return null;
        }
        Map<String, String> data = remoteMessage.getData();
        if (!TYPE_COMPANION_CHAT_MESSAGE.equals(trim(data.get("type")))) {
            return null;
        }

        String title = trim(data.get("title"));
        String body = trim(data.get("body"));
        if (TextUtils.isEmpty(title) && remoteMessage.getNotification() != null) {
            title = trim(remoteMessage.getNotification().getTitle());
        }
        if (TextUtils.isEmpty(body) && remoteMessage.getNotification() != null) {
            body = trim(remoteMessage.getNotification().getBody());
        }

        return new CompanionChatPushPayload(
                trim(data.get("appointmentRequestId")),
                trim(data.get("sessionId")),
                trim(data.get("senderRole")),
                trim(data.get("sentAtMillis")),
                title,
                body
        );
    }

    private final String appointmentRequestId;
    private final String sessionId;
    private final String senderRole;
    private final String sentAtMillis;
    private final String title;
    private final String body;

    public CompanionChatPushPayload(
            @Nullable String appointmentRequestId,
            @Nullable String sessionId,
            @Nullable String senderRole,
            @Nullable String sentAtMillis,
            @Nullable String title,
            @Nullable String body
    ) {
        this.appointmentRequestId = trim(appointmentRequestId);
        this.sessionId = trim(sessionId);
        this.senderRole = trim(senderRole);
        this.sentAtMillis = trim(sentAtMillis);
        this.title = trim(title);
        this.body = trim(body);
    }

    @NonNull
    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @NonNull
    public String getSenderRole() {
        return senderRole;
    }

    @NonNull
    public String getSentAtMillis() {
        return sentAtMillis;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getBody() {
        return body;
    }

    public int toNotificationId() {
        if (!TextUtils.isEmpty(sentAtMillis)) {
            return sentAtMillis.hashCode();
        }
        if (!TextUtils.isEmpty(sessionId)) {
            return sessionId.hashCode();
        }
        if (!TextUtils.isEmpty(appointmentRequestId)) {
            return appointmentRequestId.hashCode();
        }
        return TYPE_COMPANION_CHAT_MESSAGE.hashCode();
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
