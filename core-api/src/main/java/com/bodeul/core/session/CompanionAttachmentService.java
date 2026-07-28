package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.web.multipart.MultipartFile;

interface CompanionAttachmentService {

    CompanionRealtimeService.ChatMessageView postMessage(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID clientMessageId,
            String body,
            List<MultipartFile> attachments);

    DownloadedAttachment download(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID attachmentId);

    record DownloadedAttachment(
            String fileName,
            String contentType,
            byte[] content) {
    }
}
