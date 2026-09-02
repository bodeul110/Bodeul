package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 무통장입금 원장의 사용자 조회 범위를 표현한다.
 */
public final class BankTransferPayment {
    private final String appointmentRequestId;
    private final BookingPaymentMethod paymentMethod;
    private final BookingPaymentStatus paymentStatus;
    private final int expectedAmount;
    private final String depositorName;
    private final String paymentDueAt;
    @Nullable
    private final Integer receivedAmount;
    private final String confirmedAt;
    private final String refundRequestedAt;
    private final String refundedAt;
    private final long paymentVersion;
    private final boolean instructionAvailable;

    public BankTransferPayment(
            String appointmentRequestId,
            BookingPaymentMethod paymentMethod,
            BookingPaymentStatus paymentStatus,
            int expectedAmount,
            String depositorName,
            String paymentDueAt,
            @Nullable Integer receivedAmount,
            String confirmedAt,
            String refundRequestedAt,
            String refundedAt,
            long paymentVersion,
            boolean instructionAvailable
    ) {
        this.appointmentRequestId = normalize(appointmentRequestId);
        this.paymentMethod = paymentMethod == null
                ? BookingPaymentMethod.UNKNOWN
                : paymentMethod;
        this.paymentStatus = paymentStatus == null
                ? BookingPaymentStatus.UNKNOWN
                : paymentStatus;
        if (expectedAmount < 0) {
            throw new IllegalArgumentException("예상 입금액은 0 이상이어야 합니다.");
        }
        if (receivedAmount != null && receivedAmount < 0) {
            throw new IllegalArgumentException("확인 입금액은 0 이상이어야 합니다.");
        }
        if (paymentVersion < 0L) {
            throw new IllegalArgumentException("결제 버전은 0 이상이어야 합니다.");
        }
        this.expectedAmount = expectedAmount;
        this.depositorName = normalize(depositorName);
        this.paymentDueAt = normalize(paymentDueAt);
        this.receivedAmount = receivedAmount;
        this.confirmedAt = normalize(confirmedAt);
        this.refundRequestedAt = normalize(refundRequestedAt);
        this.refundedAt = normalize(refundedAt);
        this.paymentVersion = paymentVersion;
        this.instructionAvailable = instructionAvailable;
    }

    public String getAppointmentRequestId() {
        return appointmentRequestId;
    }

    public BookingPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BookingPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public int getExpectedAmount() {
        return expectedAmount;
    }

    public String getDepositorName() {
        return depositorName;
    }

    public String getPaymentDueAt() {
        return paymentDueAt;
    }

    @Nullable
    public Integer getReceivedAmount() {
        return receivedAmount;
    }

    public String getConfirmedAt() {
        return confirmedAt;
    }

    public String getRefundRequestedAt() {
        return refundRequestedAt;
    }

    public String getRefundedAt() {
        return refundedAt;
    }

    public long getPaymentVersion() {
        return paymentVersion;
    }

    public boolean isInstructionAvailable() {
        return instructionAvailable;
    }

    public boolean hasDepositorName() {
        return !depositorName.isEmpty();
    }

    public boolean canEditDepositorName() {
        return paymentMethod == BookingPaymentMethod.BANK_TRANSFER
                && paymentVersion >= 0L
                && (paymentStatus == BookingPaymentStatus.AWAITING_DEPOSIT
                || paymentStatus == BookingPaymentStatus.REVIEW_REQUIRED);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
