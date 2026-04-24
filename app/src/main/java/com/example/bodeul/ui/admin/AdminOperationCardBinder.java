package com.example.bodeul.ui.admin;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 운영 카드 모델을 공통 카드 레이아웃에 바인딩한다.
 */
public final class AdminOperationCardBinder {
    public interface Listener {
        void onAction(String requestId, AdminOperationActionType actionType);
    }

    private final LayoutInflater inflater;

    public AdminOperationCardBinder(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public void bind(View itemView, AdminOperationCardModel model, Listener listener) {
        TextView badgeView = itemView.findViewById(R.id.textAdminOperationCardBadge);
        TextView priorityBadgeView = itemView.findViewById(R.id.textAdminOperationCardPriorityBadge);
        TextView titleView = itemView.findViewById(R.id.textAdminOperationCardTitle);
        TextView subtitleView = itemView.findViewById(R.id.textAdminOperationCardSubtitle);
        TextView summaryView = itemView.findViewById(R.id.textAdminOperationCardSummary);
        TextView activityView = itemView.findViewById(R.id.textAdminOperationCardActivity);
        LinearLayout lineContainer = itemView.findViewById(R.id.adminOperationCardLineContainer);
        LinearLayout actionContainer = itemView.findViewById(R.id.adminOperationCardActionContainer);

        bindBadge(itemView, badgeView, model.getStatusBadge());
        if (model.getPriorityBadge() == null) {
            priorityBadgeView.setVisibility(View.GONE);
        } else {
            priorityBadgeView.setVisibility(View.VISIBLE);
            bindBadge(itemView, priorityBadgeView, model.getPriorityBadge());
        }
        titleView.setText(model.getTitleText());
        subtitleView.setText(model.getSubtitleText());
        summaryView.setText(model.getSummaryText());
        if (model.getActivityText().isEmpty()) {
            activityView.setVisibility(View.GONE);
        } else {
            activityView.setVisibility(View.VISIBLE);
            activityView.setText(model.getActivityText());
        }

        lineContainer.removeAllViews();
        for (AdminOperationLineItem lineItem : model.getDetailLines()) {
            View lineView = inflater.inflate(R.layout.item_admin_operation_line, lineContainer, false);
            TextView labelView = lineView.findViewById(R.id.textAdminOperationLineLabel);
            TextView valueView = lineView.findViewById(R.id.textAdminOperationLineValue);
            labelView.setText(lineItem.getLabelText());
            valueView.setText(lineItem.getValueText());
            valueView.setTypeface(
                    valueView.getTypeface(),
                    lineItem.isEmphasized() ? Typeface.BOLD : Typeface.NORMAL
            );
            valueView.setTextSize(lineItem.isEmphasized() ? 16f : 14f);
            lineContainer.addView(lineView);
        }

        bindActions(itemView, model, actionContainer, listener);
    }

    private void bindBadge(View itemView, TextView badgeView, AdminOperationBadgeModel badgeModel) {
        badgeView.setText(badgeModel.getText());
        switch (badgeModel.getTone()) {
            case WARNING:
                badgeView.setBackgroundResource(R.drawable.bg_badge_yellow);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_text_primary));
                return;
            case SUCCESS:
                badgeView.setBackgroundResource(R.drawable.bg_badge_green);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_success));
                return;
            case PURPLE:
                badgeView.setBackgroundResource(R.drawable.bg_badge_purple);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary));
                return;
            case PRIMARY:
            default:
                badgeView.setBackgroundResource(R.drawable.bg_badge_blue);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary));
        }
    }

    private void bindActions(
            View itemView,
            AdminOperationCardModel model,
            LinearLayout container,
            Listener listener
    ) {
        container.removeAllViews();
        if (model.getActions().isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        for (AdminOperationActionModel actionModel : model.getActions()) {
            MaterialButton button = new MaterialButton(
                    itemView.getContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dpToPx(itemView, 8);
            button.setLayoutParams(params);
            button.setText(actionModel.getButtonText());
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(itemView, 18));
            button.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
            ));
            button.setTextColor(
                    ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
            );
            button.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.white)
            ));
            button.setOnClickListener(view -> listener.onAction(
                    model.getRequestId(),
                    actionModel.getActionType()
            ));
            container.addView(button);
        }
    }

    private int dpToPx(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
