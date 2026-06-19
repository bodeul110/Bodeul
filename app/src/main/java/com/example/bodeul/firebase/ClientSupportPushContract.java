package com.example.bodeul.firebase;

import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * 문의 답변 푸시를 앱 내부 갱신 이벤트로 전달하는 규약이다.
 */
public final class ClientSupportPushContract {
    public static final String ACTION_CLIENT_SUPPORT_UPDATED =
            "com.example.bodeul.action.CLIENT_SUPPORT_UPDATED";
    public static final String EXTRA_SUPPORT_REQUEST_ID = "supportRequestId";
    public static final String EXTRA_APPOINTMENT_REQUEST_ID = "appointmentRequestId";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";

    private ClientSupportPushContract() {}

    @NonNull
    public static Intent createRefreshIntent(@NonNull String packageName, @NonNull ClientSupportPushPayload payload) {
        Intent intent = new Intent(ACTION_CLIENT_SUPPORT_UPDATED);
        intent.setPackage(packageName);
        intent.putExtra(EXTRA_SUPPORT_REQUEST_ID, payload.getSupportRequestId());
        intent.putExtra(EXTRA_APPOINTMENT_REQUEST_ID, payload.getAppointmentRequestId());
        intent.putExtra(EXTRA_TITLE, payload.getTitle());
        intent.putExtra(EXTRA_BODY, payload.getBody());
        return intent;
    }
}
