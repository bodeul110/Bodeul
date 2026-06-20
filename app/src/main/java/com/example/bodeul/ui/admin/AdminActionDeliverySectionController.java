package com.example.bodeul.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminDashboard;

/**
 * 관리자 액션 전달 섹션의 요약과 목록 렌더링을 분리한다.
 */
final class AdminActionDeliverySectionController {
    interface Listener {
        void renderEmptyText(LinearLayout container, int titleResId, int messageResId);
    }

    private final LayoutInflater inflater;
    private final TextView summaryView;
    private final LinearLayout container;
    private final AdminActionDeliveryCoordinator coordinator;
    private final AdminActionDeliveryCardBinder cardBinder;
    private final Listener listener;

    AdminActionDeliverySectionController(
            LayoutInflater inflater,
            TextView summaryView,
            LinearLayout container,
            AdminActionDeliveryCoordinator coordinator,
            AdminActionDeliveryCardBinder cardBinder,
            Listener listener
    ) {
        this.inflater = inflater;
        this.summaryView = summaryView;
        this.container = container;
        this.coordinator = coordinator;
        this.cardBinder = cardBinder;
        this.listener = listener;
    }

    void bind(AdminDashboard dashboard) {
        AdminActionDeliveryDashboardModel deliveryModel =
                coordinator.createDashboardModel(
                        dashboard.getActionDeliveries(),
                        dashboard.getActionOverview()
                );
        summaryView.setText(deliveryModel.getSummaryText());
        container.removeAllViews();
        if (deliveryModel.getCardModels().isEmpty()) {
            listener.renderEmptyText(
                    container,
                    R.string.admin_action_delivery_title,
                    R.string.admin_action_delivery_empty
            );
            return;
        }

        for (AdminActionDeliveryCardModel cardModel : deliveryModel.getCardModels()) {
            View itemView = inflater.inflate(
                    R.layout.item_admin_action_delivery_entry,
                    container,
                    false
            );
            cardBinder.bind(itemView, cardModel);
            container.addView(itemView);
        }
    }

    void clear() {
        summaryView.setText(R.string.admin_action_delivery_summary_empty);
        container.removeAllViews();
        listener.renderEmptyText(
                container,
                R.string.admin_action_delivery_title,
                R.string.admin_action_delivery_empty
        );
    }
}
