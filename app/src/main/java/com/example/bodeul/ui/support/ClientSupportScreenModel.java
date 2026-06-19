package com.example.bodeul.ui.support;

import java.util.List;

/**
 * 사용자 문의 화면 전체를 표현하는 모델이다.
 */
public final class ClientSupportScreenModel {
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String requestSummaryText;
    private final String latestSummaryText;
    private final List<ClientSupportRequestCardModel> requestCards;
    private final String focusedSupportRequestId;

    public ClientSupportScreenModel(
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String requestSummaryText,
            String latestSummaryText,
            List<ClientSupportRequestCardModel> requestCards,
            String focusedSupportRequestId
    ) {
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText;
        this.heroBodyText = heroBodyText;
        this.requestSummaryText = requestSummaryText;
        this.latestSummaryText = latestSummaryText;
        this.requestCards = requestCards;
        this.focusedSupportRequestId = focusedSupportRequestId;
    }

    public String getModeText() {
        return modeText;
    }

    public String getHeroBadgeText() {
        return heroBadgeText;
    }

    public String getHeroTitleText() {
        return heroTitleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getRequestSummaryText() {
        return requestSummaryText;
    }

    public String getLatestSummaryText() {
        return latestSummaryText;
    }

    public List<ClientSupportRequestCardModel> getRequestCards() {
        return requestCards;
    }

    public String getFocusedSupportRequestId() {
        return focusedSupportRequestId;
    }
}
