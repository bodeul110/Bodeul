package com.example.bodeul.ui.booking;

import java.util.List;

/**
 * 실시간 위치 확인 화면에 필요한 표시 데이터를 한 번에 전달한다.
 */
public final class BookingLiveLocationScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String statusSectionTitle;
    private final String memoSectionTitle;
    private final String mapSectionTitle;
    private final String mapSectionHelper;
    private final List<BookingStatusLineItem> statusLines;
    private final List<BookingStatusLineItem> memoLines;
    private final List<BookingLiveLocationMapActionModel> mapActions;
    private final String primaryActionLabel;

    public BookingLiveLocationScreenModel(
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String statusSectionTitle,
            String memoSectionTitle,
            String mapSectionTitle,
            String mapSectionHelper,
            List<BookingStatusLineItem> statusLines,
            List<BookingStatusLineItem> memoLines,
            List<BookingLiveLocationMapActionModel> mapActions,
            String primaryActionLabel
    ) {
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.statusSectionTitle = statusSectionTitle;
        this.memoSectionTitle = memoSectionTitle;
        this.mapSectionTitle = mapSectionTitle;
        this.mapSectionHelper = mapSectionHelper;
        this.statusLines = statusLines;
        this.memoLines = memoLines;
        this.mapActions = mapActions;
        this.primaryActionLabel = primaryActionLabel;
    }

    public String getModeLabel() {
        return modeLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroBadge() {
        return heroBadge;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public String getHeroBody() {
        return heroBody;
    }

    public String getStatusSectionTitle() {
        return statusSectionTitle;
    }

    public String getMemoSectionTitle() {
        return memoSectionTitle;
    }

    public String getMapSectionTitle() {
        return mapSectionTitle;
    }

    public String getMapSectionHelper() {
        return mapSectionHelper;
    }

    public List<BookingStatusLineItem> getStatusLines() {
        return statusLines;
    }

    public List<BookingStatusLineItem> getMemoLines() {
        return memoLines;
    }

    public List<BookingLiveLocationMapActionModel> getMapActions() {
        return mapActions;
    }

    public String getPrimaryActionLabel() {
        return primaryActionLabel;
    }
}
