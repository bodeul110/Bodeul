package com.example.bodeul.ui.manager;

/**
 * 매니저 내 페이지에서 문서별 원본 파일 상태를 보여주는 카드 모델이다.
 */
public final class ManagerDocumentFileCardModel {
    private final String titleText;
    private final String badgeText;
    private final int badgeBackgroundColorResId;
    private final int badgeTextColorResId;
    private final String bodyText;
    private final String timestampText;

    public ManagerDocumentFileCardModel(
            String titleText,
            String badgeText,
            int badgeBackgroundColorResId,
            int badgeTextColorResId,
            String bodyText,
            String timestampText
    ) {
        this.titleText = titleText;
        this.badgeText = badgeText;
        this.badgeBackgroundColorResId = badgeBackgroundColorResId;
        this.badgeTextColorResId = badgeTextColorResId;
        this.bodyText = bodyText;
        this.timestampText = timestampText;
    }

    public String getTitleText() {
        return titleText;
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

    public String getBodyText() {
        return bodyText;
    }

    public String getTimestampText() {
        return timestampText;
    }
}
