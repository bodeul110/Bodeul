package com.example.bodeul.ui.auth;

/**
 * 인증 화면 상단 요약 카드에 표시할 문구 묶음이다.
 */
public final class AuthSummaryCardModel {
    private final String badgeText;
    private final String titleText;
    private final String bodyText;

    public AuthSummaryCardModel(String badgeText, String titleText, String bodyText) {
        this.badgeText = badgeText;
        this.titleText = titleText;
        this.bodyText = bodyText;
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
}
