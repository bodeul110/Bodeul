package com.example.bodeul.ui.manager;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 서류 등록 화면 모델을 실제 뷰 계층에 연결한다.
 */
public final class ManagerDocumentRegistrationBinder {
    public interface Listener {
        void onDocumentUploadRequested(@Nullable ManagerDocumentFileType fileType);

        void onDocumentPreviewRequested(@Nullable ManagerDocumentFileType fileType);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private final TextView textMode;
    private final View hiddenStatusContainer;
    private final TextView textStatusBadge;
    private final TextView textStatusTitle;
    private final TextView textStatusBody;
    private final TextView textPrimaryTitle;
    private final TextView textPrimaryHelper;
    private final TextView textPrimaryFileName;
    private final TextView textPrimaryFileMeta;
    private final MaterialButton buttonPrimaryPreview;
    private final MaterialButton buttonPrimaryUpload;
    private final LinearLayout documentContainer;
    private final View reviewCard;
    private final TextView textReviewTitle;
    private final TextView textReviewBody;
    private final MaterialButton buttonRequest;
    private final ManagerDocumentRegistrationItemBinder documentItemBinder;

    public ManagerDocumentRegistrationBinder(
            LayoutInflater inflater,
            Listener listener,
            TextView textMode,
            View hiddenStatusContainer,
            TextView textStatusBadge,
            TextView textStatusTitle,
            TextView textStatusBody,
            TextView textPrimaryTitle,
            TextView textPrimaryHelper,
            TextView textPrimaryFileName,
            TextView textPrimaryFileMeta,
            MaterialButton buttonPrimaryPreview,
            MaterialButton buttonPrimaryUpload,
            LinearLayout documentContainer,
            View reviewCard,
            TextView textReviewTitle,
            TextView textReviewBody,
            MaterialButton buttonRequest
    ) {
        this.inflater = inflater;
        this.listener = listener;
        this.textMode = textMode;
        this.hiddenStatusContainer = hiddenStatusContainer;
        this.textStatusBadge = textStatusBadge;
        this.textStatusTitle = textStatusTitle;
        this.textStatusBody = textStatusBody;
        this.textPrimaryTitle = textPrimaryTitle;
        this.textPrimaryHelper = textPrimaryHelper;
        this.textPrimaryFileName = textPrimaryFileName;
        this.textPrimaryFileMeta = textPrimaryFileMeta;
        this.buttonPrimaryPreview = buttonPrimaryPreview;
        this.buttonPrimaryUpload = buttonPrimaryUpload;
        this.documentContainer = documentContainer;
        this.reviewCard = reviewCard;
        this.textReviewTitle = textReviewTitle;
        this.textReviewBody = textReviewBody;
        this.buttonRequest = buttonRequest;
        this.documentItemBinder = new ManagerDocumentRegistrationItemBinder();
    }

    public void bindScreen(ManagerDocumentRegistrationScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        hiddenStatusContainer.setVisibility(View.GONE);
        textStatusBadge.setText(screenModel.getStatusBadgeText());
        textStatusTitle.setText(screenModel.getStatusTitleText());
        textStatusBody.setText(screenModel.getStatusBodyText());
        buttonRequest.setText(screenModel.getRequestButtonText());
        buttonRequest.setEnabled(screenModel.isRequestButtonEnabled());
        buttonRequest.setAlpha(screenModel.isRequestButtonEnabled() ? 1f : 0.55f);

        bindPrimaryDocument(screenModel);
        bindDocumentItems(screenModel);
        bindReviewCard(screenModel);
    }

    private void bindPrimaryDocument(ManagerDocumentRegistrationScreenModel screenModel) {
        if (screenModel.getDocumentItems().isEmpty()) {
            textPrimaryTitle.setText("");
            textPrimaryHelper.setText("");
            textPrimaryFileName.setVisibility(View.GONE);
            textPrimaryFileMeta.setVisibility(View.GONE);
            buttonPrimaryPreview.setVisibility(View.GONE);
            buttonPrimaryUpload.setEnabled(false);
            return;
        }

        ManagerDocumentRegistrationItemModel itemModel = screenModel.getDocumentItems().get(0);
        textPrimaryTitle.setText(itemModel.getTitleText());
        textPrimaryHelper.setText(itemModel.getHelperText());

        if (itemModel.getFileNameText().isEmpty()) {
            textPrimaryFileName.setVisibility(View.GONE);
            textPrimaryFileName.setText("");
        } else {
            textPrimaryFileName.setVisibility(View.VISIBLE);
            textPrimaryFileName.setText(itemModel.getFileNameText());
        }

        if (itemModel.getFileMetaText().isEmpty()) {
            textPrimaryFileMeta.setVisibility(View.GONE);
            textPrimaryFileMeta.setText("");
        } else {
            textPrimaryFileMeta.setVisibility(View.VISIBLE);
            textPrimaryFileMeta.setText(itemModel.getFileMetaText());
        }
        buttonPrimaryPreview.setVisibility(itemModel.isPreviewAvailable() ? View.VISIBLE : View.GONE);
        buttonPrimaryPreview.setOnClickListener(view ->
                listener.onDocumentPreviewRequested(itemModel.getPreviewFileType()));
        buttonPrimaryUpload.setText(itemModel.getActionText());
        buttonPrimaryUpload.setOnClickListener(view ->
                listener.onDocumentUploadRequested(itemModel.getUploadFileType()));
    }

    private void bindDocumentItems(ManagerDocumentRegistrationScreenModel screenModel) {
        documentContainer.removeAllViews();
        for (int index = 1; index < screenModel.getDocumentItems().size(); index++) {
            ManagerDocumentRegistrationItemModel itemModel = screenModel.getDocumentItems().get(index);
            View itemView = inflater.inflate(
                    R.layout.item_manager_document_registration,
                    documentContainer,
                    false
            );
            documentItemBinder.bind(itemView, itemModel);
            itemView.setOnClickListener(view -> {
                if (itemModel.isPreviewAvailable()) {
                    listener.onDocumentPreviewRequested(itemModel.getPreviewFileType());
                    return;
                }
                listener.onDocumentUploadRequested(itemModel.getUploadFileType());
            });
            itemView.findViewById(R.id.buttonManagerDocumentRegistrationPreview)
                    .setOnClickListener(view ->
                            listener.onDocumentPreviewRequested(itemModel.getPreviewFileType()));
            itemView.findViewById(R.id.buttonManagerDocumentRegistrationUpload)
                    .setOnClickListener(view ->
                            listener.onDocumentUploadRequested(itemModel.getUploadFileType()));
            documentContainer.addView(itemView);
        }
    }

    private void bindReviewCard(ManagerDocumentRegistrationScreenModel screenModel) {
        reviewCard.setVisibility(screenModel.isReviewCardVisible() ? View.VISIBLE : View.GONE);
        textReviewTitle.setText(screenModel.getReviewTitleText());
        textReviewBody.setText(screenModel.getReviewBodyText());
    }
}
