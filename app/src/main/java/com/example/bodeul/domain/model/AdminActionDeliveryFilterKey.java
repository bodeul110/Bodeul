package com.example.bodeul.domain.model;

/**
 * 서버와 화면이 공통으로 사용하는 관리자 후속 알림 전달 기록 필터 태그다.
 */
public enum AdminActionDeliveryFilterKey {
    PENDING_CONFIRMATION("pending_confirmation"),
    FOLLOW_UP_REQUIRED("follow_up_required"),
    COMPLETED("completed");

    private final String value;

    AdminActionDeliveryFilterKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliveryFilterKey fromValue(String value) {
        if (value == null) {
            return COMPLETED;
        }
        for (AdminActionDeliveryFilterKey filterKey : values()) {
            if (filterKey.value.equalsIgnoreCase(value.trim())) {
                return filterKey;
            }
        }
        return COMPLETED;
    }
}
