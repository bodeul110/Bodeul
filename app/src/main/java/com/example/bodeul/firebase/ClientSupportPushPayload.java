package com.example.bodeul.firebase;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * 이용자 문의 답변 푸시 payload를 화면 갱신과 시스템 알림에 공통으로 전달한다.
 */
public final class ClientSupportPushPayload {
    public static final String TYPE_CLIENT_SUPPORT_ANSWERED = "client_support_answered";

    @Nullable
    public static ClientSupportPushPayload from(@Nullable RemoteMessage remoteMessage) {
        if (remoteMessage == null) {
            return null;
        }
        Map<String, String> data = remoteMessage.getData();
        if (!TYPE_CLIENT_SUPPORT_ANSWERED.equals(trim(data.get("type")))) {
            return null;
        }

        String title = remoteMessage.getNotification() == null
                ? ""
                : trim(remoteMessage.getNotification().getTitle());
        String body = remoteMessage.getNotification() == null
                ? ""
                : trim(remoteMessage.getNotification().getBody());
        return new ClientSupportPushPayload(
                trim(data.get("supportRequestId")),
                trim(data.get("appointmentRequestId")),
                title,
                body
        );
    }

    private final String supportRequestId;
    private final String appointmentRequestId;
    private final String title;
    private final String body;

    public ClientSupportPushPayload(
            @Nullable String supportRequestId,
            @Nullable String appointmentRequestId,
            @Nullable String title,
            @Nullable String body
    ) {
        this.supportRequestId = trim(supportRequestId);
        this.appointmentRequestId = trim(appointmentRequestId);
        this.title = trim(title);
        this.body = trim(body);
    }

    @NonNull
    public String getSupportRequestId() {
        return supportRequestId;
    }

    @NonNull
    public String getAppointmentRequestId() {
        return appointmentRequestId;
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
        if (!TextUtils.isEmpty(supportRequestId)) {
            return supportRequestId.hashCode();
        }
        if (!TextUtils.isEmpty(appointmentRequestId)) {
            return appointmentRequestId.hashCode();
        }
        return TYPE_CLIENT_SUPPORT_ANSWERED.hashCode();
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
