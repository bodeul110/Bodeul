package com.example.bodeul.ui.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.SupportInquiry;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 문의 섹션의 필터, 목록, 응답 다이얼로그를 한 곳에서 관리한다.
 */
final class AdminSupportSectionController {
    interface Listener {
        boolean isInteractionBlocked();

        void onRespondSupportInquiry(String inquiryId, String response);

        void onRespondClientSupportRequest(String supportRequestId, String response);

        MaterialButton createFilterButton(String text, boolean selected);

        void renderEmptyText(LinearLayout container, int titleResId, int messageResId);
    }

    private final AppCompatActivity activity;
    private final TextView summaryView;
    private final LinearLayout sourceFilterContainer;
    private final LinearLayout statusFilterContainer;
    private final LinearLayout inquiryContainer;
    private final AdminSupportCoordinator coordinator;
    private final AdminSupportInquiryCardBinder cardBinder;
    private final Listener listener;
    private final List<SupportInquiry> supportInquiriesSnapshot = new ArrayList<>();
    private final List<ClientSupportRequest> clientSupportRequestsSnapshot = new ArrayList<>();
    private AdminSupportSourceFilter sourceFilter = AdminSupportSourceFilter.ALL;
    private AdminSupportStatusFilter statusFilter = AdminSupportStatusFilter.ALL;

    AdminSupportSectionController(
            AppCompatActivity activity,
            TextView summaryView,
            LinearLayout sourceFilterContainer,
            LinearLayout statusFilterContainer,
            LinearLayout inquiryContainer,
            AdminSupportCoordinator coordinator,
            AdminSupportInquiryCardBinder cardBinder,
            Listener listener
    ) {
        this.activity = activity;
        this.summaryView = summaryView;
        this.sourceFilterContainer = sourceFilterContainer;
        this.statusFilterContainer = statusFilterContainer;
        this.inquiryContainer = inquiryContainer;
        this.coordinator = coordinator;
        this.cardBinder = cardBinder;
        this.listener = listener;
    }

    void bindSupportInquiries(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests
    ) {
        supportInquiriesSnapshot.clear();
        supportInquiriesSnapshot.addAll(inquiries);
        clientSupportRequestsSnapshot.clear();
        clientSupportRequestsSnapshot.addAll(clientRequests);
        renderSupportInquiries();
    }

    void clear() {
        sourceFilter = AdminSupportSourceFilter.ALL;
        statusFilter = AdminSupportStatusFilter.ALL;
        supportInquiriesSnapshot.clear();
        clientSupportRequestsSnapshot.clear();
        summaryView.setText(R.string.admin_support_summary_empty);
        sourceFilterContainer.removeAllViews();
        sourceFilterContainer.setVisibility(View.GONE);
        statusFilterContainer.removeAllViews();
        statusFilterContainer.setVisibility(View.GONE);
        inquiryContainer.removeAllViews();
    }

    void showEmptyPanel() {
        clear();
        listener.renderEmptyText(
                inquiryContainer,
                R.string.admin_support_title,
                R.string.admin_support_empty
        );
    }

    private void renderSupportInquiries() {
        AdminSupportDashboardModel supportModel =
                coordinator.createDashboardModel(
                        supportInquiriesSnapshot,
                        clientSupportRequestsSnapshot,
                        sourceFilter,
                        statusFilter
                );
        summaryView.setText(supportModel.getSummaryText());
        renderSupportFilters(supportModel);
        inquiryContainer.removeAllViews();
        if (supportModel.getInquiryCards().isEmpty()) {
            listener.renderEmptyText(
                    inquiryContainer,
                    R.string.admin_support_title,
                    R.string.admin_support_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (AdminSupportInquiryCardModel cardModel : supportModel.getInquiryCards()) {
            View itemView = inflater.inflate(R.layout.item_admin_support_inquiry, inquiryContainer, false);
            cardBinder.bind(itemView, cardModel, model -> {
                if (model.getSourceType() == AdminSupportInquirySourceType.CLIENT) {
                    ClientSupportRequest request = findClientSupportRequest(model.getInquiryId());
                    if (request != null) {
                        openClientSupportResponseDialog(request);
                    }
                    return;
                }
                SupportInquiry inquiry = findSupportInquiry(model.getInquiryId());
                if (inquiry != null) {
                    openSupportResponseDialog(inquiry);
                }
            });
            inquiryContainer.addView(itemView);
        }
    }

    private void renderSupportFilters(AdminSupportDashboardModel supportModel) {
        sourceFilterContainer.removeAllViews();
        if (supportModel.getSourceFilterChips().size() <= 1) {
            sourceFilterContainer.setVisibility(View.GONE);
        } else {
            sourceFilterContainer.setVisibility(View.VISIBLE);
            for (AdminSupportSourceFilterChipModel chipModel : supportModel.getSourceFilterChips()) {
                MaterialButton button = listener.createFilterButton(
                        chipModel.getButtonText(),
                        chipModel.isSelected()
                );
                button.setOnClickListener(view -> {
                    sourceFilter = chipModel.getFilter();
                    renderSupportInquiries();
                });
                sourceFilterContainer.addView(button);
            }
        }

        statusFilterContainer.removeAllViews();
        if (supportModel.getStatusFilterChips().size() <= 1) {
            statusFilterContainer.setVisibility(View.GONE);
            return;
        }

        statusFilterContainer.setVisibility(View.VISIBLE);
        for (AdminSupportStatusFilterChipModel chipModel : supportModel.getStatusFilterChips()) {
            MaterialButton button = listener.createFilterButton(
                    chipModel.getButtonText(),
                    chipModel.isSelected()
            );
            button.setOnClickListener(view -> {
                statusFilter = chipModel.getFilter();
                renderSupportInquiries();
            });
            statusFilterContainer.addView(button);
        }
    }

    private void openSupportResponseDialog(SupportInquiry inquiry) {
        if (listener.isInteractionBlocked()) {
            return;
        }
        View dialogView = LayoutInflater.from(activity).inflate(
                R.layout.dialog_admin_document_review,
                null,
                false
        );
        TextInputLayout inputLayout = dialogView.findViewById(R.id.layoutAdminDocumentReviewNote);
        TextInputEditText inputEditText = dialogView.findViewById(R.id.inputAdminDocumentReviewNote);
        inputLayout.setHint(activity.getString(R.string.admin_support_response_hint));
        inputLayout.setHelperText(activity.getString(R.string.admin_support_response_helper));
        inputEditText.setText(inquiry.getResponseText());
        if (inputEditText.getText() != null) {
            inputEditText.setSelection(inputEditText.getText().length());
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(
                        R.string.admin_support_response_dialog_title,
                        inquiry.getManagerName()
                ))
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String response = valueOf(inputEditText);
                    if (TextUtils.isEmpty(response)) {
                        inputLayout.setError(activity.getString(R.string.admin_operation_note_required));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    listener.onRespondSupportInquiry(inquiry.getId(), response);
                }));
        dialog.show();
    }

    private void openClientSupportResponseDialog(ClientSupportRequest request) {
        if (listener.isInteractionBlocked()) {
            return;
        }
        View dialogView = LayoutInflater.from(activity).inflate(
                R.layout.dialog_admin_document_review,
                null,
                false
        );
        TextInputLayout inputLayout = dialogView.findViewById(R.id.layoutAdminDocumentReviewNote);
        TextInputEditText inputEditText = dialogView.findViewById(R.id.inputAdminDocumentReviewNote);
        inputLayout.setHint(activity.getString(R.string.admin_support_response_hint));
        inputLayout.setHelperText(activity.getString(R.string.admin_support_response_helper));
        inputEditText.setText(request.getResponseText());
        if (inputEditText.getText() != null) {
            inputEditText.setSelection(inputEditText.getText().length());
        }

        String requesterName = TextUtils.isEmpty(request.getUserName())
                ? activity.getString(R.string.admin_manager_pending)
                : request.getUserName();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(
                        R.string.admin_support_response_dialog_title,
                        requesterName
                ))
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String response = valueOf(inputEditText);
                    if (TextUtils.isEmpty(response)) {
                        inputLayout.setError(activity.getString(R.string.admin_operation_note_required));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    listener.onRespondClientSupportRequest(request.getId(), response);
                }));
        dialog.show();
    }

    @Nullable
    private SupportInquiry findSupportInquiry(String inquiryId) {
        for (SupportInquiry inquiry : supportInquiriesSnapshot) {
            if (inquiry.getId().equals(inquiryId)) {
                return inquiry;
            }
        }
        return null;
    }

    @Nullable
    private ClientSupportRequest findClientSupportRequest(String supportRequestId) {
        for (ClientSupportRequest request : clientSupportRequestsSnapshot) {
            if (request.getId().equals(supportRequestId)) {
                return request;
            }
        }
        return null;
    }

    private static String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
