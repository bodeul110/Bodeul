package com.example.bodeul.domain.model;

/**
 * 관리자 정산 후속 처리 결과를 요청 단위로 저장한다.
 */
public final class AdminSettlementRecord {
    private final String requestId;
    private final AdminSettlementStatus status;
    private final String note;
    private final String handledByName;
    private final long handledAtMillis;

    public AdminSettlementRecord(
            String requestId,
            AdminSettlementStatus status,
            String note,
            String handledByName,
            long handledAtMillis
    ) {
        this.requestId = requestId == null ? "" : requestId;
        this.status = status == null ? AdminSettlementStatus.PENDING : status;
        this.note = note == null ? "" : note;
        this.handledByName = handledByName == null ? "" : handledByName;
        this.handledAtMillis = Math.max(handledAtMillis, 0L);
    }

    public String getRequestId() {
        return requestId;
    }

    public AdminSettlementStatus getStatus() {
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
