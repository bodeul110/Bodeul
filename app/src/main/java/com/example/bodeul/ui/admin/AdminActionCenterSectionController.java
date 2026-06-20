package com.example.bodeul.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminDashboard;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 액션 센터의 요약, 필터, 목록 렌더링을 관리한다.
 */
final class AdminActionCenterSectionController {
    interface Listener {
        MaterialButton createFilterButton(String text, boolean selected);

        void renderEmptyText(
                LinearLayout container,
                CharSequence title,
                CharSequence message
        );

        void onMarkRead(String notificationId);

        void onMarkResolved(String notificationId);

        void onReopen(String notificationId);
    }

    private final AppCompatActivity activity;
    private final TextView summaryView;
    private final LinearLayout filterContainer;
    private final LinearLayout entryContainer;
    private final AdminActionCenterCoordinator coordinator;
    private final AdminActionCenterEntryBinder entryBinder;
    private final Listener listener;
    private AdminActionCenterFilter filter = AdminActionCenterFilter.ALL;

    AdminActionCenterSectionController(
            AppCompatActivity activity,
            TextView summaryView,
            LinearLayout filterContainer,
            LinearLayout entryContainer,
            AdminActionCenterCoordinator coordinator,
            AdminActionCenterEntryBinder entryBinder,
            Listener listener
    ) {
        this.activity = activity;
        this.summaryView = summaryView;
        this.filterContainer = filterContainer;
        this.entryContainer = entryContainer;
        this.coordinator = coordinator;
        this.entryBinder = entryBinder;
        this.listener = listener;
    }

    void bind(AdminDashboard dashboard) {
        renderActionCenter(dashboard);
    }

    void clear() {
        filter = AdminActionCenterFilter.ALL;
        summaryView.setText(R.string.admin_action_center_summary_empty);
        filterContainer.removeAllViews();
        filterContainer.setVisibility(View.GONE);
        entryContainer.removeAllViews();
    }

    void showEmptyPanel() {
        clear();
        listener.renderEmptyText(
                entryContainer,
                activity.getString(R.string.admin_action_center_title),
                activity.getString(R.string.admin_action_center_empty)
        );
    }

    private void renderActionCenter(AdminDashboard dashboard) {
        AdminActionCenterScreenModel screenModel = coordinator.createScreenModel(
                dashboard.getActionNotifications(),
                dashboard.getAuditLogs(),
                dashboard.getActionOverview(),
                filter
        );
        summaryView.setText(screenModel.getSummaryText());
        renderFilters(screenModel, dashboard);
        entryContainer.removeAllViews();
        if (screenModel.getEntryModels().isEmpty()) {
            listener.renderEmptyText(
                    entryContainer,
                    activity.getString(R.string.admin_action_center_title),
                    screenModel.getEmptyText()
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (AdminActionCenterEntryModel entryModel : screenModel.getEntryModels()) {
            View itemView = inflater.inflate(
                    R.layout.item_admin_action_center_entry,
                    entryContainer,
                    false
            );
            entryBinder.bind(itemView, entryModel, (entryId, actionType) -> {
                switch (actionType) {
                    case MARK_READ:
                        listener.onMarkRead(entryId);
                        return;
                    case MARK_RESOLVED:
                        listener.onMarkResolved(entryId);
                        return;
                    case REOPEN:
                    default:
                        listener.onReopen(entryId);
                }
            });
            entryContainer.addView(itemView);
        }
    }

    private void renderFilters(
            AdminActionCenterScreenModel screenModel,
            AdminDashboard dashboard
    ) {
        filterContainer.removeAllViews();
        if (screenModel.getFilterChips().isEmpty()) {
            filterContainer.setVisibility(View.GONE);
            return;
        }

        filterContainer.setVisibility(View.VISIBLE);
        for (AdminActionCenterFilterChipModel chipModel : screenModel.getFilterChips()) {
            MaterialButton button = listener.createFilterButton(
                    chipModel.getButtonText(),
                    chipModel.isSelected()
            );
            button.setOnClickListener(view -> {
                filter = chipModel.getFilter();
                renderActionCenter(dashboard);
            });
            filterContainer.addView(button);
        }
    }
}
