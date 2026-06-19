package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 문의 응답 섹션의 요약과 카드 목록을 묶는다.
 */
public final class AdminSupportDashboardModel {
    private final String summaryText;
    private final List<AdminSupportFilterChipModel> filterChips;
    private final List<AdminSupportInquiryCardModel> inquiryCards;

    public AdminSupportDashboardModel(
            String summaryText,
            List<AdminSupportFilterChipModel> filterChips,
            List<AdminSupportInquiryCardModel> inquiryCards
    ) {
        this.summaryText = summaryText;
        this.filterChips = filterChips;
        this.inquiryCards = inquiryCards;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public List<AdminSupportFilterChipModel> getFilterChips() {
        return filterChips;
    }

    public List<AdminSupportInquiryCardModel> getInquiryCards() {
        return inquiryCards;
    }
}
