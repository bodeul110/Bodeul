package com.example.bodeul.domain.model;

/**
 * 예약 접수 전 결제 단계의 사용자 확인 결과를 보관한다.
 */
public final class BookingPaymentApproval {
    private final BookingPaymentStatus status;
    private final String providerLabel;
    private final String approvalCode;
    private final String approvedAt;
    private final boolean submissionConfirmed;

    private BookingPaymentApproval(
            BookingPaymentStatus status,
            String providerLabel,
            String approvalCode,
            String approvedAt,
            boolean submissionConfirmed
    ) {
        this.status = status == null ? BookingPaymentStatus.PENDING : status;
        this.providerLabel = normalize(providerLabel);
        this.approvalCode = normalize(approvalCode);
        this.approvedAt = normalize(approvedAt);
        this.submissionConfirmed = submissionConfirmed;
    }

    public static BookingPaymentApproval empty() {
        return new BookingPaymentApproval(BookingPaymentStatus.PENDING, "", "", "", false);
    }

    public static BookingPaymentApproval simulated(String providerLabel) {
        return new BookingPaymentApproval(
                BookingPaymentStatus.PENDING,
                providerLabel,
                "",
                "",
                true
        );
    }

    public static BookingPaymentApproval authorized(String providerLabel, String approvalCode, String approvedAt) {
        return new BookingPaymentApproval(
                BookingPaymentStatus.AUTHORIZED,
                providerLabel,
                approvalCode,
                approvedAt,
                true
        );
    }

    public static BookingPaymentApproval deferred(String providerLabel, String approvedAt) {
        return new BookingPaymentApproval(
                BookingPaymentStatus.DEFERRED,
                providerLabel,
                "",
                approvedAt,
                true
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
        return submissionConfirmed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
