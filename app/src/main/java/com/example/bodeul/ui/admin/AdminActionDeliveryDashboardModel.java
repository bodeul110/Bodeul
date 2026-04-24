package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 운영 알림 전송 현황 섹션 전체를 표현한다.
 */
public final class AdminActionDeliveryDashboardModel {
    private final String summaryText;
    private final List<AdminActionDeliveryCardModel> cardModels;

    public AdminActionDeliveryDashboardModel(
            String summaryText,
            List<AdminActionDeliveryCardModel> cardModels
    ) {
        this.summaryText = summaryText == null ? "" : summaryText;
        this.cardModels = cardModels == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cardModels));
    }

    public String getSummaryText() {
        return summaryText;
    }

    public List<AdminActionDeliveryCardModel> getCardModels() {
        return cardModels;
    }
}
