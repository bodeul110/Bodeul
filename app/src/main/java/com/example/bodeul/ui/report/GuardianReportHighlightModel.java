package com.example.bodeul.ui.report;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentStatus;

/**
 * 화면 상단 대표 진행 현황 카드 모델이다.
 */
public final class GuardianReportHighlightModel {
    private final AppointmentStatus status;
    private final String titleText;
    private final String bodyText;
    @Nullable
    private final String requestId;
    @Nullable
    private final String actionLabelText;

    public GuardianReportHighlightModel(
            AppointmentStatus status,
            String titleText,
            String bodyText,
            @Nullable String requestId,
            @Nullable String actionLabelText
    ) {
        this.status = status;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.requestId = requestId;
        this.actionLabelText = actionLabelText;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    @Nullable
    public String getActionLabelText() {
        return actionLabelText;
    }
}
