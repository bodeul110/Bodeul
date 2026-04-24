package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림 카드의 정렬과 강조 수준을 정의한다.
 */
public enum AdminActionNotificationPriority {
    IMMEDIATE("immediate", 125),
    ACTION_REQUIRED("action_required", 120),
    MONITORING("monitoring", 100),
    ARCHIVED("archived", 0);

    private final String value;
    private final int sortOrder;

    AdminActionNotificationPriority(String value, int sortOrder) {
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public String getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public static AdminActionNotificationPriority fromValue(String value) {
        if (value == null) {
            return MONITORING;
        }
        for (AdminActionNotificationPriority priority : values()) {
            if (priority.value.equalsIgnoreCase(value.trim())) {
                return priority;
            }
        }
        return MONITORING;
    }
}
