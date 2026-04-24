package com.example.bodeul.domain.model;

/**
 * 서버와 화면이 공통으로 사용하는 관리자 후속 알림 필터 태그다.
 */
public enum AdminActionNotificationFilterKey {
    UNREAD("unread"),
    UNRESOLVED("unresolved"),
    RESOLVED("resolved");

    private final String value;

    AdminActionNotificationFilterKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionNotificationFilterKey fromValue(String value) {
        if (value == null) {
            return UNREAD;
        }
        for (AdminActionNotificationFilterKey filterKey : values()) {
            if (filterKey.value.equalsIgnoreCase(value.trim())) {
                return filterKey;
            }
        }
        return UNREAD;
    }
}
