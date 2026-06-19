package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 안심 채팅 메시지에 함께 저장되는 첨부 파일 메타데이터다.
 */
public final class CompanionChatAttachment {
    private final String fullPath;
    private final String fileName;
    private final String contentType;
    private final long uploadedAtMillis;
    private final String previewUri;

    public CompanionChatAttachment(
            String fullPath,
            String fileName,
            String contentType,
            long uploadedAtMillis
    ) {
        this(fullPath, fileName, contentType, uploadedAtMillis, "");
    }

    public CompanionChatAttachment(
            String fullPath,
            String fileName,
            String contentType,
            long uploadedAtMillis,
            @Nullable String previewUri
    ) {
        this.fullPath = fullPath == null ? "" : fullPath;
        this.fileName = fileName == null ? "" : fileName;
        this.contentType = contentType == null ? "" : contentType;
        this.uploadedAtMillis = Math.max(uploadedAtMillis, 0L);
        this.previewUri = previewUri == null ? "" : previewUri;
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getUploadedAtMillis() {
        return uploadedAtMillis;
    }

    public String getPreviewUri() {
        return previewUri;
    }

    public boolean isEmpty() {
        return fullPath.isEmpty();
    }

    public boolean isImageType() {
        return contentType.startsWith("image/");
    }

    public boolean isPdfType() {
        return "application/pdf".equals(contentType);
    }
}
