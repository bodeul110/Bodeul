package com.example.bodeul.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public final class CompanionLocationAlertPushPayload {
    @Nullable
    public static CompanionLocationAlertPushPayload from(@Nullable RemoteMessage remoteMessage) {
        if (remoteMessage == null) {
            return null;
        }
        Map<String, String> data = remoteMessage.getData();
        if (!"companion_location_alert".equals(normalize(data.get("type")))) {
            return null;
        }
        String requestId = normalize(data.get("appointmentRequestId"));
        if (requestId.isEmpty()) {
            return null;
        }
        return new CompanionLocationAlertPushPayload(
                requestId,
                normalize(data.get("sessionId")),
                normalize(data.get("alertStage")),
                normalize(data.get("title")),
                normalize(data.get("body"))
        );
    }

    private final String appointmentRequestId;
    private final String sessionId;
    private final String alertStage;
    private final String title;
    private final String body;

    public CompanionLocationAlertPushPayload(
            String appointmentRequestId,
            String sessionId,
            String alertStage,
            String title,
            String body
    ) {
        this.appointmentRequestId = appointmentRequestId;
        this.sessionId = sessionId;
        this.alertStage = alertStage;
        this.title = title;
        this.body = body;
    }

    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAlertStage() {
        return alertStage;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
