package com.example.bodeul.ui.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 관리자 운영 섹션의 필터, 요약, 카드 렌더링 상태를 한 곳에서 관리한다.
 */
final class AdminOperationsSectionController {
    interface Listener {
        MaterialButton createFilterButton(String text, boolean selected);

        void renderEmptyText(LinearLayout container, int titleResId, int messageResId);

        void onSaveSettlementRecord(String requestId, AdminSettlementStatus status);

        void onSaveEmergencyIssue(String requestId, AdminEmergencyIssueStatus status);
    }

    private final LayoutInflater inflater;
    private final TextView monitoringSummaryView;
    private final TextView monitoringAlertView;
    private final LinearLayout monitoringFilterContainer;
    private final LinearLayout monitoringContainer;
    private final TextView settlementSummaryView;
    private final TextView settlementAlertView;
    private final LinearLayout settlementFilterContainer;
    private final LinearLayout settlementContainer;
    private final AdminOperationsCoordinator coordinator;
    private final AdminOperationCardBinder cardBinder;
    private final Listener listener;

    private AdminMonitoringFilter monitoringFilter = AdminMonitoringFilter.ALL;
    private AdminSettlementFilter settlementFilter = AdminSettlementFilter.ALL;
    private AdminDashboard dashboardSnapshot;

    AdminOperationsSectionController(
            LayoutInflater inflater,
            TextView monitoringSummaryView,
            TextView monitoringAlertView,
            LinearLayout monitoringFilterContainer,
            LinearLayout monitoringContainer,
            TextView settlementSummaryView,
            TextView settlementAlertView,
            LinearLayout settlementFilterContainer,
            LinearLayout settlementContainer,
            AdminOperationsCoordinator coordinator,
            AdminOperationCardBinder cardBinder,
            Listener listener
    ) {
        this.inflater = inflater;
        this.monitoringSummaryView = monitoringSummaryView;
        this.monitoringAlertView = monitoringAlertView;
        this.monitoringFilterContainer = monitoringFilterContainer;
        this.monitoringContainer = monitoringContainer;
        this.settlementSummaryView = settlementSummaryView;
        this.settlementAlertView = settlementAlertView;
        this.settlementFilterContainer = settlementFilterContainer;
        this.settlementContainer = settlementContainer;
        this.coordinator = coordinator;
        this.cardBinder = cardBinder;
        this.listener = listener;
    }

    void bind(AdminDashboard dashboard) {
        this.dashboardSnapshot = dashboard;
        AdminOperationsDashboardModel operationsModel =
                coordinator.createDashboardModel(
                        dashboard,
                        monitoringFilter,
                        settlementFilter
                );
        monitoringSummaryView.setText(operationsModel.getMonitoringSummaryText());
        bindAlert(monitoringAlertView, operationsModel.getMonitoringAlertText());
        renderMonitoringFilters(operationsModel);
        settlementSummaryView.setText(operationsModel.getSettlementSummaryText());
        bindAlert(settlementAlertView, operationsModel.getSettlementAlertText());
        renderSettlementFilters(operationsModel);
        renderOperationCards(
                monitoringContainer,
                operationsModel.getMonitoringCards(),
                R.string.admin_monitoring_title,
                R.string.admin_monitoring_empty
        );
        renderOperationCards(
                settlementContainer,
                operationsModel.getSettlementCards(),
                R.string.admin_settlement_title,
                R.string.admin_settlement_empty
        );
    }

    void clear() {
        dashboardSnapshot = null;
        monitoringFilter = AdminMonitoringFilter.ALL;
        settlementFilter = AdminSettlementFilter.ALL;
        monitoringSummaryView.setText(R.string.admin_monitoring_summary_empty);
        monitoringAlertView.setVisibility(View.GONE);
        settlementSummaryView.setText(R.string.admin_settlement_summary_empty);
        settlementAlertView.setVisibility(View.GONE);
        monitoringFilterContainer.removeAllViews();
        monitoringFilterContainer.setVisibility(View.GONE);
        settlementFilterContainer.removeAllViews();
        settlementFilterContainer.setVisibility(View.GONE);
        monitoringContainer.removeAllViews();
        settlementContainer.removeAllViews();
        listener.renderEmptyText(
                monitoringContainer,
                R.string.admin_monitoring_title,
                R.string.admin_monitoring_empty
        );
        listener.renderEmptyText(
                settlementContainer,
                R.string.admin_settlement_title,
                R.string.admin_settlement_empty
        );
    }

    private void bindAlert(TextView alertView, String alertText) {
        if (TextUtils.isEmpty(alertText)) {
            alertView.setVisibility(View.GONE);
            return;
        }
        alertView.setVisibility(View.VISIBLE);
        alertView.setText(alertText);
    }

    private void renderMonitoringFilters(AdminOperationsDashboardModel operationsModel) {
        monitoringFilterContainer.removeAllViews();
        if (!operationsModel.hasMonitoringTargets()) {
            monitoringFilterContainer.setVisibility(View.GONE);
            return;
        }

        monitoringFilterContainer.setVisibility(View.VISIBLE);
        for (AdminMonitoringFilterChipModel chipModel : operationsModel.getMonitoringFilterChips()) {
            MaterialButton button = listener.createFilterButton(
                    chipModel.getButtonText(),
                    chipModel.isSelected()
            );
            button.setOnClickListener(view -> {
                monitoringFilter = chipModel.getFilter();
                if (dashboardSnapshot != null) {
                    bind(dashboardSnapshot);
                }
            });
            monitoringFilterContainer.addView(button);
        }
    }

    private void renderSettlementFilters(AdminOperationsDashboardModel operationsModel) {
        settlementFilterContainer.removeAllViews();
        if (!operationsModel.hasSettlementTargets()) {
            settlementFilterContainer.setVisibility(View.GONE);
            return;
        }

        settlementFilterContainer.setVisibility(View.VISIBLE);
        for (AdminSettlementFilterChipModel chipModel : operationsModel.getSettlementFilterChips()) {
            MaterialButton button = listener.createFilterButton(
                    chipModel.getButtonText(),
                    chipModel.isSelected()
            );
            button.setOnClickListener(view -> {
                settlementFilter = chipModel.getFilter();
                if (dashboardSnapshot != null) {
                    bind(dashboardSnapshot);
                }
            });
            settlementFilterContainer.addView(button);
        }
    }

    private void renderOperationCards(
            LinearLayout container,
            List<AdminOperationCardModel> cardModels,
            int titleResId,
            int emptyResId
    ) {
        container.removeAllViews();
        if (cardModels.isEmpty()) {
            listener.renderEmptyText(container, titleResId, emptyResId);
            return;
        }

        for (AdminOperationCardModel cardModel : cardModels) {
            View itemView = inflater.inflate(R.layout.item_admin_operation_card, container, false);
            cardBinder.bind(itemView, cardModel, (requestId, actionType) -> {
                if (actionType == AdminOperationActionType.SAVE_SETTLEMENT_CONFIRMED) {
                    listener.onSaveSettlementRecord(requestId, AdminSettlementStatus.CONFIRMED);
                    return;
                }
                if (actionType == AdminOperationActionType.SAVE_SETTLEMENT_RECHECK) {
                    listener.onSaveSettlementRecord(requestId, AdminSettlementStatus.NEEDS_REVIEW);
                    return;
                }
                if (actionType == AdminOperationActionType.RESOLVE_EMERGENCY) {
                    listener.onSaveEmergencyIssue(requestId, AdminEmergencyIssueStatus.RESOLVED);
                    return;
                }
                listener.onSaveEmergencyIssue(requestId, AdminEmergencyIssueStatus.REPORTED);
            });
            container.addView(itemView);
        }
    }
}
