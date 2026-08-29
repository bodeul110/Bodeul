package com.bodeul.core.session;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("database")
class DefaultCompanionAttachmentService implements CompanionAttachmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DefaultCompanionAttachmentService.class);
    private static final int MAX_ATTACHMENTS = 3;
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_MESSAGE_ATTACHMENT_BYTES = MAX_ATTACHMENTS
            * MAX_ATTACHMENT_SIZE_BYTES;
    private static final Set<String> CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2d};

    private final CompanionRealtimeService realtimeService;
    private final CompanionAttachmentStorage attachmentStorage;

    DefaultCompanionAttachmentService(
            CompanionRealtimeService realtimeService,
            CompanionAttachmentStorage attachmentStorage) {
        this.realtimeService = realtimeService;
        this.attachmentStorage = attachmentStorage;
    }

    @Override
    public CompanionRealtimeService.ChatMessageView postMessage(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID clientMessageId,
            String body,
            List<MultipartFile> attachments) {
        if (clientMessageId == null) {
            throw CompanionSessionException.invalidRequest("메시지 재시도 식별자가 필요합니다.");
        }
        List<MultipartFile> files = attachments == null ? List.of() : attachments;
        if (files.size() > MAX_ATTACHMENTS) {
            throw CompanionSessionException.invalidRequest("첨부 파일은 메시지당 3개까지 등록할 수 있습니다.");
        }

        realtimeService.validateAttachmentWrite(appUser, sessionId);
        validateAttachments(sessionId, files);
        List<CompanionRealtimeService.AttachmentCommand> attachmentCommands = new ArrayList<>();
        List<String> createdStoragePaths = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                PreparedAttachment attachment = prepareAttachment(
                        sessionId,
                        clientMessageId,
                        index,
                        files.get(index));
                CompanionAttachmentStorage.StoreResult result = attachmentStorage.store(
                        attachment.command().storagePath(),
                        attachment.content(),
                        attachment.command().contentType(),
                        attachment.sha256());
                if (result.created()) {
                    createdStoragePaths.add(attachment.command().storagePath());
                }
                attachmentCommands.add(attachment.command());
            }
            return realtimeService.postMessage(
                    appUser,
                    sessionId,
                    new CompanionRealtimeService.PostMessageCommand(
                            clientMessageId,
                            body,
                            List.copyOf(attachmentCommands)));
        } catch (RuntimeException exception) {
            compensate(createdStoragePaths, clientMessageId);
            throw exception;
        }
    }

    @Override
    public DownloadedAttachment download(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID attachmentId) {
        CompanionRealtimeService.AttachmentView attachment = realtimeService.getAttachment(
                appUser,
                sessionId,
                attachmentId);
        byte[] content = attachmentStorage.read(
                attachment.storagePath(),
                MAX_ATTACHMENT_SIZE_BYTES);
        if (content.length != attachment.sizeBytes()) {
            throw CompanionSessionException.attachmentUnavailable();
        }
        return new DownloadedAttachment(
                attachment.fileName(),
                attachment.contentType(),
                content);
    }

    private void validateAttachments(UUID sessionId, List<MultipartFile> files) {
        if (sessionId == null) {
            throw CompanionSessionException.invalidRequest("동행 세션 ID가 필요합니다.");
        }
        long totalBytes = 0L;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw CompanionSessionException.invalidRequest("비어 있는 첨부 파일은 보낼 수 없습니다.");
            }
            long sizeBytes = file.getSize();
            if (sizeBytes <= 0L || sizeBytes > MAX_ATTACHMENT_SIZE_BYTES) {
                throw CompanionSessionException.invalidRequest("첨부 파일 크기는 10MiB 이하여야 합니다.");
            }
            totalBytes += sizeBytes;
            if (totalBytes > MAX_MESSAGE_ATTACHMENT_BYTES) {
                throw CompanionSessionException.invalidRequest("첨부 파일 전체 크기를 확인해 주세요.");
            }

            String contentType = normalizeContentType(file.getContentType());
            if (!CONTENT_TYPES.contains(contentType)) {
                throw CompanionSessionException.invalidRequest("JPEG, PNG 또는 PDF 파일만 첨부할 수 있습니다.");
            }
        }
    }

    private PreparedAttachment prepareAttachment(
            UUID sessionId,
            UUID clientMessageId,
            int index,
            MultipartFile file) {
        long sizeBytes = file.getSize();
        String contentType = normalizeContentType(file.getContentType());
        String fileName = sanitizeFileName(file.getOriginalFilename());
        byte[] content = readContent(file, sizeBytes);
        if (!hasExpectedSignature(contentType, content)) {
            throw CompanionSessionException.invalidRequest("첨부 파일 형식과 내용이 일치하지 않습니다.");
        }
        String sha256 = sha256(content);
        String storagePath = "companion-chat-attachments/"
                + sessionId + "/" + clientMessageId + "/"
                + index + "-" + sha256 + "." + extension(contentType);
        return new PreparedAttachment(
                new CompanionRealtimeService.AttachmentCommand(
                        storagePath,
                        fileName,
                        contentType,
                        sizeBytes),
                content,
                sha256);
    }

    private byte[] readContent(MultipartFile file, long expectedSize) {
        try {
            byte[] content = file.getBytes();
            if (content.length != expectedSize || content.length > MAX_ATTACHMENT_SIZE_BYTES) {
                throw CompanionSessionException.invalidRequest("첨부 파일 크기가 전송 중 변경되었습니다.");
            }
            return content;
        } catch (IOException exception) {
            throw CompanionSessionException.attachmentUnavailable();
        }
    }

    private String sanitizeFileName(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        normalized = normalized.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_");
        if (normalized.isBlank()) {
            normalized = "companion-chat-attachment";
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private boolean hasExpectedSignature(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(content, JPEG_SIGNATURE);
            case "image/png" -> startsWith(content, PNG_SIGNATURE);
            case "application/pdf" -> startsWith(content, PDF_SIGNATURE);
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalizeContentType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            default -> throw CompanionSessionException.invalidRequest("첨부 파일 형식을 확인해 주세요.");
        };
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void compensate(List<String> storagePaths, UUID clientMessageId) {
        int failedDeletes = 0;
        for (String storagePath : storagePaths) {
            try {
                attachmentStorage.delete(storagePath);
            } catch (RuntimeException exception) {
                failedDeletes++;
            }
        }
        if (failedDeletes > 0) {
            LOGGER.warn(
                    "채팅 첨부 보상 삭제를 완료하지 못했습니다. clientMessageId={}, failedCount={}",
                    clientMessageId,
                    failedDeletes);
        }
    }

    private record PreparedAttachment(
            CompanionRealtimeService.AttachmentCommand command,
            byte[] content,
            String sha256) {
    }
}
