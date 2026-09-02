package com.example.bodeul.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingPaymentMethodTest {
    @Test
    public void fromValue_parsesSupportedMethods() {
        assertEquals(BookingPaymentMethod.BANK_TRANSFER, BookingPaymentMethod.fromValue("BANK_TRANSFER"));
        assertEquals(BookingPaymentMethod.CARD, BookingPaymentMethod.fromValue("CARD"));
        assertEquals(BookingPaymentMethod.EASY_PAY, BookingPaymentMethod.fromValue("EASY_PAY"));
        assertEquals(BookingPaymentMethod.ON_SITE, BookingPaymentMethod.fromValue("ON_SITE"));
    }

    @Test
    public void fromValue_unknownInputFailsClosed() {
        assertEquals(BookingPaymentMethod.UNKNOWN, BookingPaymentMethod.fromValue(null));
        assertEquals(BookingPaymentMethod.UNKNOWN, BookingPaymentMethod.fromValue("  "));
        assertEquals(BookingPaymentMethod.UNKNOWN, BookingPaymentMethod.fromValue("UNSUPPORTED"));
    }

    @Test
    public void selectableForRequest_blocksOnlyUnknown() {
        assertTrue(BookingPaymentMethod.BANK_TRANSFER.isSelectableForRequest());
        assertTrue(BookingPaymentMethod.CARD.isSelectableForRequest());
        assertFalse(BookingPaymentMethod.UNKNOWN.isSelectableForRequest());
    }
}
