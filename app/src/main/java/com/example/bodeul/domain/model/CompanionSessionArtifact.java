package com.example.bodeul.domain.model;

/** 동행 단계에서 선택 등록한 결제 증빙 또는 처방 이미지 메타데이터다. */
public final class CompanionSessionArtifact {
    private final String id;
    private final String purpose;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final long createdAtMillis;

    public CompanionSessionArtifact(
            String id,
            String purpose,
            String fileName,
            String contentType,
            long sizeBytes,
            long createdAtMillis
    ) {
        this.id = id == null ? "" : id;
        this.purpose = purpose == null ? "" : purpose;
        this.fileName = fileName == null ? "" : fileName;
        this.contentType = contentType == null ? "" : contentType;
        this.sizeBytes = Math.max(sizeBytes, 0L);
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
    }

    public String getId() { return id; }
    public String getPurpose() { return purpose; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public long getCreatedAtMillis() { return createdAtMillis; }
}
