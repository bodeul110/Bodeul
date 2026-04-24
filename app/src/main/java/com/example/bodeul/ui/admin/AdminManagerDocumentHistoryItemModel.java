package com.example.bodeul.ui.admin;

/**
 * 관리자 서류 검토 이력 한 줄의 표현 상태를 담는다.
 */
public final class AdminManagerDocumentHistoryItemModel {
    private final String badgeText;
    private final int badgeBackgroundColorResId;
    private final int badgeTextColorResId;
    private final String timestampText;
    private final String actorText;
    private final String bodyText;

    public AdminManagerDocumentHistoryItemModel(
            String badgeText,
            int badgeBackgroundColorResId,
            int badgeTextColorResId,
            String timestampText,
            String actorText,
            String bodyText
    ) {
        this.badgeText = badgeText;
        this.badgeBackgroundColorResId = badgeBackgroundColorResId;
        this.badgeTextColorResId = badgeTextColorResId;
        this.timestampText = timestampText;
        this.actorText = actorText;
        this.bodyText = bodyText;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public int getBadgeBackgroundColorResId() {
        return badgeBackgroundColorResId;
    }

    public int getBadgeTextColorResId() {
        return badgeTextColorResId;
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
