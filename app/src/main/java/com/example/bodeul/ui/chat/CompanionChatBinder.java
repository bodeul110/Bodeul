package com.example.bodeul.ui.chat;

import android.content.Context;
import android.view.Gravity;
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

    public interface PendingAttachmentActionListener {
        void onPreviewPendingAttachment(CompanionChatPendingAttachment pendingAttachment);

        void onRemovePendingAttachment(CompanionChatPendingAttachment pendingAttachment);
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
    private final LinearLayout pendingAttachmentItemsContainer;
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
            LinearLayout pendingAttachmentItemsContainer,
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
        this.pendingAttachmentItemsContainer = pendingAttachmentItemsContainer;
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

    public void bindPendingAttachments(
            List<CompanionChatPendingAttachment> pendingAttachments,
            PendingAttachmentActionListener actionListener
    ) {
        pendingAttachmentItemsContainer.removeAllViews();
        if (pendingAttachments == null || pendingAttachments.isEmpty()) {
            pendingAttachmentContainer.setVisibility(View.GONE);
            return;
        }

        pendingAttachmentContainer.setVisibility(View.VISIBLE);
        for (int index = 0; index < pendingAttachments.size(); index++) {
            CompanionChatPendingAttachment item = pendingAttachments.get(index);
            View itemView = inflater.inflate(
                    R.layout.item_companion_chat_pending_attachment,
                    pendingAttachmentItemsContainer,
                    false
            );
            ImageView imagePreview = itemView.findViewById(R.id.imageCompanionChatPendingAttachmentPreview);
            TextView textTitle = itemView.findViewById(R.id.textCompanionChatPendingAttachmentTitle);
            TextView textMeta = itemView.findViewById(R.id.textCompanionChatPendingAttachmentMeta);
            MaterialButton buttonPreview = itemView.findViewById(R.id.buttonCompanionChatAttachmentPreview);
            MaterialButton buttonClear = itemView.findViewById(R.id.buttonCompanionChatAttachmentClear);

            if (index == pendingAttachments.size() - 1) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
                params.bottomMargin = 0;
                itemView.setLayoutParams(params);
            }

            textTitle.setText(item.getFileName());
            textMeta.setText(resolvePendingAttachmentMeta(item));
            if (item.isImageType()) {
                imagePreview.setVisibility(View.VISIBLE);
                pendingAttachmentThumbnailBinder.onBindPendingAttachmentThumbnail(imagePreview, item);
            } else {
                imagePreview.setVisibility(View.GONE);
                imagePreview.setImageDrawable(null);
                imagePreview.setTag(null);
            }
            buttonPreview.setOnClickListener(view -> actionListener.onPreviewPendingAttachment(item));
            buttonClear.setOnClickListener(view -> actionListener.onRemovePendingAttachment(item));
            pendingAttachmentItemsContainer.addView(itemView);
        }
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
            LinearLayout attachmentItemsContainer =
                    itemView.findViewById(R.id.layoutCompanionChatAttachmentItems);

            root.setGravity(item.isMine() ? Gravity.END : Gravity.START);
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
            bindMessageAttachments(item.getAttachments(), attachmentContainer, attachmentItemsContainer);
            messageContainer.addView(itemView);
        }
    }

    private void bindMessageAttachments(
            List<CompanionChatAttachmentItemModel> attachments,
            View attachmentContainer,
            LinearLayout attachmentItemsContainer
    ) {
        attachmentItemsContainer.removeAllViews();
        if (attachments == null || attachments.isEmpty()) {
            attachmentContainer.setVisibility(View.GONE);
            return;
        }

        attachmentContainer.setVisibility(View.VISIBLE);
        for (int index = 0; index < attachments.size(); index++) {
            CompanionChatAttachmentItemModel item = attachments.get(index);
            View itemView = inflater.inflate(
                    R.layout.item_companion_chat_message_attachment,
                    attachmentItemsContainer,
                    false
            );
            ImageView imagePreview = itemView.findViewById(R.id.imageCompanionChatAttachmentPreview);
            TextView textSummary = itemView.findViewById(R.id.textCompanionChatAttachmentSummary);
            MaterialButton buttonOpen = itemView.findViewById(R.id.buttonCompanionChatAttachmentOpen);

            if (index == attachments.size() - 1) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
                params.bottomMargin = 0;
                itemView.setLayoutParams(params);
            }

            if (item.hasImageAttachment() && item.getAttachment() != null) {
                imagePreview.setVisibility(View.VISIBLE);
                messageAttachmentThumbnailBinder.onBindMessageAttachmentThumbnail(
                        imagePreview,
                        item.getAttachment()
                );
            } else {
                imagePreview.setVisibility(View.GONE);
                imagePreview.setImageDrawable(null);
                imagePreview.setTag(null);
            }
            textSummary.setText(item.getSummary());
            buttonOpen.setText(item.getActionLabel());
            buttonOpen.setOnClickListener(view -> {
                if (item.getAttachment() != null) {
                    messageAttachmentActionListener.onOpenAttachment(item.getAttachment());
                }
            });
            attachmentItemsContainer.addView(itemView);
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
