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
    private final List<HealthInfoLineItem> serviceLines;
    private final String bookingActionLabel;
    private final String bookingStatusActionLabel;
    private final String guardianReportActionLabel;
    private final String supportActionLabel;
    private final String accountSectionTitle;
    private final String accountSectionHelper;
    private final String profileSectionTitle;
    private final String profileSectionHelper;
    private final String requestSectionTitle;
    private final String requestSectionHelper;
    private final String historySectionTitle;
    private final String historySectionHelper;
    private final String historyActionLabel;
    private final String supportSectionTitle;
    private final String supportSectionHelper;
    private final List<HealthInfoLineItem> accountLines;
    private final List<HealthInfoLineItem> profileLines;
    private final List<HealthInfoLineItem> requestLines;
    private final List<HealthInfoLineItem> historyLines;
    private final List<HealthInfoLineItem> supportLines;
    private final HealthInfoPrimaryActionType primaryActionType;
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
            List<HealthInfoLineItem> serviceLines,
            String bookingActionLabel,
            String bookingStatusActionLabel,
            String guardianReportActionLabel,
            String supportActionLabel,
            String accountSectionTitle,
            String accountSectionHelper,
            String profileSectionTitle,
            String profileSectionHelper,
            String requestSectionTitle,
            String requestSectionHelper,
            String historySectionTitle,
            String historySectionHelper,
            String historyActionLabel,
            String supportSectionTitle,
            String supportSectionHelper,
            List<HealthInfoLineItem> accountLines,
            List<HealthInfoLineItem> profileLines,
            List<HealthInfoLineItem> requestLines,
            List<HealthInfoLineItem> historyLines,
            List<HealthInfoLineItem> supportLines,
            HealthInfoPrimaryActionType primaryActionType,
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
        this.serviceLines = serviceLines;
        this.bookingActionLabel = bookingActionLabel;
        this.bookingStatusActionLabel = bookingStatusActionLabel;
        this.guardianReportActionLabel = guardianReportActionLabel;
        this.supportActionLabel = supportActionLabel;
        this.accountSectionTitle = accountSectionTitle;
        this.accountSectionHelper = accountSectionHelper;
        this.profileSectionTitle = profileSectionTitle;
        this.profileSectionHelper = profileSectionHelper;
        this.requestSectionTitle = requestSectionTitle;
        this.requestSectionHelper = requestSectionHelper;
        this.historySectionTitle = historySectionTitle;
        this.historySectionHelper = historySectionHelper;
        this.historyActionLabel = historyActionLabel;
        this.supportSectionTitle = supportSectionTitle;
        this.supportSectionHelper = supportSectionHelper;
        this.accountLines = accountLines;
        this.profileLines = profileLines;
        this.requestLines = requestLines;
        this.historyLines = historyLines;
        this.supportLines = supportLines;
        this.primaryActionType = primaryActionType;
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

    public List<HealthInfoLineItem> getServiceLines() {
        return serviceLines;
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

    public String getSupportActionLabel() {
        return supportActionLabel;
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

    public String getHistorySectionTitle() {
        return historySectionTitle;
    }

    public String getHistorySectionHelper() {
        return historySectionHelper;
    }

    public String getHistoryActionLabel() {
        return historyActionLabel;
    }

    public String getSupportSectionTitle() {
        return supportSectionTitle;
    }

    public String getSupportSectionHelper() {
        return supportSectionHelper;
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

    public List<HealthInfoLineItem> getHistoryLines() {
        return historyLines;
    }

    public List<HealthInfoLineItem> getSupportLines() {
        return supportLines;
    }

    public HealthInfoPrimaryActionType getPrimaryActionType() {
        return primaryActionType;
    }

    public String getPrimaryActionLabel() {
        return primaryActionLabel;
    }
}
