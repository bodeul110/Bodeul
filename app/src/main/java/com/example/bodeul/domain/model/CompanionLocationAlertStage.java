package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 연속 위치 공유 중 자동 위치 알림이 어느 단계까지 발송됐는지 표현한다.
 */
public enum CompanionLocationAlertStage {
    NONE("none", 0),
    HOSPITAL_NEAR("hospital_near", 1),
    PHARMACY_NEAR("pharmacy_near", 2);

    private final String value;
    private final int order;

    CompanionLocationAlertStage(String value, int order) {
        this.value = value;
        this.order = order;
    }

    public String getValue() {
        return value;
    }

    public boolean canAdvanceTo(@Nullable CompanionLocationAlertStage nextStage) {
        if (nextStage == null) {
            return false;
        }
        return nextStage.order > order;
    }

    public static CompanionLocationAlertStage fromValue(@Nullable String rawValue) {
        if (rawValue != null) {
            for (CompanionLocationAlertStage stage : values()) {
                if (stage.value.equalsIgnoreCase(rawValue.trim())) {
                    return stage;
                }
            }
        }
        return NONE;
    }
}
