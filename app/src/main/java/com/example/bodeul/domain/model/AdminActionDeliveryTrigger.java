package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림 전달 기록이 어떤 이벤트에서 생성됐는지 정의한다.
 */
public enum AdminActionDeliveryTrigger {
    NOTIFICATION_CREATED("notification_created"),
    NOTIFICATION_READ("notification_read"),
    NOTIFICATION_RESOLVED("notification_resolved"),
    NOTIFICATION_REOPENED("notification_reopened");

    private final String value;

    AdminActionDeliveryTrigger(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliveryTrigger fromValue(String value) {
        if (value == null) {
            return NOTIFICATION_CREATED;
        }
        for (AdminActionDeliveryTrigger trigger : values()) {
            if (trigger.value.equalsIgnoreCase(value.trim())) {
                return trigger;
            }
        }
        return NOTIFICATION_CREATED;
    }
}
