package com.example.bodeul.ui.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 지원팀 문의 화면 전체를 표현하는 모델이다.
 */
public final class ManagerSupportScreenModel {
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String latestSummaryText;
    private final List<ManagerSupportInquiryCardModel> inquiryCards;

    public ManagerSupportScreenModel(
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String latestSummaryText,
            List<ManagerSupportInquiryCardModel> inquiryCards
    ) {
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText;
        this.heroBodyText = heroBodyText;
        this.latestSummaryText = latestSummaryText;
        this.inquiryCards = inquiryCards == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(inquiryCards));
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

    public String getLatestSummaryText() {
        return latestSummaryText;
    }

    public List<ManagerSupportInquiryCardModel> getInquiryCards() {
        return inquiryCards;
    }
}
