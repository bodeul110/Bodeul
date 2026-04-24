package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림의 강조 수준을 나타낸다.
 */
public enum AdminActionNotificationLevel {
    INFO("INFO"),
    WARNING("WARNING");

    private final String value;

    AdminActionNotificationLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionNotificationLevel fromValue(String value) {
        if (value == null) {
            return INFO;
        }
        for (AdminActionNotificationLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return INFO;
    }
}
