package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 예약 종료 후 사용자가 남기는 정산 확인 상태를 정의한다.
 */
public enum AppointmentFollowUpSettlementStatus {
    CONFIRMED("CONFIRMED"),
    NEEDS_HELP("NEEDS_HELP");

    private final String value;

    AppointmentFollowUpSettlementStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static AppointmentFollowUpSettlementStatus fromValue(@Nullable String value) {
        for (AppointmentFollowUpSettlementStatus item : values()) {
            if (item.value.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return item;
            }
        }
        return null;
    }
}
