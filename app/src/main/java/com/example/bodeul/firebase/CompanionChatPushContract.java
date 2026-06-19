package com.example.bodeul.firebase;

import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * 안심 채팅 새 메시지 수신 이벤트를 앱 내부 브로드캐스트로 전달하는 규약이다.
 */
public final class CompanionChatPushContract {
    public static final String ACTION_COMPANION_CHAT_UPDATED =
            "com.example.bodeul.action.COMPANION_CHAT_UPDATED";
    public static final String EXTRA_APPOINTMENT_REQUEST_ID = "appointmentRequestId";
    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final String EXTRA_SENDER_ROLE = "senderRole";
    public static final String EXTRA_SENT_AT_MILLIS = "sentAtMillis";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";

    private CompanionChatPushContract() {}

    @NonNull
    public static Intent createRefreshIntent(@NonNull String packageName, @NonNull CompanionChatPushPayload payload) {
        Intent intent = new Intent(ACTION_COMPANION_CHAT_UPDATED);
        intent.setPackage(packageName);
        intent.putExtra(EXTRA_APPOINTMENT_REQUEST_ID, payload.getAppointmentRequestId());
        intent.putExtra(EXTRA_SESSION_ID, payload.getSessionId());
        intent.putExtra(EXTRA_SENDER_ROLE, payload.getSenderRole());
        intent.putExtra(EXTRA_SENT_AT_MILLIS, payload.getSentAtMillis());
        intent.putExtra(EXTRA_TITLE, payload.getTitle());
        intent.putExtra(EXTRA_BODY, payload.getBody());
        return intent;
    }
}
