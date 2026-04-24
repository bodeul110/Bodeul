package com.example.bodeul.domain.model;

/**
 * 관리자 긴급 이슈 대응 결과를 요청 단위로 저장한다.
 */
public final class AdminEmergencyIssueRecord {
    private final String requestId;
    private final AdminEmergencyIssueStatus status;
    private final String note;
    private final String handledByName;
    private final long handledAtMillis;

    public AdminEmergencyIssueRecord(
            String requestId,
            AdminEmergencyIssueStatus status,
            String note,
            String handledByName,
            long handledAtMillis
    ) {
        this.requestId = requestId == null ? "" : requestId;
        this.status = status == null ? AdminEmergencyIssueStatus.REPORTED : status;
        this.note = note == null ? "" : note;
        this.handledByName = handledByName == null ? "" : handledByName;
        this.handledAtMillis = Math.max(handledAtMillis, 0L);
    }

    public String getRequestId() {
        return requestId;
    }

    public AdminEmergencyIssueStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public String getHandledByName() {
        return handledByName;
    }

    public long getHandledAtMillis() {
        return handledAtMillis;
    }
}
