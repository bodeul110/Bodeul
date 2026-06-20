package com.example.bodeul.ui.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public final class CompanionChatBinder {
    public interface PendingAttachmentThumbnailBinder {
        void onBindPendingAttachmentThumbnail(
                ImageView imageView,
                CompanionChatPendingAttachment pendingAttachment
        );
    }

    public interface MessageAttachmentThumbnailBinder {
        void onBindMessageAttachmentThumbnail(
                ImageView imageView,
                CompanionChatAttachment attachment
        );
    }

    public interface MessageAttachmentActionListener {
        void onOpenAttachment(CompanionChatAttachment attachment);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textSectionTitle;
    private final TextView textEmptyBody;
    private final LinearLayout messageContainer;
    private final TextInputLayout inputLayout;
    private final MaterialButton buttonSend;
    private final View pendingAttachmentContainer;
    private final ImageView imagePendingAttachmentPreview;
    private final TextView textPendingAttachmentTitle;
    private final TextView textPendingAttachmentMeta;
    private final MaterialButton buttonPendingAttachmentPreview;
    private final MaterialButton buttonPendingAttachmentClear;
    private final PendingAttachmentThumbnailBinder pendingAttachmentThumbnailBinder;
    private final MessageAttachmentThumbnailBinder messageAttachmentThumbnailBinder;
    private final MessageAttachmentActionListener messageAttachmentActionListener;

    public CompanionChatBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textTitle,
            TextView textSubtitle,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textSectionTitle,
            TextView textEmptyBody,
            LinearLayout messageContainer,
            TextInputLayout inputLayout,
            MaterialButton buttonSend,
            View pendingAttachmentContainer,
            ImageView imagePendingAttachmentPreview,
            TextView textPendingAttachmentTitle,
            TextView textPendingAttachmentMeta,
            MaterialButton buttonPendingAttachmentPreview,
            MaterialButton buttonPendingAttachmentClear,
            PendingAttachmentThumbnailBinder pendingAttachmentThumbnailBinder,
            MessageAttachmentThumbnailBinder messageAttachmentThumbnailBinder,
            MessageAttachmentActionListener messageAttachmentActionListener
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textTitle = textTitle;
        this.textSubtitle = textSubtitle;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textSectionTitle = textSectionTitle;
        this.textEmptyBody = textEmptyBody;
        this.messageContainer = messageContainer;
        this.inputLayout = inputLayout;
        this.buttonSend = buttonSend;
        this.pendingAttachmentContainer = pendingAttachmentContainer;
        this.imagePendingAttachmentPreview = imagePendingAttachmentPreview;
        this.textPendingAttachmentTitle = textPendingAttachmentTitle;
        this.textPendingAttachmentMeta = textPendingAttachmentMeta;
        this.buttonPendingAttachmentPreview = buttonPendingAttachmentPreview;
        this.buttonPendingAttachmentClear = buttonPendingAttachmentClear;
        this.pendingAttachmentThumbnailBinder = pendingAttachmentThumbnailBinder;
        this.messageAttachmentThumbnailBinder = messageAttachmentThumbnailBinder;
        this.messageAttachmentActionListener = messageAttachmentActionListener;
    }

    public void bindScreen(CompanionChatScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeLabel());
        textTitle.setText(screenModel.getTitle());
        textSubtitle.setText(screenModel.getSubtitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroTitle.setText(screenModel.getHeroTitle());
        textHeroBody.setText(screenModel.getHeroBody());
        textSectionTitle.setText(screenModel.getSectionTitle());
        textEmptyBody.setText(screenModel.getEmptyBody());
        inputLayout.setHint(screenModel.getInputHint());
        buttonSend.setText(screenModel.getSendButtonLabel());
        bindMessages(screenModel.getMessages());
    }

    public void bindPendingAttachment(
            CompanionChatPendingAttachment pendingAttachment,
            View.OnClickListener previewListener,
            View.OnClickListener clearListener
    ) {
        if (pendingAttachment == null) {
            pendingAttachmentContainer.setVisibility(View.GONE);
            imagePendingAttachmentPreview.setVisibility(View.GONE);
            imagePendingAttachmentPreview.setImageDrawable(null);
            imagePendingAttachmentPreview.setTag(null);
            buttonPendingAttachmentPreview.setOnClickListener(null);
            buttonPendingAttachmentClear.setOnClickListener(null);
            return;
        }

        pendingAttachmentContainer.setVisibility(View.VISIBLE);
        textPendingAttachmentTitle.setText(pendingAttachment.getFileName());
        textPendingAttachmentMeta.setText(resolvePendingAttachmentMeta(pendingAttachment));
        if (pendingAttachment.isImageType()) {
            imagePendingAttachmentPreview.setVisibility(View.VISIBLE);
            pendingAttachmentThumbnailBinder.onBindPendingAttachmentThumbnail(
                    imagePendingAttachmentPreview,
                    pendingAttachment
            );
        } else {
            imagePendingAttachmentPreview.setVisibility(View.GONE);
            imagePendingAttachmentPreview.setImageDrawable(null);
            imagePendingAttachmentPreview.setTag(null);
        }
        buttonPendingAttachmentPreview.setOnClickListener(previewListener);
        buttonPendingAttachmentClear.setOnClickListener(clearListener);
    }

    private void bindMessages(List<CompanionChatMessageItemModel> items) {
        messageContainer.removeAllViews();
        textEmptyBody.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < items.size(); index++) {
            CompanionChatMessageItemModel item = items.get(index);
            View itemView = inflater.inflate(R.layout.item_companion_chat_message, messageContainer, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(12);
            }
            itemView.setLayoutParams(params);

            LinearLayout root = itemView.findViewById(R.id.layoutCompanionChatMessageRoot);
            TextView senderView = itemView.findViewById(R.id.textCompanionChatSender);
            TextView bodyView = itemView.findViewById(R.id.textCompanionChatBody);
            TextView timeView = itemView.findViewById(R.id.textCompanionChatSentAt);
            MaterialCardView cardView = itemView.findViewById(R.id.cardCompanionChatBody);
            View attachmentContainer = itemView.findViewById(R.id.layoutCompanionChatAttachment);
            ImageView attachmentPreviewView = itemView.findViewById(R.id.imageCompanionChatAttachmentPreview);
            TextView attachmentSummaryView = itemView.findViewById(R.id.textCompanionChatAttachmentSummary);
            MaterialButton attachmentOpenButton = itemView.findViewById(R.id.buttonCompanionChatAttachmentOpen);

            root.setGravity(item.isMine() ? android.view.Gravity.END : android.view.Gravity.START);
            senderView.setText(item.getSenderLabel());
            bodyView.setText(item.getBody());
            bodyView.setVisibility(item.hasBody() ? View.VISIBLE : View.GONE);
            timeView.setText(item.getSentAtLabel());
            cardView.setCardBackgroundColor(ContextCompat.getColor(
                    context,
                    item.isMine() ? R.color.bodeul_soft_blue : R.color.bodeul_surface_alt
            ));
            senderView.setTextColor(ContextCompat.getColor(
                    context,
                    item.isMine() ? R.color.bodeul_primary : R.color.bodeul_text_secondary
            ));
            if (item.hasAttachment() && item.getAttachment() != null) {
                attachmentContainer.setVisibility(View.VISIBLE);
                if (item.hasImageAttachment()) {
                    attachmentPreviewView.setVisibility(View.VISIBLE);
                    messageAttachmentThumbnailBinder.onBindMessageAttachmentThumbnail(
                            attachmentPreviewView,
                            item.getAttachment()
                    );
                } else {
                    attachmentPreviewView.setVisibility(View.GONE);
                    attachmentPreviewView.setImageDrawable(null);
                    attachmentPreviewView.setTag(null);
                }
                attachmentSummaryView.setText(item.getAttachmentSummary());
                attachmentOpenButton.setText(item.getAttachmentActionLabel());
                attachmentOpenButton.setOnClickListener(view ->
                        messageAttachmentActionListener.onOpenAttachment(item.getAttachment()));
            } else {
                attachmentContainer.setVisibility(View.GONE);
                attachmentPreviewView.setVisibility(View.GONE);
                attachmentPreviewView.setImageDrawable(null);
                attachmentPreviewView.setTag(null);
                attachmentOpenButton.setOnClickListener(null);
            }
            messageContainer.addView(itemView);
        }
    }

    private String resolvePendingAttachmentMeta(CompanionChatPendingAttachment pendingAttachment) {
        if (pendingAttachment.isImageType()) {
            return context.getString(R.string.companion_chat_attachment_image);
        }
        if (pendingAttachment.isPdfType()) {
            return context.getString(R.string.companion_chat_attachment_pdf);
        }
        return context.getString(R.string.companion_chat_attachment_file);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
