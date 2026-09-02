package com.example.bodeul.domain.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BankTransferPaymentTest {
    @Test
    public void canEditDepositorName_allowsOnlyWaitingAndReviewStates() {
        assertTrue(create(BookingPaymentStatus.AWAITING_DEPOSIT).canEditDepositorName());
        assertTrue(create(BookingPaymentStatus.REVIEW_REQUIRED).canEditDepositorName());
        assertFalse(create(BookingPaymentStatus.DEPOSIT_CONFIRMED).canEditDepositorName());
        assertFalse(create(BookingPaymentStatus.REFUND_REQUESTED).canEditDepositorName());
        assertFalse(create(BookingPaymentStatus.REFUNDED).canEditDepositorName());
        assertFalse(create(BookingPaymentStatus.CANCELED).canEditDepositorName());
        assertFalse(create(BookingPaymentStatus.UNKNOWN).canEditDepositorName());
    }

    @Test
    public void canEditDepositorName_rejectsOtherPaymentMethod() {
        assertFalse(create(
                BookingPaymentMethod.CARD,
                BookingPaymentStatus.AWAITING_DEPOSIT,
                40_000,
                null,
                1L).canEditDepositorName());
        assertFalse(create(
                BookingPaymentMethod.UNKNOWN,
                BookingPaymentStatus.AWAITING_DEPOSIT,
                40_000,
                null,
                1L).canEditDepositorName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNegativeExpectedAmount() {
        create(
                BookingPaymentMethod.BANK_TRANSFER,
                BookingPaymentStatus.AWAITING_DEPOSIT,
                -1,
                null,
                1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNegativeReceivedAmount() {
        create(
                BookingPaymentMethod.BANK_TRANSFER,
                BookingPaymentStatus.AWAITING_DEPOSIT,
                40_000,
                -1,
                1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNegativePaymentVersion() {
        create(
                BookingPaymentMethod.BANK_TRANSFER,
                BookingPaymentStatus.AWAITING_DEPOSIT,
                40_000,
                null,
                -1L);
    }

    private BankTransferPayment create(BookingPaymentStatus status) {
        return create(
                BookingPaymentMethod.BANK_TRANSFER,
                status,
                40_000,
                null,
                1L);
    }

    private BankTransferPayment create(
            BookingPaymentMethod method,
            BookingPaymentStatus status,
            int expectedAmount,
            Integer receivedAmount,
            long paymentVersion
    ) {
        return new BankTransferPayment(
                "appointment-id",
                method,
                status,
                expectedAmount,
                "",
                "",
                receivedAmount,
                "",
                "",
                "",
                paymentVersion,
                false
        );
    }
}
