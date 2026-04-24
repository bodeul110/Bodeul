package com.example.bodeul.ui.manager;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

/**
 * 매니저 홈 하단 소개 카드에 필요한 표시 정보를 담는다.
 */
public final class ManagerHomePromoCardModel {
    @DrawableRes
    private final int bannerBackgroundResId;
    private final String badgeText;
    @DrawableRes
    private final int badgeBackgroundResId;
    @ColorRes
    private final int badgeTextColorResId;
    private final String titleText;
    private final String bodyText;

    public ManagerHomePromoCardModel(
            int bannerBackgroundResId,
            String badgeText,
            int badgeBackgroundResId,
            int badgeTextColorResId,
            String titleText,
            String bodyText
    ) {
        this.bannerBackgroundResId = bannerBackgroundResId;
        this.badgeText = badgeText;
        this.badgeBackgroundResId = badgeBackgroundResId;
        this.badgeTextColorResId = badgeTextColorResId;
        this.titleText = titleText;
        this.bodyText = bodyText;
    }

    public int getBannerBackgroundResId() {
        return bannerBackgroundResId;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public int getBadgeBackgroundResId() {
        return badgeBackgroundResId;
    }

    public int getBadgeTextColorResId() {
        return badgeTextColorResId;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }
}
