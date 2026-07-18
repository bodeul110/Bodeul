package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;

interface CompanionRealtimeService {

    RealtimeSnapshotView getSnapshot(
            AppUserRepository.AppUser appUser,
            UUID sessionId);

    ChatMessageView postMessage(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            PostMessageCommand command);

    ReadReceiptView updateReadReceipt(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID lastReadMessageId);

    LocationView postLocation(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            PostLocationCommand command);

    record PostMessageCommand(
            UUID clientMessageId,
            String body,
            List<AttachmentCommand> attachments) {
    }

    record AttachmentCommand(
            String storagePath,
            String fileName,
            String contentType,
            Long sizeBytes) {
    }

    record PostLocationCommand(
            UUID clientLocationId,
            Double latitude,
            Double longitude,
            String capturedAt) {
    }

    record RealtimeSnapshotView(
            String realtimeTopic,
            List<ChatMessageView> messages,
            List<ReadReceiptView> readReceipts,
            List<LocationView> locations) {
    }

    record ChatMessageView(
            UUID id,
            UUID clientMessageId,
            UUID senderUserId,
            String senderRole,
            String body,
            String sentAt,
            List<AttachmentView> attachments) {
    }

    record AttachmentView(
            UUID id,
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes) {
    }

    record ReadReceiptView(
            UUID userId,
            UUID lastReadMessageId,
            String lastReadAt) {
    }

    record LocationView(
            UUID id,
            UUID clientLocationId,
            UUID managerUserId,
            double latitude,
            double longitude,
            String capturedAt) {
    }
}
