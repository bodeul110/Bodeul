package com.example.bodeul.ui.admin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 요청 관리 섹션의 필터, 확장 상태, 카드 렌더링을 한 곳에서 관리한다.
 */
final class AdminRequestSectionController {
    interface Listener {
        void onAssignManager(String requestId, String managerUserId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView managedSummaryView;
    private final LinearLayout pendingContainer;
    private final LinearLayout managedFilterContainer;
    private final LinearLayout managedDateFilterContainer;
    private final LinearLayout managedContainer;
    private final AdminRequestCoordinator requestCoordinator;
    private final AdminRequestCardBinder requestCardBinder;
    private final Listener listener;

    private final List<AdminRequestOverview> pendingRequestsSnapshot = new ArrayList<>();
    private final List<AdminRequestOverview> managedRequestsSnapshot = new ArrayList<>();
    private final List<User> availableManagersSnapshot = new ArrayList<>();
    private final Set<String> expandedRequestIds = new HashSet<>();

    private AdminManagedRequestFilter managedRequestFilter = AdminManagedRequestFilter.ALL;
    private AdminManagedRequestDateFilter managedRequestDateFilter = AdminManagedRequestDateFilter.ALL;

    AdminRequestSectionController(
            Context context,
            LayoutInflater inflater,
            TextView managedSummaryView,
            LinearLayout pendingContainer,
            LinearLayout managedFilterContainer,
            LinearLayout managedDateFilterContainer,
            LinearLayout managedContainer,
            AdminRequestCoordinator requestCoordinator,
            AdminRequestCardBinder requestCardBinder,
            Listener listener
    ) {
        this.context = context;
        this.inflater = inflater;
        this.managedSummaryView = managedSummaryView;
        this.pendingContainer = pendingContainer;
        this.managedFilterContainer = managedFilterContainer;
        this.managedDateFilterContainer = managedDateFilterContainer;
        this.managedContainer = managedContainer;
        this.requestCoordinator = requestCoordinator;
        this.requestCardBinder = requestCardBinder;
        this.listener = listener;
    }

    void bindRequests(
            List<AdminRequestOverview> pendingRequests,
            List<AdminRequestOverview> managedRequests,
            List<User> availableManagers
    ) {
        pendingRequestsSnapshot.clear();
        pendingRequestsSnapshot.addAll(pendingRequests);
        managedRequestsSnapshot.clear();
        managedRequestsSnapshot.addAll(managedRequests);
        availableManagersSnapshot.clear();
        availableManagersSnapshot.addAll(availableManagers);
        renderPendingRequests();
        renderManagedRequests();
    }

    void clear() {
        pendingRequestsSnapshot.clear();
        managedRequestsSnapshot.clear();
        availableManagersSnapshot.clear();
        expandedRequestIds.clear();
        managedRequestFilter = AdminManagedRequestFilter.ALL;
        managedRequestDateFilter = AdminManagedRequestDateFilter.ALL;

        managedSummaryView.setText(R.string.admin_managed_summary_empty);
        managedFilterContainer.removeAllViews();
        managedFilterContainer.setVisibility(View.GONE);
        managedDateFilterContainer.removeAllViews();
        managedDateFilterContainer.setVisibility(View.GONE);
        pendingContainer.removeAllViews();
        managedContainer.removeAllViews();
        renderEmptyText(pendingContainer, R.string.admin_pending_title, R.string.admin_pending_empty);
        renderEmptyText(managedContainer, R.string.admin_managed_title, R.string.admin_managed_empty);
    }

    private void renderPendingRequests() {
        List<AdminRequestCardModel> cardModels = requestCoordinator.createPendingCards(
                pendingRequestsSnapshot,
                availableManagersSnapshot,
                expandedRequestIds
        );
        renderRequestCards(
                pendingContainer,
                cardModels,
                R.string.admin_pending_title,
                R.string.admin_pending_empty
        );
    }

    private void renderManagedRequests() {
        AdminManagedRequestSectionModel sectionModel = requestCoordinator.createManagedSectionModel(
                managedRequestsSnapshot,
                managedRequestFilter,
                managedRequestDateFilter,
                expandedRequestIds
        );
        renderManagedFilters(sectionModel);
        renderManagedDateFilters(sectionModel);
        managedSummaryView.setText(sectionModel.getSummaryText());
        managedContainer.removeAllViews();
        if (!sectionModel.hasRequests()) {
            renderEmptyText(
                    managedContainer,
                    R.string.admin_managed_title,
                    R.string.admin_managed_empty
            );
            return;
        }
        if (sectionModel.getRequestCards().isEmpty()) {
            renderEmptyText(
                    managedContainer,
                    R.string.admin_managed_title,
                    R.string.admin_managed_filtered_empty
            );
            return;
        }
        renderRequestCards(
                managedContainer,
                sectionModel.getRequestCards(),
                R.string.admin_managed_title,
                R.string.admin_managed_filtered_empty
        );
    }

    private void renderManagedFilters(AdminManagedRequestSectionModel sectionModel) {
        managedFilterContainer.removeAllViews();
        if (!sectionModel.hasRequests()) {
            managedFilterContainer.setVisibility(View.GONE);
            return;
        }

        managedFilterContainer.setVisibility(View.VISIBLE);
        for (AdminManagedFilterChipModel chipModel : sectionModel.getStatusFilterChips()) {
            MaterialButton button = createFilterButton(chipModel.getButtonText(), chipModel.isSelected());
            button.setOnClickListener(view -> {
                managedRequestFilter = chipModel.getFilter();
                renderManagedRequests();
            });
            managedFilterContainer.addView(button);
        }
    }

    private void renderManagedDateFilters(AdminManagedRequestSectionModel sectionModel) {
        managedDateFilterContainer.removeAllViews();
        if (!sectionModel.hasRequests()) {
            managedDateFilterContainer.setVisibility(View.GONE);
            return;
        }

        managedDateFilterContainer.setVisibility(View.VISIBLE);
        for (AdminManagedDateFilterChipModel chipModel : sectionModel.getDateFilterChips()) {
            MaterialButton button = createFilterButton(chipModel.getButtonText(), chipModel.isSelected());
            button.setOnClickListener(view -> {
                managedRequestDateFilter = chipModel.getFilter();
                renderManagedRequests();
            });
            managedDateFilterContainer.addView(button);
        }
    }

    private MaterialButton createFilterButton(String buttonText, boolean selected) {
        MaterialButton button = new MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dpToPx(8));
        button.setLayoutParams(params);
        button.setAllCaps(false);
        button.setCornerRadius(dpToPx(18));
        button.setText(buttonText);
        bindFilterButtonStyle(button, selected);
        return button;
    }

    private void renderRequestCards(
            LinearLayout container,
            List<AdminRequestCardModel> cardModels,
            int titleResId,
            int emptyResId
    ) {
        container.removeAllViews();
        if (cardModels.isEmpty()) {
            renderEmptyText(container, titleResId, emptyResId);
            return;
        }

        for (AdminRequestCardModel cardModel : cardModels) {
            View itemView = inflater.inflate(R.layout.item_admin_request, container, false);
            requestCardBinder.bind(itemView, cardModel, new AdminRequestCardBinder.Listener() {
                @Override
                public void onToggleDetail(String requestId) {
                    toggleRequestDetail(requestId);
                }

                @Override
                public void onAssignManager(String requestId, String managerUserId) {
                    listener.onAssignManager(requestId, managerUserId);
                }
            });
            container.addView(itemView);
        }
    }

    private void toggleRequestDetail(String requestId) {
        if (expandedRequestIds.contains(requestId)) {
            expandedRequestIds.remove(requestId);
        } else {
            expandedRequestIds.add(requestId);
        }
        renderPendingRequests();
        renderManagedRequests();
    }

    private void bindFilterButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.bodeul_primary)));
            button.setStrokeColor(ColorStateList.valueOf(context.getColor(R.color.bodeul_primary)));
            button.setTextColor(context.getColor(R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.white)));
        button.setStrokeColor(ColorStateList.valueOf(context.getColor(R.color.bodeul_primary)));
        button.setTextColor(context.getColor(R.color.bodeul_primary));
    }

    private void renderEmptyText(LinearLayout container, int titleResId, int messageResId) {
        View emptyPanel = inflater.inflate(R.layout.include_state_panel, container, false);
        StatePanelHelper.show(
                emptyPanel,
                StatePanelHelper.Tone.INFO,
                context.getString(R.string.state_badge_notice),
                context.getString(titleResId),
                context.getString(messageResId),
                null,
                null,
                null,
                null
        );
        container.addView(emptyPanel);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
