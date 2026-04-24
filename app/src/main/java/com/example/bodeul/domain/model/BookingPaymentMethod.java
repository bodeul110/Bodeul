package com.example.bodeul.domain.model;

/**
 * 예약 단계에서 선택한 결제 수단을 표현한다.
 */
public enum BookingPaymentMethod {
    CARD,
    EASY_PAY,
    ON_SITE;

    public static BookingPaymentMethod fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return CARD;
        }
        try {
            return BookingPaymentMethod.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return CARD;
        }
    }
}
