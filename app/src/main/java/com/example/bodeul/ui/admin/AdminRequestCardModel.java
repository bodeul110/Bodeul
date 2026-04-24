package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 요청 카드를 그리기 위한 표현 모델이다.
 */
public final class AdminRequestCardModel {
    private final String requestId;
    private final String titleText;
    private final String statusText;
    private final int statusBackgroundColorResId;
    private final int statusTextColorResId;
    private final String participantsText;
    private final String scheduleText;
    private final String managerText;
    private final String progressText;
    private final String detailToggleText;
    private final boolean detailExpanded;
    private final String detailPanelText;
    private final String noteText;
    private final String constraintText;
    private final List<AdminRequestAssignActionModel> assignActions;

    public AdminRequestCardModel(
            String requestId,
            String titleText,
            String statusText,
            int statusBackgroundColorResId,
            int statusTextColorResId,
            String participantsText,
            String scheduleText,
            String managerText,
            String progressText,
            String detailToggleText,
            boolean detailExpanded,
            String detailPanelText,
            String noteText,
            String constraintText,
            List<AdminRequestAssignActionModel> assignActions
    ) {
        this.requestId = requestId;
        this.titleText = titleText;
        this.statusText = statusText;
        this.statusBackgroundColorResId = statusBackgroundColorResId;
        this.statusTextColorResId = statusTextColorResId;
        this.participantsText = participantsText;
        this.scheduleText = scheduleText;
        this.managerText = managerText;
        this.progressText = progressText;
        this.detailToggleText = detailToggleText;
        this.detailExpanded = detailExpanded;
        this.detailPanelText = detailPanelText;
        this.noteText = noteText;
        this.constraintText = constraintText;
        this.assignActions = assignActions;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getStatusBackgroundColorResId() {
        return statusBackgroundColorResId;
    }

    public int getStatusTextColorResId() {
        return statusTextColorResId;
    }

    public String getParticipantsText() {
        return participantsText;
    }

    public String getScheduleText() {
        return scheduleText;
    }

    public String getManagerText() {
        return managerText;
    }

    public String getProgressText() {
        return progressText;
    }

    public String getDetailToggleText() {
        return detailToggleText;
    }

    public boolean isDetailExpanded() {
        return detailExpanded;
    }

    public String getDetailPanelText() {
        return detailPanelText;
    }

    public String getNoteText() {
        return noteText;
    }

    public String getConstraintText() {
        return constraintText;
    }

    public List<AdminRequestAssignActionModel> getAssignActions() {
        return assignActions;
    }
}
