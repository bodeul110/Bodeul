package com.example.bodeul.domain.model;

/**
 * 결제 승인 단계에서 확정된 승인 결과를 예약 요청에 함께 보관한다.
 */
public final class BookingPaymentApproval {
    private final BookingPaymentStatus status;
    private final String providerLabel;
    private final String approvalCode;
    private final String approvedAt;

    private BookingPaymentApproval(
            BookingPaymentStatus status,
            String providerLabel,
            String approvalCode,
            String approvedAt
    ) {
        this.status = status == null ? BookingPaymentStatus.PENDING : status;
        this.providerLabel = normalize(providerLabel);
        this.approvalCode = normalize(approvalCode);
        this.approvedAt = normalize(approvedAt);
    }

    public static BookingPaymentApproval empty() {
        return new BookingPaymentApproval(BookingPaymentStatus.PENDING, "", "", "");
    }

    public static BookingPaymentApproval authorized(String providerLabel, String approvalCode, String approvedAt) {
        return new BookingPaymentApproval(
                BookingPaymentStatus.AUTHORIZED,
                providerLabel,
                approvalCode,
                approvedAt
        );
    }

    public static BookingPaymentApproval deferred(String providerLabel, String approvedAt) {
        return new BookingPaymentApproval(
                BookingPaymentStatus.DEFERRED,
                providerLabel,
                "",
                approvedAt
        );
    }

    public BookingPaymentStatus getStatus() {
        return status;
    }

    public String getProviderLabel() {
        return providerLabel;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public boolean isCompleted() {
        return status == BookingPaymentStatus.AUTHORIZED || status == BookingPaymentStatus.DEFERRED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
