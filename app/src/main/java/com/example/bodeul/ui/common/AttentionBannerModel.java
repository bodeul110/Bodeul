package com.example.bodeul.ui.common;

public final class AttentionBannerModel {
    private final String badgeText;
    private final String titleText;
    private final String bodyText;
    private final String actionText;
    private final AttentionBannerTone tone;

    public AttentionBannerModel(
            String badgeText,
            String titleText,
            String bodyText,
            String actionText,
            AttentionBannerTone tone
    ) {
        this.badgeText = badgeText;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.actionText = actionText;
        this.tone = tone == null ? AttentionBannerTone.WARNING : tone;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getActionText() {
        return actionText;
    }

    public AttentionBannerTone getTone() {
        return tone;
    }
}
