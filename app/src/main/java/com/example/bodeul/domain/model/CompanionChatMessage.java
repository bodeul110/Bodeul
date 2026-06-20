package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 동행 세션 참여자가 주고받는 안심 채팅 한 건을 표현한다.
 */
public final class CompanionChatMessage {
    private final UserRole senderRole;
    private final String body;
    private final long sentAtMillis;
    private final List<CompanionChatAttachment> attachments;

    public CompanionChatMessage(UserRole senderRole, String body, long sentAtMillis) {
        this(senderRole, body, sentAtMillis, Collections.emptyList());
    }

    public CompanionChatMessage(
            UserRole senderRole,
            String body,
            long sentAtMillis,
            @Nullable CompanionChatAttachment attachment
    ) {
        this(
                senderRole,
                body,
                sentAtMillis,
                attachment == null || attachment.isEmpty()
                        ? Collections.emptyList()
                        : Collections.singletonList(attachment)
        );
    }

    public CompanionChatMessage(
            UserRole senderRole,
            String body,
            long sentAtMillis,
            List<CompanionChatAttachment> attachments
    ) {
        this.senderRole = senderRole;
        this.body = body == null ? "" : body;
        this.sentAtMillis = sentAtMillis;
        this.attachments = attachments == null
                ? new ArrayList<>()
                : new ArrayList<>(attachments);
    }

    public UserRole getSenderRole() {
        return senderRole;
    }

    public String getBody() {
        return body;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }

    @Nullable
    public CompanionChatAttachment getAttachment() {
        return attachments.isEmpty() ? null : attachments.get(0);
    }

    public List<CompanionChatAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    public boolean hasAttachment() {
        return !attachments.isEmpty();
    }
}
