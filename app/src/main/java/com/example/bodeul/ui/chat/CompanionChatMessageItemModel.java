package com.example.bodeul.ui.chat;

public final class CompanionChatMessageItemModel {
    private final String senderLabel;
    private final String body;
    private final String sentAtLabel;
    private final boolean mine;

    public CompanionChatMessageItemModel(
            String senderLabel,
            String body,
            String sentAtLabel,
            boolean mine
    ) {
        this.senderLabel = senderLabel;
        this.body = body;
        this.sentAtLabel = sentAtLabel;
        this.mine = mine;
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
}
