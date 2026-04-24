package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 매니저 홈 전체 화면에 필요한 표시 데이터를 한 번에 전달한다.
 */
public final class ManagerHomeScreenModel {
    private final String modeText;
    private final String greetingText;
    private final String subtitleText;
    private final ManagerHomeHeroModel heroModel;
    private final List<ManagerHomeActionCardModel> actionCards;
    private final String sectionTitleText;
    private final String sectionActionText;
    private final boolean sectionActionVisible;
    private final List<ManagerHomePromoCardModel> promoCards;
    @Nullable
    private final ManagerHomeLiveFeedModel liveFeedModel;
    private final boolean hasActiveSession;

    public ManagerHomeScreenModel(
            String modeText,
            String greetingText,
            String subtitleText,
            ManagerHomeHeroModel heroModel,
            List<ManagerHomeActionCardModel> actionCards,
            String sectionTitleText,
            String sectionActionText,
            boolean sectionActionVisible,
            List<ManagerHomePromoCardModel> promoCards,
            @Nullable ManagerHomeLiveFeedModel liveFeedModel,
            boolean hasActiveSession
    ) {
        this.modeText = modeText;
        this.greetingText = greetingText;
        this.subtitleText = subtitleText;
        this.heroModel = heroModel;
        this.actionCards = Collections.unmodifiableList(actionCards);
        this.sectionTitleText = sectionTitleText;
        this.sectionActionText = sectionActionText;
        this.sectionActionVisible = sectionActionVisible;
        this.promoCards = Collections.unmodifiableList(promoCards);
        this.liveFeedModel = liveFeedModel;
        this.hasActiveSession = hasActiveSession;
    }

    public String getModeText() {
        return modeText;
    }

    public String getGreetingText() {
        return greetingText;
    }

    public String getSubtitleText() {
        return subtitleText;
    }

    public ManagerHomeHeroModel getHeroModel() {
        return heroModel;
    }

    public List<ManagerHomeActionCardModel> getActionCards() {
        return actionCards;
    }

    public String getSectionTitleText() {
        return sectionTitleText;
    }

    public String getSectionActionText() {
        return sectionActionText;
    }

    public boolean isSectionActionVisible() {
        return sectionActionVisible;
    }

    public List<ManagerHomePromoCardModel> getPromoCards() {
        return promoCards;
    }

    @Nullable
    public ManagerHomeLiveFeedModel getLiveFeedModel() {
        return liveFeedModel;
    }

    public boolean hasActiveSession() {
        return hasActiveSession;
    }
}
