package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.UserRole;

/**
 * 무통장입금 정보는 예약을 소유한 환자 화면에서만 연다.
 */
public final class BankTransferPaymentAccessPolicy {
    private BankTransferPaymentAccessPolicy() {
    }

    public static boolean canOpen(UserRole role, String paymentMethodCode) {
        return role == UserRole.PATIENT
                && BookingPaymentMethod.fromValue(paymentMethodCode)
                == BookingPaymentMethod.BANK_TRANSFER;
    }

    public static String normalizeDepositorName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static boolean isValidDepositorName(String value) {
        String normalized = normalizeDepositorName(value);
        return !normalized.isEmpty() && normalized.length() <= 100;
    }
}
