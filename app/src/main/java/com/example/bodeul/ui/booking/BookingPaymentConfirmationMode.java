package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingPaymentMethod;

/**
 * 예약 제출 전에 보여줄 결제 확인 방식과 제출 허용 여부를 결정한다.
 */
public enum BookingPaymentConfirmationMode {
    SIMULATION,
    BANK_TRANSFER_SYNTHETIC,
    DEFERRED,
    BLOCKED;

    public static BookingPaymentConfirmationMode fromPaymentMethod(BookingPaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return BLOCKED;
        }
        switch (paymentMethod) {
            case CARD:
            case EASY_PAY:
                return SIMULATION;
            case BANK_TRANSFER:
                return BANK_TRANSFER_SYNTHETIC;
            case ON_SITE:
                return DEFERRED;
            case UNKNOWN:
            default:
                return BLOCKED;
        }
    }

    public boolean isConfirmable() {
        return this != BLOCKED;
    }
}
