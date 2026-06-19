package com.example.bodeul.ui.chat;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionChatAttachment;

public final class CompanionChatMessageItemModel {
    private final String senderLabel;
    private final String body;
    private final String sentAtLabel;
    private final boolean mine;
    @Nullable
    private final CompanionChatAttachment attachment;
    private final String attachmentSummary;
    private final String attachmentActionLabel;

    public CompanionChatMessageItemModel(
            String senderLabel,
            String body,
            String sentAtLabel,
            boolean mine,
            @Nullable CompanionChatAttachment attachment,
            String attachmentSummary,
            String attachmentActionLabel
    ) {
        this.senderLabel = senderLabel;
        this.body = body;
        this.sentAtLabel = sentAtLabel;
        this.mine = mine;
        this.attachment = attachment;
        this.attachmentSummary = attachmentSummary;
        this.attachmentActionLabel = attachmentActionLabel;
    }

    public String getSenderLabel() {
        return senderLabel;
    }

    public String getBody() {
        return body;
    }

    public String getSentAtLabel() {
        return sentAtLabel;
    }

    public boolean isMine() {
        return mine;
    }

    public boolean hasBody() {
        return body != null && !body.trim().isEmpty();
    }

    public boolean hasAttachment() {
        return attachment != null && !attachment.isEmpty();
    }

    @Nullable
    public CompanionChatAttachment getAttachment() {
        return attachment;
    }

    public String getAttachmentSummary() {
        return attachmentSummary;
    }

    public String getAttachmentActionLabel() {
        return attachmentActionLabel;
    }
}
