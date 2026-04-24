package com.example.bodeul.ui.admin;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 요청 카드 모델을 실제 카드 뷰에 바인딩한다.
 */
public final class AdminRequestCardBinder {
    public interface Listener {
        void onToggleDetail(String requestId);

        void onAssignManager(String requestId, String managerUserId);
    }

    private final LayoutInflater inflater;

    public AdminRequestCardBinder(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public void bind(View itemView, AdminRequestCardModel model, Listener listener) {
        TextView statusView = itemView.findViewById(R.id.textAdminRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textAdminRequestTitle);
        TextView participantsView = itemView.findViewById(R.id.textAdminRequestParticipants);
        TextView scheduleView = itemView.findViewById(R.id.textAdminRequestSchedule);
        TextView managerView = itemView.findViewById(R.id.textAdminRequestManager);
        TextView progressView = itemView.findViewById(R.id.textAdminRequestProgress);
        TextView noteView = itemView.findViewById(R.id.textAdminRequestNote);
        TextView constraintView = itemView.findViewById(R.id.textAdminRequestConstraint);
        TextView detailButton = itemView.findViewById(R.id.textAdminRequestDetailToggle);
        TextView detailPanel = itemView.findViewById(R.id.textAdminRequestDetailPanel);
        LinearLayout managerActionsContainer = itemView.findViewById(R.id.managerActionsContainer);

        titleView.setText(model.getTitleText());
        statusView.setText(model.getStatusText());
        statusView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(itemView.getContext(), model.getStatusBackgroundColorResId())
        ));
        statusView.setTextColor(
                ContextCompat.getColor(itemView.getContext(), model.getStatusTextColorResId())
        );
        participantsView.setText(model.getParticipantsText());
        scheduleView.setText(model.getScheduleText());
        managerView.setText(model.getManagerText());
        progressView.setText(model.getProgressText());
        detailButton.setText(model.getDetailToggleText());
        detailPanel.setText(model.getDetailPanelText());
        detailPanel.setVisibility(model.isDetailExpanded() ? View.VISIBLE : View.GONE);
        detailButton.setOnClickListener(view -> listener.onToggleDetail(model.getRequestId()));

        if (TextUtils.isEmpty(model.getNoteText())) {
            noteView.setVisibility(View.GONE);
        } else {
            noteView.setVisibility(View.VISIBLE);
            noteView.setText(itemView.getContext().getString(
                    R.string.admin_request_note,
                    model.getNoteText()
            ));
        }

        if (TextUtils.isEmpty(model.getConstraintText())) {
            constraintView.setVisibility(View.GONE);
        } else {
            constraintView.setVisibility(View.VISIBLE);
            constraintView.setText(model.getConstraintText());
        }

        bindAssignActions(itemView, model, managerActionsContainer, listener);
    }

    private void bindAssignActions(
            View itemView,
            AdminRequestCardModel model,
            LinearLayout container,
            Listener listener
    ) {
        container.removeAllViews();
        if (TextUtils.isEmpty(model.getConstraintText()) && !model.getAssignActions().isEmpty()) {
            container.setVisibility(View.VISIBLE);
            for (AdminRequestAssignActionModel actionModel : model.getAssignActions()) {
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
                button.setOnClickListener(view -> listener.onAssignManager(
                        model.getRequestId(),
                        actionModel.getManagerUserId()
                ));
                container.addView(button);
            }
            return;
        }
        container.setVisibility(View.GONE);
    }

    private int dpToPx(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
