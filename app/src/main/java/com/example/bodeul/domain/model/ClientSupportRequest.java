package com.example.bodeul.domain.model;

/**
 * 환자 또는 보호자가 남긴 문의 한 건과 응답 상태를 담는다.
 */
public final class ClientSupportRequest {
    private final String id;
    private final String userId;
    private final String userName;
    private final UserRole userRole;
    private final String appointmentRequestId;
    private final ClientSupportCategory category;
    private final String title;
    private final String body;
    private final ClientSupportStatus status;
    private final long createdAtMillis;
    private final String responseText;
    private final long respondedAtMillis;
    private final String respondedByName;
    private final boolean responseReadByUser;
    private final long responseReadAtMillis;
    private final int responseReminderCount;
    private final long responseReminderSentAtMillis;

    public ClientSupportRequest(
            String id,
            String userId,
            String userName,
            UserRole userRole,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body,
            ClientSupportStatus status,
            long createdAtMillis,
            String responseText,
            long respondedAtMillis,
            String respondedByName,
            boolean responseReadByUser,
            long responseReadAtMillis,
            int responseReminderCount,
            long responseReminderSentAtMillis
    ) {
        this.id = id == null ? "" : id;
        this.userId = userId == null ? "" : userId;
        this.userName = userName == null ? "" : userName;
        this.userRole = userRole == null ? UserRole.PATIENT : userRole;
        this.appointmentRequestId = appointmentRequestId == null ? "" : appointmentRequestId;
        this.category = category == null ? ClientSupportCategory.RESERVATION : category;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.status = status == null ? ClientSupportStatus.RECEIVED : status;
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
        this.responseText = responseText == null ? "" : responseText;
        this.respondedAtMillis = Math.max(respondedAtMillis, 0L);
        this.respondedByName = respondedByName == null ? "" : respondedByName;
        this.responseReadByUser = responseReadByUser;
        this.responseReadAtMillis = Math.max(responseReadAtMillis, 0L);
        this.responseReminderCount = Math.max(responseReminderCount, 0);
        this.responseReminderSentAtMillis = Math.max(responseReminderSentAtMillis, 0L);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    public ClientSupportCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public ClientSupportStatus getStatus() {
        return status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getResponseText() {
        return responseText;
    }

    public long getRespondedAtMillis() {
        return respondedAtMillis;
    }

    public String getRespondedByName() {
        return respondedByName;
    }

    public boolean isResponseReadByUser() {
        return responseReadByUser;
    }

    public long getResponseReadAtMillis() {
        return responseReadAtMillis;
    }

    public int getResponseReminderCount() {
        return responseReminderCount;
    }

    public long getResponseReminderSentAtMillis() {
        return responseReminderSentAtMillis;
    }

    public boolean hasUnreadResponse() {
        return status == ClientSupportStatus.ANSWERED
                && !responseText.isEmpty()
                && !responseReadByUser;
    }

    public boolean hasStaleUnreadResponse(long nowMillis, long staleThresholdMillis) {
        if (!hasUnreadResponse()) {
            return false;
        }
        if (respondedAtMillis <= 0L || staleThresholdMillis <= 0L) {
            return false;
        }
        return nowMillis - respondedAtMillis >= staleThresholdMillis;
    }
}
