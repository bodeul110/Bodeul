package com.example.bodeul.domain.model;

/**
 * 동행 진행 중 참여자와 매니저가 주고받는 안심 채팅 한 건을 나타낸다.
 */
public final class CompanionChatMessage {
    private final UserRole senderRole;
    private final String body;
    private final long sentAtMillis;

    public CompanionChatMessage(UserRole senderRole, String body, long sentAtMillis) {
        this.senderRole = senderRole;
        this.body = body;
        this.sentAtMillis = sentAtMillis;
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
}
