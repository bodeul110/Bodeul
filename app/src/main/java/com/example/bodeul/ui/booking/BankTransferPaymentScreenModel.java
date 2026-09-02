package com.example.bodeul.ui.booking;

public final class BankTransferPaymentScreenModel {
    private final String statusLabel;
    private final String statusBody;
    private final String expectedAmount;
    private final String paymentDueAt;
    private final String receivedAmount;
    private final String confirmedAt;
    private final String refundRequestedAt;
    private final String refundedAt;
    private final String currentDepositorName;
    private final String depositorInputValue;
    private final String depositorHelper;
    private final String instructionNotice;
    private final boolean depositorEditable;

    public BankTransferPaymentScreenModel(
            String statusLabel,
            String statusBody,
            String expectedAmount,
            String paymentDueAt,
            String receivedAmount,
            String confirmedAt,
            String refundRequestedAt,
            String refundedAt,
            String currentDepositorName,
            String depositorInputValue,
            String depositorHelper,
            String instructionNotice,
            boolean depositorEditable
    ) {
        this.statusLabel = statusLabel;
        this.statusBody = statusBody;
        this.expectedAmount = expectedAmount;
        this.paymentDueAt = paymentDueAt;
        this.receivedAmount = receivedAmount;
        this.confirmedAt = confirmedAt;
        this.refundRequestedAt = refundRequestedAt;
        this.refundedAt = refundedAt;
        this.currentDepositorName = currentDepositorName;
        this.depositorInputValue = depositorInputValue;
        this.depositorHelper = depositorHelper;
        this.instructionNotice = instructionNotice;
        this.depositorEditable = depositorEditable;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusBody() {
        return statusBody;
    }

    public String getExpectedAmount() {
        return expectedAmount;
    }

    public String getPaymentDueAt() {
        return paymentDueAt;
    }

    public String getReceivedAmount() {
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

    public String getCurrentDepositorName() {
        return currentDepositorName;
    }

    public String getDepositorInputValue() {
        return depositorInputValue;
    }

    public String getDepositorHelper() {
        return depositorHelper;
    }

    public String getInstructionNotice() {
        return instructionNotice;
    }

    public boolean isDepositorEditable() {
        return depositorEditable;
    }
}
