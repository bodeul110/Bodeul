package com.example.bodeul.ui.admin;

import java.util.List;

/**
 * 관리자 운영 섹션의 요약, 경고, 필터, 카드 목록을 함께 담는다.
 */
public final class AdminOperationsDashboardModel {
    private final String monitoringSummaryText;
    private final String monitoringAlertText;
    private final List<AdminMonitoringFilterChipModel> monitoringFilterChips;
    private final List<AdminOperationCardModel> monitoringCards;
    private final boolean hasMonitoringTargets;
    private final String settlementSummaryText;
    private final String settlementAlertText;
    private final List<AdminSettlementFilterChipModel> settlementFilterChips;
    private final List<AdminOperationCardModel> settlementCards;
    private final boolean hasSettlementTargets;

    public AdminOperationsDashboardModel(
            String monitoringSummaryText,
            String monitoringAlertText,
            List<AdminMonitoringFilterChipModel> monitoringFilterChips,
            List<AdminOperationCardModel> monitoringCards,
            boolean hasMonitoringTargets,
            String settlementSummaryText,
            String settlementAlertText,
            List<AdminSettlementFilterChipModel> settlementFilterChips,
            List<AdminOperationCardModel> settlementCards,
            boolean hasSettlementTargets
    ) {
        this.monitoringSummaryText = monitoringSummaryText;
        this.monitoringAlertText = monitoringAlertText;
        this.monitoringFilterChips = monitoringFilterChips;
        this.monitoringCards = monitoringCards;
        this.hasMonitoringTargets = hasMonitoringTargets;
        this.settlementSummaryText = settlementSummaryText;
        this.settlementAlertText = settlementAlertText;
        this.settlementFilterChips = settlementFilterChips;
        this.settlementCards = settlementCards;
        this.hasSettlementTargets = hasSettlementTargets;
    }

    public String getMonitoringSummaryText() {
        return monitoringSummaryText;
    }

    public String getMonitoringAlertText() {
        return monitoringAlertText;
    }

    public List<AdminMonitoringFilterChipModel> getMonitoringFilterChips() {
        return monitoringFilterChips;
    }

    public List<AdminOperationCardModel> getMonitoringCards() {
        return monitoringCards;
    }

    public boolean hasMonitoringTargets() {
        return hasMonitoringTargets;
    }

    public String getSettlementSummaryText() {
        return settlementSummaryText;
    }

    public String getSettlementAlertText() {
        return settlementAlertText;
    }

    public List<AdminSettlementFilterChipModel> getSettlementFilterChips() {
        return settlementFilterChips;
    }

    public List<AdminOperationCardModel> getSettlementCards() {
        return settlementCards;
    }

    public boolean hasSettlementTargets() {
        return hasSettlementTargets;
    }
}
