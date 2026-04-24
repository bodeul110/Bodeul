package com.example.bodeul.ui.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 과거 동행 이력 화면 전체를 그리기 위한 표현 모델이다.
 */
public final class ManagerHistoryScreenModel {
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String summaryText;
    private final String listHelperText;
    private final String emptyText;
    private final List<ManagerHistoryMetricModel> metricCards;
    private final List<ManagerHistoryFilterChipModel> filterChips;
    private final List<ManagerHistoryEntryCardModel> entryCards;

    public ManagerHistoryScreenModel(
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String summaryText,
            String listHelperText,
            String emptyText,
            List<ManagerHistoryMetricModel> metricCards,
            List<ManagerHistoryFilterChipModel> filterChips,
            List<ManagerHistoryEntryCardModel> entryCards
    ) {
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText;
        this.heroBodyText = heroBodyText;
        this.summaryText = summaryText;
        this.listHelperText = listHelperText;
        this.emptyText = emptyText;
        this.metricCards = metricCards == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(metricCards));
        this.filterChips = filterChips == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(filterChips));
        this.entryCards = entryCards == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(entryCards));
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

    public String getSummaryText() {
        return summaryText;
    }

    public String getListHelperText() {
        return listHelperText;
    }

    public String getEmptyText() {
        return emptyText;
    }

    public List<ManagerHistoryMetricModel> getMetricCards() {
        return metricCards;
    }

    public List<ManagerHistoryFilterChipModel> getFilterChips() {
        return filterChips;
    }

    public List<ManagerHistoryEntryCardModel> getEntryCards() {
        return entryCards;
    }
}
