package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림 전달 기록의 SLA 상태를 정의한다.
 */
public enum AdminActionDeliverySlaStatus {
    ON_TRACK("on_track"),
    ATTENTION_REQUIRED("attention_required"),
    COMPLETED("completed");

    private final String value;

    AdminActionDeliverySlaStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliverySlaStatus fromValue(String value) {
        if (value == null) {
            return COMPLETED;
        }
        for (AdminActionDeliverySlaStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return COMPLETED;
    }
}
