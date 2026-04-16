package com.example.bodeul.domain.model;

/**
 * 매니저 홈에서 빠르게 확인하고 수정하는 서류 / 일정 요약 정보다.
 */
public class ManagerHomeProfile {
    private final String documentSummary;
    private final String availabilitySummary;
    private final ManagerDocumentStatus documentStatus;
    private final String documentReviewNote;
    private final long documentUpdatedAtMillis;
    private final long documentReviewedAtMillis;
    private final String documentReviewedByName;

    public ManagerHomeProfile(String documentSummary, String availabilitySummary) {
        this(documentSummary, availabilitySummary, ManagerDocumentStatus.NOT_SUBMITTED, "", 0L, 0L, "");
    }

    public ManagerHomeProfile(
            String documentSummary,
            String availabilitySummary,
            ManagerDocumentStatus documentStatus,
            String documentReviewNote
    ) {
        this(documentSummary, availabilitySummary, documentStatus, documentReviewNote, 0L, 0L, "");
    }

    public ManagerHomeProfile(
            String documentSummary,
            String availabilitySummary,
            ManagerDocumentStatus documentStatus,
            String documentReviewNote,
            long documentUpdatedAtMillis,
            long documentReviewedAtMillis,
            String documentReviewedByName
    ) {
        this.documentSummary = documentSummary == null ? "" : documentSummary;
        this.availabilitySummary = availabilitySummary == null ? "" : availabilitySummary;
        this.documentStatus = documentStatus == null
                ? ManagerDocumentStatus.NOT_SUBMITTED
                : documentStatus;
        this.documentReviewNote = documentReviewNote == null ? "" : documentReviewNote;
        this.documentUpdatedAtMillis = Math.max(documentUpdatedAtMillis, 0L);
        this.documentReviewedAtMillis = Math.max(documentReviewedAtMillis, 0L);
        this.documentReviewedByName = documentReviewedByName == null ? "" : documentReviewedByName;
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

    public long getDocumentUpdatedAtMillis() {
        return documentUpdatedAtMillis;
    }

    public long getDocumentReviewedAtMillis() {
        return documentReviewedAtMillis;
    }

    public String getDocumentReviewedByName() {
        return documentReviewedByName;
    }
}
