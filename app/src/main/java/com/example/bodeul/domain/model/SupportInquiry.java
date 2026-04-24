package com.example.bodeul.domain.model;

/**
 * 매니저 지원 문의 한 건과 관리자 응답 상태를 담는다.
 */
public final class SupportInquiry {
    private final String id;
    private final String managerUserId;
    private final String managerName;
    private final SupportInquiryCategory category;
    private final String title;
    private final String body;
    private final SupportInquiryStatus status;
    private final long createdAtMillis;
    private final String responseText;
    private final long respondedAtMillis;
    private final String respondedByName;

    public SupportInquiry(
            String id,
            String managerUserId,
            String managerName,
            SupportInquiryCategory category,
            String title,
            String body,
            SupportInquiryStatus status,
            long createdAtMillis,
            String responseText,
            long respondedAtMillis,
            String respondedByName
    ) {
        this.id = id == null ? "" : id;
        this.managerUserId = managerUserId == null ? "" : managerUserId;
        this.managerName = managerName == null ? "" : managerName;
        this.category = category == null ? SupportInquiryCategory.MATCHING : category;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.status = status == null ? SupportInquiryStatus.RECEIVED : status;
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
        this.responseText = responseText == null ? "" : responseText;
        this.respondedAtMillis = Math.max(respondedAtMillis, 0L);
        this.respondedByName = respondedByName == null ? "" : respondedByName;
    }

    public String getId() {
        return id;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    public String getManagerName() {
        return managerName;
    }

    public SupportInquiryCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public SupportInquiryStatus getStatus() {
        return status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getResponseText() {
        return responseText;
    }

    public long getRespondedAtMillis() {
        return respondedAtMillis;
    }

    public String getRespondedByName() {
        return respondedByName;
    }
}
