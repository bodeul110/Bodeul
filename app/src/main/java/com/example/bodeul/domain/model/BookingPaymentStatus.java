package com.example.bodeul.domain.model;

/**
 * 예약 요청에 남기는 결제 진행 상태를 표현한다.
 */
public enum BookingPaymentStatus {
    PENDING,
    AUTHORIZED,
    DEFERRED;

    public static BookingPaymentStatus fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return PENDING;
        }
        try {
            return BookingPaymentStatus.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return PENDING;
        }
    }
}
