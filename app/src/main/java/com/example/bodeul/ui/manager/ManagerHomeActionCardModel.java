package com.example.bodeul.ui.manager;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

/**
 * 매니저 홈 빠른 액션 카드의 표시 정보를 담는다.
 */
public final class ManagerHomeActionCardModel {
    private final ManagerHomeActionType actionType;
    private final String badgeText;
    @DrawableRes
    private final int badgeBackgroundResId;
    @ColorRes
    private final int badgeTextColorResId;
    private final String titleText;
    private final String bodyText;
    private final String statusText;

    public ManagerHomeActionCardModel(
            ManagerHomeActionType actionType,
            String badgeText,
            int badgeBackgroundResId,
            int badgeTextColorResId,
            String titleText,
            String bodyText,
            String statusText
    ) {
        this.actionType = actionType;
        this.badgeText = badgeText;
        this.badgeBackgroundResId = badgeBackgroundResId;
        this.badgeTextColorResId = badgeTextColorResId;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.statusText = statusText;
    }

    public ManagerHomeActionType getActionType() {
        return actionType;
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

    public String getStatusText() {
        return statusText;
    }
}
