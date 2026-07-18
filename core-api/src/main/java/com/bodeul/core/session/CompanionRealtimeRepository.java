package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CompanionRealtimeRepository {

    List<ChatMessageRecord> findRecentMessages(UUID sessionId, int limit);

    List<ReadReceiptRecord> findReadReceipts(UUID sessionId);

    List<LocationRecord> findRecentLocations(UUID sessionId, int limit);

    MessageSaveResult saveMessage(MessageMutation mutation);

    Optional<ReadReceiptRecord> upsertReadReceipt(
            UUID sessionId,
            UUID userId,
            UUID lastReadMessageId);

    Optional<LocationRecord> saveLocation(LocationMutation mutation);

    record MessageMutation(
            UUID sessionId,
            UUID clientMessageId,
            UUID senderUserId,
            String senderRole,
            String body,
            List<AttachmentMutation> attachments) {
    }

    record AttachmentMutation(
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes) {
    }

    record LocationMutation(
            UUID sessionId,
            UUID clientLocationId,
            UUID managerUserId,
            double latitude,
            double longitude,
            Instant capturedAt) {
    }

    record MessageSaveResult(ChatMessageRecord message, boolean created) {
    }

    record ChatMessageRecord(
            UUID id,
            UUID sessionId,
            UUID clientMessageId,
            UUID senderUserId,
            String senderRole,
            String body,
            Instant sentAt,
            List<AttachmentRecord> attachments) {
    }

    record AttachmentRecord(
            UUID id,
            UUID chatMessageId,
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes) {
    }

    record ReadReceiptRecord(
            UUID sessionId,
            UUID userId,
            UUID lastReadMessageId,
            Instant lastReadAt) {
    }

    record LocationRecord(
            UUID id,
            UUID sessionId,
            UUID clientLocationId,
            UUID managerUserId,
            double latitude,
            double longitude,
            Instant capturedAt) {
    }
}
