package com.example.bodeul.ui.admin;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;

/**
 * PostgreSQL 결제 상태를 Firestore 정산 기록으로 잘못 변경하지 않도록 기존 동작을 제한한다.
 */
public final class AdminSettlementActionPolicy {
    private AdminSettlementActionPolicy() {
    }

    public static boolean canUseLegacyFirestoreAction(@Nullable AppointmentRequest request) {
        if (request == null) {
            return false;
        }
        return canUseLegacyFirestoreAction(
                request.getPaymentMethodCode(),
                request.getPaymentStatusCode()
        );
    }

    static boolean canUseLegacyFirestoreAction(
            @Nullable String paymentMethodCode,
            @Nullable String paymentStatusCode
    ) {
        BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(paymentMethodCode);
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(paymentStatusCode);
        if (paymentMethod == BookingPaymentMethod.UNKNOWN || paymentStatus == BookingPaymentStatus.UNKNOWN) {
            return false;
        }
        if (paymentMethod == BookingPaymentMethod.BANK_TRANSFER) {
            return false;
        }
        switch (paymentStatus) {
            case AWAITING_DEPOSIT:
            case DEPOSIT_CONFIRMED:
            case REVIEW_REQUIRED:
            case REFUND_REQUESTED:
            case REFUNDED:
            case CANCELED:
                return false;
            default:
                return true;
        }
    }
}
