package com.example.bodeul.ui.admin;

/**
 * 관리자 서류 검토 카드 한 장에 필요한 표시 상태를 담는다.
 */
public final class AdminManagerDocumentCardModel {
    private final String managerUserId;
    private final String titleText;
    private final String statusText;
    private final int statusBackgroundColorResId;
    private final int statusTextColorResId;
    private final String summaryText;
    private final String availabilityText;
    private final String reviewNoteText;
    private final String timelineText;
    private final boolean showActions;
    private final boolean actionsEnabled;
    private final boolean showFilesButton;
    private final boolean filesButtonEnabled;
    private final boolean showHistoryButton;
    private final boolean historyButtonEnabled;

    public AdminManagerDocumentCardModel(
            String managerUserId,
            String titleText,
            String statusText,
            int statusBackgroundColorResId,
            int statusTextColorResId,
            String summaryText,
            String availabilityText,
            String reviewNoteText,
            String timelineText,
            boolean showActions,
            boolean actionsEnabled,
            boolean showFilesButton,
            boolean filesButtonEnabled,
            boolean showHistoryButton,
            boolean historyButtonEnabled
    ) {
        this.managerUserId = managerUserId;
        this.titleText = titleText;
        this.statusText = statusText;
        this.statusBackgroundColorResId = statusBackgroundColorResId;
        this.statusTextColorResId = statusTextColorResId;
        this.summaryText = summaryText;
        this.availabilityText = availabilityText;
        this.reviewNoteText = reviewNoteText;
        this.timelineText = timelineText;
        this.showActions = showActions;
        this.actionsEnabled = actionsEnabled;
        this.showFilesButton = showFilesButton;
        this.filesButtonEnabled = filesButtonEnabled;
        this.showHistoryButton = showHistoryButton;
        this.historyButtonEnabled = historyButtonEnabled;
    }

    public String getManagerUserId() {
        return managerUserId;
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

    public String getSummaryText() {
        return summaryText;
    }

    public String getAvailabilityText() {
        return availabilityText;
    }

    public String getReviewNoteText() {
        return reviewNoteText;
    }

    public String getTimelineText() {
        return timelineText;
    }

    public boolean isShowActions() {
        return showActions;
    }

    public boolean isActionsEnabled() {
        return actionsEnabled;
    }

    public boolean isShowFilesButton() {
        return showFilesButton;
    }

    public boolean isFilesButtonEnabled() {
        return filesButtonEnabled;
    }

    public boolean isShowHistoryButton() {
        return showHistoryButton;
    }

    public boolean isHistoryButtonEnabled() {
        return historyButtonEnabled;
    }
}
