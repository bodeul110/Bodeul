package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 예약 종료 후 SOS 관련으로 사용자가 남긴 후속 기록 상태를 정의한다.
 */
public enum AppointmentFollowUpSupportEscalationStatus {
    GUIDE_VIEWED("GUIDE_VIEWED"),
    MANAGER_CALLED("MANAGER_CALLED"),
    DIALED_119("DIALED_119");

    private final String value;

    AppointmentFollowUpSupportEscalationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static AppointmentFollowUpSupportEscalationStatus fromValue(@Nullable String value) {
        for (AppointmentFollowUpSupportEscalationStatus item : values()) {
            if (item.value.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return item;
            }
        }
        return null;
    }
}
