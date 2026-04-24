package com.example.bodeul.ui.manager;

/**
 * 매니저 홈 상단 히어로 카드에 필요한 표시 정보를 묶는다.
 */
public final class ManagerHomeHeroModel {
    private final String badgeText;
    private final String statusText;
    private final String titleText;
    private final String bodyText;
    private final String actionText;
    private final boolean actionEnabled;

    public ManagerHomeHeroModel(
            String badgeText,
            String statusText,
            String titleText,
            String bodyText,
            String actionText,
            boolean actionEnabled
    ) {
        this.badgeText = badgeText;
        this.statusText = statusText;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.actionText = actionText;
        this.actionEnabled = actionEnabled;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public String getStatusText() {
        return statusText;
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

    public boolean isActionEnabled() {
        return actionEnabled;
    }
}
