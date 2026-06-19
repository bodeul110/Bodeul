package com.example.bodeul.util;

import androidx.annotation.Nullable;

/**
 * 예약 단계 복약 정보와 현장 리포트 비교 결과를 사용자 화면에 전달한다.
 */
public final class MedicationComparisonSummary {
    private final String statusLabel;
    @Nullable
    private final String detailLabel;
    @Nullable
    private final String followUpLabel;

    public MedicationComparisonSummary(
            String statusLabel,
            @Nullable String detailLabel,
            @Nullable String followUpLabel
    ) {
        this.statusLabel = statusLabel;
        this.detailLabel = detailLabel;
        this.followUpLabel = followUpLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    @Nullable
    public String getDetailLabel() {
        return detailLabel;
    }

    @Nullable
    public String getFollowUpLabel() {
        return followUpLabel;
    }
}
