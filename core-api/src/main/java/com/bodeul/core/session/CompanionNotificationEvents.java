package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CompanionChatMessageCreatedEvent(
        UUID sessionId,
        UUID appointmentRequestId,
        String senderRole,
        Instant sentAt,
        List<UUID> recipientUserIds) {
}

record CompanionLocationAlertChangedEvent(
        UUID sessionId,
        UUID appointmentRequestId,
        String alertStage,
        List<UUID> recipientUserIds) {
}
