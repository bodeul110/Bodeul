package com.example.bodeul.ui.manager;

/**
 * 내 페이지에서 보여주는 서류 검토 이력 카드의 표현 모델이다.
 */
public final class ManagerDocumentHistoryItemModel {
    private final String badgeText;
    private final int badgeBackgroundColorRes;
    private final int badgeTextColorRes;
    private final String timestampText;
    private final String actorText;
    private final String bodyText;

    public ManagerDocumentHistoryItemModel(
            String badgeText,
            int badgeBackgroundColorRes,
            int badgeTextColorRes,
            String timestampText,
            String actorText,
            String bodyText
    ) {
        this.badgeText = badgeText;
        this.badgeBackgroundColorRes = badgeBackgroundColorRes;
        this.badgeTextColorRes = badgeTextColorRes;
        this.timestampText = timestampText;
        this.actorText = actorText;
        this.bodyText = bodyText;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public int getBadgeBackgroundColorRes() {
        return badgeBackgroundColorRes;
    }

    public int getBadgeTextColorRes() {
        return badgeTextColorRes;
    }

    public String getTimestampText() {
        return timestampText;
    }

    public String getActorText() {
        return actorText;
    }

    public String getBodyText() {
        return bodyText;
    }
}

