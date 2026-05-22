package com.example.bodeul.domain.model;

/**
 * 매니저 원본 서류 한 건의 Storage 경로와 기본 메타데이터를 담는다.
 */
public final class ManagerDocumentFileMetadata {
    private final ManagerDocumentFileType fileType;
    private final String fullPath;
    private final String fileName;
    private final String contentType;
    private final long uploadedAtMillis;
    private final String previewUri;

    public ManagerDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            String fullPath,
            String fileName,
            String contentType,
            long uploadedAtMillis
    ) {
        this(
                fileType,
                fullPath,
                fileName,
                contentType,
                uploadedAtMillis,
                ""
        );
    }

    public ManagerDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            String fullPath,
            String fileName,
            String contentType,
            long uploadedAtMillis,
            String previewUri
    ) {
        this.fileType = fileType;
        this.fullPath = fullPath == null ? "" : fullPath;
        this.fileName = fileName == null ? "" : fileName;
        this.contentType = contentType == null ? "" : contentType;
        this.uploadedAtMillis = Math.max(uploadedAtMillis, 0L);
        this.previewUri = previewUri == null ? "" : previewUri;
    }

    public ManagerDocumentFileType getFileType() {
        return fileType;
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
        return fileType == null || fullPath.isEmpty();
    }
}
