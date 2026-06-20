package com.example.bodeul.ui.admin;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.util.DocumentPreviewLauncher;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 서류 심사 섹션의 목록, 이력, 파일 미리보기, 승인/반려 다이얼로그를 관리한다.
 */
final class AdminManagerDocumentSectionController {
    interface Listener {
        boolean isInteractionBlocked();

        void onReviewManagerDocument(
                String managerUserId,
                ManagerDocumentStatus status,
                String reviewNote
        );

        void setLoading(boolean loading);

        void renderEmptyText(LinearLayout container, int titleResId, int messageResId);
    }

    private final AppCompatActivity activity;
    private final LinearLayout container;
    private final AdminManagerDocumentCoordinator coordinator;
    private final AdminManagerDocumentCardBinder cardBinder;
    private final AdminManagerDocumentHistoryItemBinder historyItemBinder;
    private final ManagerDocumentPreviewResolver previewResolver;
    private final Listener listener;
    private final List<ManagerDocumentOverview> overviews = new ArrayList<>();
    private boolean loading;

    AdminManagerDocumentSectionController(
            AppCompatActivity activity,
            LinearLayout container,
            AdminManagerDocumentCoordinator coordinator,
            AdminManagerDocumentCardBinder cardBinder,
            AdminManagerDocumentHistoryItemBinder historyItemBinder,
            ManagerDocumentPreviewResolver previewResolver,
            Listener listener
    ) {
        this.activity = activity;
        this.container = container;
        this.coordinator = coordinator;
        this.cardBinder = cardBinder;
        this.historyItemBinder = historyItemBinder;
        this.previewResolver = previewResolver;
        this.listener = listener;
    }

    void bindDocuments(List<ManagerDocumentOverview> source, boolean loading) {
        this.loading = loading;
        overviews.clear();
        overviews.addAll(source);
        renderDocuments();
    }

    void clear() {
        overviews.clear();
        loading = false;
        container.removeAllViews();
    }

    void showEmptyPanel() {
        clear();
        listener.renderEmptyText(
                container,
                R.string.admin_manager_documents_title,
                R.string.admin_manager_documents_empty
        );
    }

    private void renderDocuments() {
        container.removeAllViews();
        if (overviews.isEmpty()) {
            showEmptyPanel();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        List<AdminManagerDocumentCardModel> cards = coordinator.createDocumentCards(overviews, loading);
        for (AdminManagerDocumentCardModel card : cards) {
            View itemView = inflater.inflate(R.layout.item_admin_manager_document, container, false);
            cardBinder.bind(
                    itemView,
                    card,
                    new AdminManagerDocumentCardBinder.Listener() {
                        @Override
                        public void onApprove(String managerUserId) {
                            ManagerDocumentOverview overview = findOverview(managerUserId);
                            if (overview != null) {
                                openReviewDialog(overview, ManagerDocumentStatus.APPROVED);
                            }
                        }

                        @Override
                        public void onReject(String managerUserId) {
                            ManagerDocumentOverview overview = findOverview(managerUserId);
                            if (overview != null) {
                                openReviewDialog(overview, ManagerDocumentStatus.REJECTED);
                            }
                        }

                        @Override
                        public void onOpenFiles(String managerUserId) {
                            ManagerDocumentOverview overview = findOverview(managerUserId);
                            if (overview != null) {
                                openFilesDialog(overview);
                            }
                        }

                        @Override
                        public void onOpenHistory(String managerUserId) {
                            ManagerDocumentOverview overview = findOverview(managerUserId);
                            if (overview != null) {
                                openHistoryDialog(overview);
                            }
                        }
                    }
            );
            container.addView(itemView);
        }
    }

    private void openReviewDialog(
            ManagerDocumentOverview overview,
            ManagerDocumentStatus targetStatus
    ) {
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
        inputLayout.setHelperText(activity.getString(
                targetStatus == ManagerDocumentStatus.APPROVED
                        ? R.string.admin_manager_document_approve_helper
                        : R.string.admin_manager_document_reject_helper
        ));
        inputEditText.setText(overview.getProfile().getDocumentReviewNote());
        if (inputEditText.getText() != null) {
            inputEditText.setSelection(inputEditText.getText().length());
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(
                        targetStatus == ManagerDocumentStatus.APPROVED
                                ? R.string.admin_manager_document_approve_dialog_title
                                : R.string.admin_manager_document_reject_dialog_title,
                        overview.getManager().getName()
                ))
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String reviewNote = valueOf(inputEditText);
                    if (targetStatus == ManagerDocumentStatus.REJECTED && TextUtils.isEmpty(reviewNote)) {
                        inputLayout.setError(activity.getString(
                                R.string.admin_manager_document_review_note_required
                        ));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    listener.onReviewManagerDocument(
                            overview.getManager().getId(),
                            targetStatus,
                            reviewNote
                    );
                }));
        dialog.show();
    }

    private void openHistoryDialog(ManagerDocumentOverview overview) {
        View dialogView = LayoutInflater.from(activity).inflate(
                R.layout.dialog_admin_document_history,
                null,
                false
        );
        TextView helperView = dialogView.findViewById(R.id.textAdminDocumentHistoryHelper);
        LinearLayout historyContainer = dialogView.findViewById(R.id.adminDocumentHistoryContainer);
        helperView.setText(R.string.admin_manager_document_history_helper);
        renderHistoryEntries(historyContainer, coordinator.createHistoryItems(overview));

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(
                        R.string.admin_manager_document_history_dialog_title,
                        overview.getManager().getName()
                ))
                .setView(dialogView)
                .setPositiveButton(R.string.admin_manager_document_history_close, null)
                .show();
    }

    private void renderHistoryEntries(
            LinearLayout historyContainer,
            List<AdminManagerDocumentHistoryItemModel> historyItems
    ) {
        historyContainer.removeAllViews();
        if (historyItems.isEmpty()) {
            listener.renderEmptyText(
                    historyContainer,
                    R.string.admin_manager_document_history,
                    R.string.admin_manager_document_history_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (AdminManagerDocumentHistoryItemModel historyItem : historyItems) {
            View itemView = inflater.inflate(
                    R.layout.item_admin_document_history,
                    historyContainer,
                    false
            );
            historyItemBinder.bind(itemView, historyItem);
            historyContainer.addView(itemView);
        }
    }

    private void openFilesDialog(ManagerDocumentOverview overview) {
        List<ManagerDocumentFileMetadata> documentFiles = overview.getProfile().getDocumentFiles();
        if (documentFiles.isEmpty()) {
            Toast.makeText(activity, R.string.admin_manager_document_files_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] items = new CharSequence[documentFiles.size()];
        for (int index = 0; index < documentFiles.size(); index++) {
            ManagerDocumentFileMetadata metadata = documentFiles.get(index);
            items[index] = activity.getString(
                    R.string.admin_manager_document_file_item_format,
                    getManagerDocumentLabel(metadata.getFileType()),
                    metadata.getFileName()
            );
        }

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(
                        R.string.admin_manager_document_files_dialog_title,
                        overview.getManager().getName()
                ))
                .setItems(items, (dialogInterface, which) ->
                        openPreview(documentFiles.get(which)))
                .setNegativeButton(R.string.admin_manager_document_history_close, null)
                .show();
    }

    private void openPreview(ManagerDocumentFileMetadata metadata) {
        if (listener.isInteractionBlocked()) {
            return;
        }
        listener.setLoading(true);
        previewResolver.resolvePreviewUri(
                metadata,
                new RepositoryCallback<Uri>() {
                    @Override
                    public void onSuccess(Uri result) {
                        listener.setLoading(false);
                        if (!DocumentPreviewLauncher.open(activity, result, metadata.getContentType())) {
                            Toast.makeText(
                                    activity,
                                    R.string.manager_document_preview_open_failed,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        listener.setLoading(false);
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    private ManagerDocumentOverview findOverview(String managerUserId) {
        for (ManagerDocumentOverview overview : overviews) {
            if (overview.getManager().getId().equals(managerUserId)) {
                return overview;
            }
        }
        return null;
    }

    private String getManagerDocumentLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return activity.getString(R.string.manager_document_registration_document_id_card);
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return activity.getString(R.string.manager_document_registration_document_nursing_license);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return activity.getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        return activity.getString(R.string.manager_document_registration_document_criminal_record);
    }

    private static String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
