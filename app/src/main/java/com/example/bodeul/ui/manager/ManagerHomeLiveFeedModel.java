package com.example.bodeul.ui.manager;

import androidx.annotation.DrawableRes;

/**
 * 진행 중 동행이 있을 때 노출하는 라이브 카드 정보를 담는다.
 */
public final class ManagerHomeLiveFeedModel {
    @DrawableRes
    private final int bannerBackgroundResId;
    private final String badgeText;
    private final String timeText;
    private final String titleText;
    private final String subtitleText;
    private final String noteText;
    private final String footerText;

    public ManagerHomeLiveFeedModel(
            int bannerBackgroundResId,
            String badgeText,
            String timeText,
            String titleText,
            String subtitleText,
            String noteText,
            String footerText
    ) {
        this.bannerBackgroundResId = bannerBackgroundResId;
        this.badgeText = badgeText;
        this.timeText = timeText;
        this.titleText = titleText;
        this.subtitleText = subtitleText;
        this.noteText = noteText;
        this.footerText = footerText;
    }

    public int getBannerBackgroundResId() {
        return bannerBackgroundResId;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public String getTimeText() {
        return timeText;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getSubtitleText() {
        return subtitleText;
    }

    public String getNoteText() {
        return noteText;
    }

    public String getFooterText() {
        return footerText;
    }
}
