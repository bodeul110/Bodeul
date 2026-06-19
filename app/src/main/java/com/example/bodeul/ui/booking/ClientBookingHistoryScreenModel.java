package com.example.bodeul.ui.booking;

import java.util.Collections;
import java.util.List;

public final class ClientBookingHistoryScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String listSectionTitle;
    private final String listSectionHelper;
    private final String manageActionLabel;
    private final List<ClientBookingHistoryEntryModel> entries;

    public ClientBookingHistoryScreenModel(
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String listSectionTitle,
            String listSectionHelper,
            String manageActionLabel,
            List<ClientBookingHistoryEntryModel> entries
    ) {
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.listSectionTitle = listSectionTitle;
        this.listSectionHelper = listSectionHelper;
        this.manageActionLabel = manageActionLabel;
        this.entries = Collections.unmodifiableList(entries);
    }

    public String getModeLabel() { return modeLabel; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getHeroBadge() { return heroBadge; }
    public String getHeroTitle() { return heroTitle; }
    public String getHeroBody() { return heroBody; }
    public String getListSectionTitle() { return listSectionTitle; }
    public String getListSectionHelper() { return listSectionHelper; }
    public String getManageActionLabel() { return manageActionLabel; }
    public List<ClientBookingHistoryEntryModel> getEntries() { return entries; }
}
