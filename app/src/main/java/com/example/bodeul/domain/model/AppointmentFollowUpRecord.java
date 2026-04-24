package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 완료된 예약의 후기 저장 상태를 담는 후속 기록이다.
 */
public final class AppointmentFollowUpRecord {
    private final String requestId;
    @Nullable
    private final AppointmentFollowUpReviewRating reviewRating;
    private final long reviewSavedAtMillis;
    @Nullable
    private final AppointmentFollowUpSettlementStatus settlementStatus;
    private final String settlementNote;
    private final long settlementSavedAtMillis;
    @Nullable
    private final AppointmentFollowUpSupportEscalationStatus supportEscalationStatus;
    private final long supportEscalatedAtMillis;

    public AppointmentFollowUpRecord(
            String requestId,
            @Nullable AppointmentFollowUpReviewRating reviewRating,
            long reviewSavedAtMillis,
            @Nullable AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            long settlementSavedAtMillis,
            @Nullable AppointmentFollowUpSupportEscalationStatus supportEscalationStatus,
            long supportEscalatedAtMillis
    ) {
        this.requestId = requestId == null ? "" : requestId;
        this.reviewRating = reviewRating;
        this.reviewSavedAtMillis = reviewSavedAtMillis;
        this.settlementStatus = settlementStatus;
        this.settlementNote = settlementNote == null ? "" : settlementNote;
        this.settlementSavedAtMillis = settlementSavedAtMillis;
        this.supportEscalationStatus = supportEscalationStatus;
        this.supportEscalatedAtMillis = supportEscalatedAtMillis;
    }

    public static AppointmentFollowUpRecord empty(String requestId) {
        return new AppointmentFollowUpRecord(requestId, null, 0L, null, "", 0L, null, 0L);
    }

    public String getRequestId() {
        return requestId;
    }

    @Nullable
    public AppointmentFollowUpReviewRating getReviewRating() {
        return reviewRating;
    }

    public long getReviewSavedAtMillis() {
        return reviewSavedAtMillis;
    }

    @Nullable
    public AppointmentFollowUpSettlementStatus getSettlementStatus() {
        return settlementStatus;
    }

    public String getSettlementNote() {
        return settlementNote;
    }

    public long getSettlementSavedAtMillis() {
        return settlementSavedAtMillis;
    }

    @Nullable
    public AppointmentFollowUpSupportEscalationStatus getSupportEscalationStatus() {
        return supportEscalationStatus;
    }

    public long getSupportEscalatedAtMillis() {
        return supportEscalatedAtMillis;
    }

    public boolean hasSavedReview() {
        return reviewRating != null && reviewSavedAtMillis > 0L;
    }

    public boolean hasSavedSettlement() {
        return settlementStatus != null && settlementSavedAtMillis > 0L;
    }

    public boolean hasSavedSupportEscalation() {
        return supportEscalationStatus != null && supportEscalatedAtMillis > 0L;
    }

    public boolean hasAnySavedAction() {
        return hasSavedReview() || hasSavedSettlement() || hasSavedSupportEscalation();
    }
}
