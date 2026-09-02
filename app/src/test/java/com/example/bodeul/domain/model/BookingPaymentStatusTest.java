package com.example.bodeul.domain.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BookingPaymentStatusTest {
    @Test
    public void fromValue_parsesServerManagedBankTransferStatuses() {
        assertEquals(BookingPaymentStatus.AWAITING_DEPOSIT, BookingPaymentStatus.fromValue("AWAITING_DEPOSIT"));
        assertEquals(BookingPaymentStatus.DEPOSIT_CONFIRMED, BookingPaymentStatus.fromValue("DEPOSIT_CONFIRMED"));
        assertEquals(BookingPaymentStatus.REVIEW_REQUIRED, BookingPaymentStatus.fromValue("REVIEW_REQUIRED"));
        assertEquals(BookingPaymentStatus.REFUND_REQUESTED, BookingPaymentStatus.fromValue("REFUND_REQUESTED"));
        assertEquals(BookingPaymentStatus.REFUNDED, BookingPaymentStatus.fromValue("REFUNDED"));
        assertEquals(BookingPaymentStatus.CANCELED, BookingPaymentStatus.fromValue("CANCELED"));
    }

    @Test
    public void fromValue_keepsLegacyStatusesCompatible() {
        assertEquals(BookingPaymentStatus.PENDING, BookingPaymentStatus.fromValue("PENDING"));
        assertEquals(BookingPaymentStatus.AUTHORIZED, BookingPaymentStatus.fromValue("AUTHORIZED"));
        assertEquals(BookingPaymentStatus.DEFERRED, BookingPaymentStatus.fromValue("DEFERRED"));
    }

    @Test
    public void fromValue_unknownInputFailsClosed() {
        assertEquals(BookingPaymentStatus.UNKNOWN, BookingPaymentStatus.fromValue(null));
        assertEquals(BookingPaymentStatus.UNKNOWN, BookingPaymentStatus.fromValue(""));
        assertEquals(BookingPaymentStatus.UNKNOWN, BookingPaymentStatus.fromValue("UNSUPPORTED"));
    }
}
