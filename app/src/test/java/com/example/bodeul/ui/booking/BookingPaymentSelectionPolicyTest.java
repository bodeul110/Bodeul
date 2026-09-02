package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.BookingPaymentMethod;

import org.junit.Test;

public class BookingPaymentSelectionPolicyTest {
    @Test
    public void create_keepsCardDefaultAndGatesBankTransferToDebug() {
        assertEquals(BookingPaymentMethod.CARD, BookingPaymentSelectionPolicy.defaultCreateMethod());
        assertFalse(BookingPaymentSelectionPolicy.isBankTransferVisibleForCreate(false));
        assertTrue(BookingPaymentSelectionPolicy.isBankTransferVisibleForCreate(true));
    }

    @Test
    public void edit_showsAndLocksOnlyExistingBankTransferTerms() {
        assertTrue(BookingPaymentSelectionPolicy.isBankTransferVisibleForEdit(
                BookingPaymentMethod.BANK_TRANSFER
        ));
        assertTrue(BookingPaymentSelectionPolicy.arePaymentTermsLockedForEdit(
                BookingPaymentMethod.BANK_TRANSFER
        ));
        assertFalse(BookingPaymentSelectionPolicy.isBankTransferVisibleForEdit(BookingPaymentMethod.CARD));
        assertFalse(BookingPaymentSelectionPolicy.arePaymentTermsLockedForEdit(BookingPaymentMethod.CARD));
        assertFalse(BookingPaymentSelectionPolicy.isBankTransferVisibleForEdit(BookingPaymentMethod.UNKNOWN));
    }
}
