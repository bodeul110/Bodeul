package com.example.bodeul.ui.admin;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 서류 검토 카드 모델을 실제 카드 뷰에 바인딩한다.
 */
public final class AdminManagerDocumentCardBinder {
    public interface Listener {
        void onApprove(String managerUserId);

        void onReject(String managerUserId);

        void onOpenFiles(String managerUserId);

        void onOpenHistory(String managerUserId);
    }

    public void bind(View itemView, AdminManagerDocumentCardModel model, Listener listener) {
        TextView titleView = itemView.findViewById(R.id.textAdminManagerDocumentTitle);
        TextView statusView = itemView.findViewById(R.id.textAdminManagerDocumentStatus);
        TextView summaryView = itemView.findViewById(R.id.textAdminManagerDocumentSummary);
        TextView availabilityView = itemView.findViewById(R.id.textAdminManagerDocumentAvailability);
        TextView reviewNoteView = itemView.findViewById(R.id.textAdminManagerDocumentReviewNote);
        TextView timelineView = itemView.findViewById(R.id.textAdminManagerDocumentTimeline);
        View actionLayout = itemView.findViewById(R.id.layoutAdminManagerDocumentActions);
        MaterialButton filesButton = itemView.findViewById(R.id.buttonAdminManagerDocumentFiles);
        MaterialButton historyButton = itemView.findViewById(R.id.buttonAdminManagerDocumentHistory);
        MaterialButton approveButton = itemView.findViewById(R.id.buttonAdminManagerDocumentApprove);
        MaterialButton rejectButton = itemView.findViewById(R.id.buttonAdminManagerDocumentReject);

        titleView.setText(model.getTitleText());
        statusView.setText(model.getStatusText());
        statusView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(itemView.getContext(), model.getStatusBackgroundColorResId())
        ));
        statusView.setTextColor(
                ContextCompat.getColor(itemView.getContext(), model.getStatusTextColorResId())
        );
        summaryView.setText(model.getSummaryText());
        availabilityView.setText(model.getAvailabilityText());
        reviewNoteView.setText(model.getReviewNoteText());
        timelineView.setText(model.getTimelineText());

        actionLayout.setVisibility(model.isShowActions() ? View.VISIBLE : View.GONE);
        approveButton.setEnabled(model.isActionsEnabled());
        rejectButton.setEnabled(model.isActionsEnabled());
        filesButton.setVisibility(model.isShowFilesButton() ? View.VISIBLE : View.GONE);
        filesButton.setEnabled(model.isFilesButtonEnabled());
        historyButton.setVisibility(model.isShowHistoryButton() ? View.VISIBLE : View.GONE);
        historyButton.setEnabled(model.isHistoryButtonEnabled());

        approveButton.setOnClickListener(view -> listener.onApprove(model.getManagerUserId()));
        rejectButton.setOnClickListener(view -> listener.onReject(model.getManagerUserId()));
        filesButton.setOnClickListener(view -> listener.onOpenFiles(model.getManagerUserId()));
        historyButton.setOnClickListener(view -> listener.onOpenHistory(model.getManagerUserId()));
    }
}
