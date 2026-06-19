package com.example.bodeul.util;

import androidx.annotation.Nullable;

/**
 * 예약 단계 복약 정보와 현장 리포트를 비교한 사용자 표시용 요약이다.
 */
public final class MedicationComparisonSummary {
    private final String statusLabel;
    @Nullable
    private final String followUpLabel;

    public MedicationComparisonSummary(String statusLabel, @Nullable String followUpLabel) {
        this.statusLabel = statusLabel;
        this.followUpLabel = followUpLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    @Nullable
    public String getFollowUpLabel() {
        return followUpLabel;
    }
}
