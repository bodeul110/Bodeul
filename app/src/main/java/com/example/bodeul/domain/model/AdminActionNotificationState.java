package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림의 현재 처리 상태를 정의한다.
 */
public enum AdminActionNotificationState {
    UNREAD("unread"),
    READ("read"),
    RESOLVED("resolved");

    private final String value;

    AdminActionNotificationState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionNotificationState fromValue(String value) {
        if (value == null) {
            return UNREAD;
        }
        for (AdminActionNotificationState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        return UNREAD;
    }
}
