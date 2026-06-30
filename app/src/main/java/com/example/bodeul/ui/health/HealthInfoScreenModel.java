package com.example.bodeul.ui.health;

import com.example.bodeul.ui.common.AttentionBannerModel;

import java.util.List;

public final class HealthInfoScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final AttentionBannerModel supportBanner;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String serviceTabLabel;
    private final String profileTabLabel;
    private final String supportTabLabel;
    private final boolean supportTabHighlighted;
    private final String serviceSectionTitle;
    private final String serviceSectionHelper;
    private final List<HealthInfoLineItem> serviceLines;
    private final String bookingActionLabel;
    private final String bookingStatusActionLabel;
    private final String guardianReportActionLabel;
    private final String supportActionLabel;
    private final String linkedProfileSectionTitle;
    private final String linkedProfileSectionHelper;
    private final String profileSectionTitle;
    private final String profileSectionHelper;
    private final String requestSectionTitle;
    private final String requestSectionHelper;
    private final String historySectionTitle;
    private final String historySectionHelper;
    private final String historyActionLabel;
    private final String supportSectionTitle;
    private final String supportSectionHelper;
    private final List<HealthInfoLineItem> linkedProfileLines;
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
            AttentionBannerModel supportBanner,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String serviceTabLabel,
            String profileTabLabel,
            String supportTabLabel,
            boolean supportTabHighlighted,
            String serviceSectionTitle,
            String serviceSectionHelper,
            List<HealthInfoLineItem> serviceLines,
            String bookingActionLabel,
            String bookingStatusActionLabel,
            String guardianReportActionLabel,
            String supportActionLabel,
            String linkedProfileSectionTitle,
            String linkedProfileSectionHelper,
            String profileSectionTitle,
            String profileSectionHelper,
            String requestSectionTitle,
            String requestSectionHelper,
            String historySectionTitle,
            String historySectionHelper,
            String historyActionLabel,
            String supportSectionTitle,
            String supportSectionHelper,
            List<HealthInfoLineItem> linkedProfileLines,
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
        this.supportBanner = supportBanner;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.serviceTabLabel = serviceTabLabel;
        this.profileTabLabel = profileTabLabel;
        this.supportTabLabel = supportTabLabel;
        this.supportTabHighlighted = supportTabHighlighted;
        this.serviceSectionTitle = serviceSectionTitle;
        this.serviceSectionHelper = serviceSectionHelper;
        this.serviceLines = serviceLines;
        this.bookingActionLabel = bookingActionLabel;
        this.bookingStatusActionLabel = bookingStatusActionLabel;
        this.guardianReportActionLabel = guardianReportActionLabel;
        this.supportActionLabel = supportActionLabel;
        this.linkedProfileSectionTitle = linkedProfileSectionTitle;
        this.linkedProfileSectionHelper = linkedProfileSectionHelper;
        this.profileSectionTitle = profileSectionTitle;
        this.profileSectionHelper = profileSectionHelper;
        this.requestSectionTitle = requestSectionTitle;
        this.requestSectionHelper = requestSectionHelper;
        this.historySectionTitle = historySectionTitle;
        this.historySectionHelper = historySectionHelper;
        this.historyActionLabel = historyActionLabel;
        this.supportSectionTitle = supportSectionTitle;
        this.supportSectionHelper = supportSectionHelper;
        this.linkedProfileLines = linkedProfileLines;
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

    public AttentionBannerModel getSupportBanner() {
        return supportBanner;
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

    public String getServiceTabLabel() {
        return serviceTabLabel;
    }

    public String getProfileTabLabel() {
        return profileTabLabel;
    }

    public String getSupportTabLabel() {
        return supportTabLabel;
    }

    public boolean isSupportTabHighlighted() {
        return supportTabHighlighted;
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

    public String getLinkedProfileSectionTitle() {
        return linkedProfileSectionTitle;
    }

    public String getLinkedProfileSectionHelper() {
        return linkedProfileSectionHelper;
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

    public List<HealthInfoLineItem> getLinkedProfileLines() {
        return linkedProfileLines;
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
