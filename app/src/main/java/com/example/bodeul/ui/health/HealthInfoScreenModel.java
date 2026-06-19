package com.example.bodeul.ui.health;

import java.util.List;

public final class HealthInfoScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String serviceSectionTitle;
    private final String serviceSectionHelper;
    private final String bookingActionLabel;
    private final String bookingStatusActionLabel;
    private final String guardianReportActionLabel;
    private final String accountSectionTitle;
    private final String accountSectionHelper;
    private final String profileSectionTitle;
    private final String profileSectionHelper;
    private final String requestSectionTitle;
    private final String requestSectionHelper;
    private final List<HealthInfoLineItem> accountLines;
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
            String serviceSectionTitle,
            String serviceSectionHelper,
            String bookingActionLabel,
            String bookingStatusActionLabel,
            String guardianReportActionLabel,
            String accountSectionTitle,
            String accountSectionHelper,
            String profileSectionTitle,
            String profileSectionHelper,
            String requestSectionTitle,
            String requestSectionHelper,
            List<HealthInfoLineItem> accountLines,
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
        this.serviceSectionTitle = serviceSectionTitle;
        this.serviceSectionHelper = serviceSectionHelper;
        this.bookingActionLabel = bookingActionLabel;
        this.bookingStatusActionLabel = bookingStatusActionLabel;
        this.guardianReportActionLabel = guardianReportActionLabel;
        this.accountSectionTitle = accountSectionTitle;
        this.accountSectionHelper = accountSectionHelper;
        this.profileSectionTitle = profileSectionTitle;
        this.profileSectionHelper = profileSectionHelper;
        this.requestSectionTitle = requestSectionTitle;
        this.requestSectionHelper = requestSectionHelper;
        this.accountLines = accountLines;
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

    public String getServiceSectionTitle() {
        return serviceSectionTitle;
    }

    public String getServiceSectionHelper() {
        return serviceSectionHelper;
    }

    public String getBookingActionLabel() {
        return bookingActionLabel;
    }

    public String getBookingStatusActionLabel() {
        return bookingStatusActionLabel;
    }

    public String getGuardianReportActionLabel() {
        return guardianReportActionLabel;
    }

    public String getAccountSectionTitle() {
        return accountSectionTitle;
    }

    public String getAccountSectionHelper() {
        return accountSectionHelper;
    }

    public String getProfileSectionTitle() {
        return profileSectionTitle;
    }

    public String getProfileSectionHelper() {
        return profileSectionHelper;
    }

    public String getRequestSectionTitle() {
        return requestSectionTitle;
    }

    public String getRequestSectionHelper() {
        return requestSectionHelper;
    }

    public List<HealthInfoLineItem> getAccountLines() {
        return accountLines;
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
