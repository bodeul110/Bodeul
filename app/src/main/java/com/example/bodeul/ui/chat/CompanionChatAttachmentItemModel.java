package com.example.bodeul.ui.chat;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionChatAttachment;

public final class CompanionChatAttachmentItemModel {
    @Nullable
    private final CompanionChatAttachment attachment;
    private final String summary;
    private final String actionLabel;

    public CompanionChatAttachmentItemModel(
            @Nullable CompanionChatAttachment attachment,
            String summary,
            String actionLabel
    ) {
        this.attachment = attachment;
        this.summary = summary;
        this.actionLabel = actionLabel;
    }

    @Nullable
    public CompanionChatAttachment getAttachment() {
        return attachment;
    }

    public String getSummary() {
        return summary;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public boolean hasImageAttachment() {
        return attachment != null && attachment.isImageType();
    }
}
