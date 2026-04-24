package com.example.bodeul.ui.admin;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 관리자 운영 섹션에서 공통으로 그리는 카드 모델이다.
 */
public final class AdminOperationCardModel {
    private final String requestId;
    private final AdminOperationBadgeModel statusBadge;
    @Nullable
    private final AdminOperationBadgeModel priorityBadge;
    private final String titleText;
    private final String subtitleText;
    private final String summaryText;
    private final String activityText;
    private final List<AdminOperationLineItem> detailLines;
    private final List<AdminOperationActionModel> actions;

    public AdminOperationCardModel(
            String requestId,
            AdminOperationBadgeModel statusBadge,
            @Nullable AdminOperationBadgeModel priorityBadge,
            String titleText,
            String subtitleText,
            String summaryText,
            String activityText,
            List<AdminOperationLineItem> detailLines,
            List<AdminOperationActionModel> actions
    ) {
        this.requestId = requestId;
        this.statusBadge = statusBadge;
        this.priorityBadge = priorityBadge;
        this.titleText = titleText;
        this.subtitleText = subtitleText;
        this.summaryText = summaryText;
        this.activityText = activityText;
        this.detailLines = detailLines;
        this.actions = actions;
    }

    public String getRequestId() {
        return requestId;
    }

    public AdminOperationBadgeModel getStatusBadge() {
        return statusBadge;
    }

    @Nullable
    public AdminOperationBadgeModel getPriorityBadge() {
        return priorityBadge;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getSubtitleText() {
        return subtitleText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getActivityText() {
        return activityText;
    }

    public List<AdminOperationLineItem> getDetailLines() {
        return detailLines;
    }

    public List<AdminOperationActionModel> getActions() {
        return actions;
    }
}
