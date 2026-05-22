package com.example.bodeul.ui.health;

import java.util.List;

public final class HealthInfoScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String profileSectionTitle;
    private final String requestSectionTitle;
    private final List<HealthInfoLineItem> profileLines;
    private final List<HealthInfoLineItem> requestLines;
    private final String primaryActionLabel;

    public HealthInfoScreenModel(
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String profileSectionTitle,
            String requestSectionTitle,
            List<HealthInfoLineItem> profileLines,
            List<HealthInfoLineItem> requestLines,
            String primaryActionLabel
    ) {
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.profileSectionTitle = profileSectionTitle;
        this.requestSectionTitle = requestSectionTitle;
        this.profileLines = profileLines;
        this.requestLines = requestLines;
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

    public String getProfileSectionTitle() {
        return profileSectionTitle;
    }

    public String getRequestSectionTitle() {
        return requestSectionTitle;
    }

    public List<HealthInfoLineItem> getProfileLines() {
        return profileLines;
    }

    public List<HealthInfoLineItem> getRequestLines() {
        return requestLines;
    }

    public String getPrimaryActionLabel() {
        return primaryActionLabel;
    }
}
