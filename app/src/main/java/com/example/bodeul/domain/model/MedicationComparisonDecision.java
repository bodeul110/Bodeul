package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 매니저가 현장 복약 안내와 기존 예약 복약 정보를 대조한 결과를 표현한다.
 */
public enum MedicationComparisonDecision {
    MATCHED,
    CHANGED,
    RECHECK_REQUIRED;

    @Nullable
    public static MedicationComparisonDecision fromValue(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(rawValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
