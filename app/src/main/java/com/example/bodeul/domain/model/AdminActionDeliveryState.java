package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림 전달 기록의 현재 처리 상태를 정의한다.
 */
public enum AdminActionDeliveryState {
    PENDING_CONFIRMATION("pending_confirmation"),
    FOLLOW_UP_REQUIRED("follow_up_required"),
    DELIVERED("delivered"),
    SKIPPED("skipped");

    private final String value;

    AdminActionDeliveryState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliveryState fromValue(String value) {
        if (value == null) {
            return DELIVERED;
        }
        for (AdminActionDeliveryState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        return DELIVERED;
    }
}
