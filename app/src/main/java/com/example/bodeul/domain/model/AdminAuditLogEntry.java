package com.example.bodeul.domain.model;

/**
 * 관리자 후속 처리 액션의 감사 로그 한 건을 나타낸다.
 */
public final class AdminAuditLogEntry {
    private final String id;
    private final AdminActionSourceType sourceType;
    private final String requestId;
    private final String inquiryId;
    private final String actionSummary;
    private final String note;
    private final String actorName;
    private final long createdAtMillis;

    public AdminAuditLogEntry(
            String id,
            AdminActionSourceType sourceType,
            String requestId,
            String inquiryId,
            String actionSummary,
            String note,
            String actorName,
            long createdAtMillis
    ) {
        this.id = id == null ? "" : id;
        this.sourceType = sourceType == null ? AdminActionSourceType.SUPPORT : sourceType;
        this.requestId = requestId == null ? "" : requestId;
        this.inquiryId = inquiryId == null ? "" : inquiryId;
        this.actionSummary = actionSummary == null ? "" : actionSummary;
        this.note = note == null ? "" : note;
        this.actorName = actorName == null ? "" : actorName;
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
    }

    public String getId() {
        return id;
    }

    public AdminActionSourceType getSourceType() {
        return sourceType;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInquiryId() {
        return inquiryId;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public String getNote() {
        return note;
    }

    public String getActorName() {
        return actorName;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
