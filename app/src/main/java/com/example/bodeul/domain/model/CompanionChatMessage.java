package com.example.bodeul.domain.model;

/**
 * 동행 진행 중 참여자와 매니저가 주고받는 안심 채팅 한 건을 나타낸다.
 */
public final class CompanionChatMessage {
    private final UserRole senderRole;
    private final String body;
    private final long sentAtMillis;
    private final CompanionChatAttachment attachment;

    public CompanionChatMessage(UserRole senderRole, String body, long sentAtMillis) {
        this(senderRole, body, sentAtMillis, null);
    }

    public CompanionChatMessage(
            UserRole senderRole,
            String body,
            long sentAtMillis,
            CompanionChatAttachment attachment
    ) {
        this.senderRole = senderRole;
        this.body = body == null ? "" : body;
        this.sentAtMillis = sentAtMillis;
        this.attachment = attachment;
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

    public CompanionChatAttachment getAttachment() {
        return attachment;
    }

    public boolean hasAttachment() {
        return attachment != null && !attachment.isEmpty();
    }
}
