package com.example.bodeul.domain.model;

/**
 * 예약 요청에 남기는 결제 진행 상태를 표현한다.
 */
public enum BookingPaymentStatus {
    PENDING,
    AWAITING_DEPOSIT,
    DEPOSIT_CONFIRMED,
    REVIEW_REQUIRED,
    REFUND_REQUESTED,
    REFUNDED,
    CANCELED,
    AUTHORIZED,
    DEFERRED,
    UNKNOWN;

    public static BookingPaymentStatus fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return UNKNOWN;
        }
        try {
            return BookingPaymentStatus.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
