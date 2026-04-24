package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 후속 알림/감사 로그 섹션 전체를 표현한다.
 */
public final class AdminActionCenterScreenModel {
    private final String summaryText;
    private final String emptyText;
    private final List<AdminActionCenterFilterChipModel> filterChips;
    private final List<AdminActionCenterEntryModel> entryModels;

    public AdminActionCenterScreenModel(
            String summaryText,
            String emptyText,
            List<AdminActionCenterFilterChipModel> filterChips,
            List<AdminActionCenterEntryModel> entryModels
    ) {
        this.summaryText = summaryText;
        this.emptyText = emptyText;
        this.filterChips = filterChips;
        this.entryModels = entryModels;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getEmptyText() {
        return emptyText;
    }

    public List<AdminActionCenterFilterChipModel> getFilterChips() {
        return filterChips;
    }

    public List<AdminActionCenterEntryModel> getEntryModels() {
        return entryModels;
    }
}
