package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 운영 요청 섹션의 요약, 필터, 카드 목록을 묶는다.
 */
public final class AdminManagedRequestSectionModel {
    private final String summaryText;
    private final List<AdminManagedFilterChipModel> statusFilterChips;
    private final List<AdminManagedDateFilterChipModel> dateFilterChips;
    private final List<AdminRequestCardModel> requestCards;
    private final boolean hasRequests;

    public AdminManagedRequestSectionModel(
            String summaryText,
            List<AdminManagedFilterChipModel> statusFilterChips,
            List<AdminManagedDateFilterChipModel> dateFilterChips,
            List<AdminRequestCardModel> requestCards,
            boolean hasRequests
    ) {
        this.summaryText = summaryText;
        this.statusFilterChips = statusFilterChips;
        this.dateFilterChips = dateFilterChips;
        this.requestCards = requestCards;
        this.hasRequests = hasRequests;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public List<AdminManagedFilterChipModel> getStatusFilterChips() {
        return statusFilterChips;
    }

    public List<AdminManagedDateFilterChipModel> getDateFilterChips() {
        return dateFilterChips;
    }

    public List<AdminRequestCardModel> getRequestCards() {
        return requestCards;
    }

    public boolean hasRequests() {
        return hasRequests;
    }
}
