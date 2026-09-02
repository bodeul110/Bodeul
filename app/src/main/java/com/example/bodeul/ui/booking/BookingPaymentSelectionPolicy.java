package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingPaymentMethod;

/**
 * 예약 생성과 편집 화면에서 무통장입금 선택 노출 및 잠금 범위를 결정한다.
 */
public final class BookingPaymentSelectionPolicy {
    private BookingPaymentSelectionPolicy() {
    }

    public static BookingPaymentMethod defaultCreateMethod() {
        return BookingPaymentMethod.CARD;
    }

    public static boolean isBankTransferVisibleForCreate(boolean debugBuild) {
        return debugBuild;
    }

    public static boolean isBankTransferVisibleForEdit(BookingPaymentMethod paymentMethod) {
        return paymentMethod == BookingPaymentMethod.BANK_TRANSFER;
    }

    public static boolean arePaymentTermsLockedForEdit(BookingPaymentMethod paymentMethod) {
        return paymentMethod == BookingPaymentMethod.BANK_TRANSFER;
    }
}
