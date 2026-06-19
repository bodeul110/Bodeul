package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 문의 응답 섹션의 요약과 카드 목록을 묶는다.
 */
public final class AdminSupportDashboardModel {
    private final String summaryText;
    private final List<AdminSupportSourceFilterChipModel> sourceFilterChips;
    private final List<AdminSupportStatusFilterChipModel> statusFilterChips;
    private final List<AdminSupportInquiryCardModel> inquiryCards;

    public AdminSupportDashboardModel(
            String summaryText,
            List<AdminSupportSourceFilterChipModel> sourceFilterChips,
            List<AdminSupportStatusFilterChipModel> statusFilterChips,
            List<AdminSupportInquiryCardModel> inquiryCards
    ) {
        this.summaryText = summaryText;
        this.sourceFilterChips = sourceFilterChips;
        this.statusFilterChips = statusFilterChips;
        this.inquiryCards = inquiryCards;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public List<AdminSupportSourceFilterChipModel> getSourceFilterChips() {
        return sourceFilterChips;
    }

    public List<AdminSupportStatusFilterChipModel> getStatusFilterChips() {
        return statusFilterChips;
    }

    public List<AdminSupportInquiryCardModel> getInquiryCards() {
        return inquiryCards;
    }
}
