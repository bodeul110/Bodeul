package com.example.bodeul.domain.model;

/**
 * 관리자 요청별 후속 처리 상태를 한 곳에서 묶는다.
 */
public final class AdminRequestActionOverview {
    private final String requestId;
    private final AdminSettlementRecord settlementRecord;
    private final AdminEmergencyIssueRecord emergencyIssueRecord;
    private final AppointmentFollowUpRecord followUpRecord;

    public AdminRequestActionOverview(
            String requestId,
            AdminSettlementRecord settlementRecord,
            AdminEmergencyIssueRecord emergencyIssueRecord,
            AppointmentFollowUpRecord followUpRecord
    ) {
        this.requestId = requestId == null ? "" : requestId;
        this.settlementRecord = settlementRecord;
        this.emergencyIssueRecord = emergencyIssueRecord;
        this.followUpRecord = followUpRecord;
    }

    public String getRequestId() {
        return requestId;
    }

    public AdminSettlementRecord getSettlementRecord() {
        return settlementRecord;
    }

    public AdminEmergencyIssueRecord getEmergencyIssueRecord() {
        return emergencyIssueRecord;
    }

    public AppointmentFollowUpRecord getFollowUpRecord() {
        return followUpRecord;
    }
}
