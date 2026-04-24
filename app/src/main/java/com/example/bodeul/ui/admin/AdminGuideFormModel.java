package com.example.bodeul.ui.admin;

/**
 * 관리자 병원 가이드 폼 상단 상태를 표현한다.
 */
public final class AdminGuideFormModel {
    private final String titleText;
    private final String badgeText;
    private final String helperText;
    private final String submitButtonText;
    private final boolean cancelVisible;
    private final boolean canEditTarget;
    private final boolean canEditSteps;

    public AdminGuideFormModel(
            String titleText,
            String badgeText,
            String helperText,
            String submitButtonText,
            boolean cancelVisible,
            boolean canEditTarget,
            boolean canEditSteps
    ) {
        this.titleText = titleText;
        this.badgeText = badgeText;
        this.helperText = helperText;
        this.submitButtonText = submitButtonText;
        this.cancelVisible = cancelVisible;
        this.canEditTarget = canEditTarget;
        this.canEditSteps = canEditSteps;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public String getHelperText() {
        return helperText;
    }

    public String getSubmitButtonText() {
        return submitButtonText;
    }

    public boolean isCancelVisible() {
        return cancelVisible;
    }

    public boolean canEditTarget() {
        return canEditTarget;
    }

    public boolean canEditSteps() {
        return canEditSteps;
    }
}
