package com.example.bodeul.domain.model;

/**
 * 매니저 홈에서 빠르게 확인하고 수정하는 서류 / 일정 요약 정보다.
 */
public class ManagerHomeProfile {
    private final String documentSummary;
    private final String availabilitySummary;
    private final ManagerDocumentStatus documentStatus;
    private final String documentReviewNote;

    public ManagerHomeProfile(String documentSummary, String availabilitySummary) {
        this(documentSummary, availabilitySummary, ManagerDocumentStatus.NOT_SUBMITTED, "");
    }

    public ManagerHomeProfile(
            String documentSummary,
            String availabilitySummary,
            ManagerDocumentStatus documentStatus,
            String documentReviewNote
    ) {
        this.documentSummary = documentSummary == null ? "" : documentSummary;
        this.availabilitySummary = availabilitySummary == null ? "" : availabilitySummary;
        this.documentStatus = documentStatus == null
                ? ManagerDocumentStatus.NOT_SUBMITTED
                : documentStatus;
        this.documentReviewNote = documentReviewNote == null ? "" : documentReviewNote;
    }

    public String getDocumentSummary() {
        return documentSummary;
    }

    public String getAvailabilitySummary() {
        return availabilitySummary;
    }

    public ManagerDocumentStatus getDocumentStatus() {
        return documentStatus;
    }

    public String getDocumentReviewNote() {
        return documentReviewNote;
    }
}
