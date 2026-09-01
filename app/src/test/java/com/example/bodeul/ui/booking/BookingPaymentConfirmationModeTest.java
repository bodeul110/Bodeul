package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.BookingPaymentMethod;

import org.junit.Test;

public class BookingPaymentConfirmationModeTest {
    @Test
    public void fromPaymentMethod_mapsSimulationMethods() {
        assertEquals(
                BookingPaymentConfirmationMode.SIMULATION,
                BookingPaymentConfirmationMode.fromPaymentMethod(BookingPaymentMethod.CARD)
        );
        assertEquals(
                BookingPaymentConfirmationMode.SIMULATION,
                BookingPaymentConfirmationMode.fromPaymentMethod(BookingPaymentMethod.EASY_PAY)
        );
    }

    @Test
    public void fromPaymentMethod_mapsBankTransferToSyntheticConfirmation() {
        BookingPaymentConfirmationMode mode = BookingPaymentConfirmationMode.fromPaymentMethod(
                BookingPaymentMethod.BANK_TRANSFER
        );

        assertEquals(BookingPaymentConfirmationMode.BANK_TRANSFER_SYNTHETIC, mode);
        assertTrue(mode.isConfirmable());
    }

    @Test
    public void fromPaymentMethod_mapsOnSiteToDeferredConfirmation() {
        assertEquals(
                BookingPaymentConfirmationMode.DEFERRED,
                BookingPaymentConfirmationMode.fromPaymentMethod(BookingPaymentMethod.ON_SITE)
        );
    }

    @Test
    public void fromPaymentMethod_blocksUnknownAndNull() {
        assertEquals(
                BookingPaymentConfirmationMode.BLOCKED,
                BookingPaymentConfirmationMode.fromPaymentMethod(BookingPaymentMethod.UNKNOWN)
        );
        assertEquals(
                BookingPaymentConfirmationMode.BLOCKED,
                BookingPaymentConfirmationMode.fromPaymentMethod(null)
        );
        assertFalse(BookingPaymentConfirmationMode.BLOCKED.isConfirmable());
    }
}
