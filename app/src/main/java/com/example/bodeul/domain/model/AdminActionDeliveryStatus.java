package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림 전달 결과를 정의한다.
 */
public enum AdminActionDeliveryStatus {
    SENT("sent"),
    CONFIRMED("confirmed"),
    SKIPPED("skipped"),
    FAILED("failed");

    private final String value;

    AdminActionDeliveryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliveryStatus fromValue(String value) {
        if (value == null) {
            return SENT;
        }
        for (AdminActionDeliveryStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return SENT;
    }
}
