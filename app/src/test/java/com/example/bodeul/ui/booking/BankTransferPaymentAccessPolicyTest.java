package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

public class BankTransferPaymentAccessPolicyTest {
    @Test
    public void canOpen_allowsOnlyPatientBankTransfer() {
        assertTrue(BankTransferPaymentAccessPolicy.canOpen(
                UserRole.PATIENT,
                "BANK_TRANSFER"));
        assertFalse(BankTransferPaymentAccessPolicy.canOpen(
                UserRole.GUARDIAN,
                "BANK_TRANSFER"));
        assertFalse(BankTransferPaymentAccessPolicy.canOpen(
                UserRole.MANAGER,
                "BANK_TRANSFER"));
        assertFalse(BankTransferPaymentAccessPolicy.canOpen(
                UserRole.PATIENT,
                "CARD"));
        assertFalse(BankTransferPaymentAccessPolicy.canOpen(
                UserRole.PATIENT,
                "SERVER_EXTENSION"));
    }

    @Test
    public void depositorName_normalizesWhitespaceAndEnforcesLength() {
        assertEquals(
                "안진영 보호자",
                BankTransferPaymentAccessPolicy.normalizeDepositorName(
                        "  안진영   보호자  "));
        assertFalse(BankTransferPaymentAccessPolicy.isValidDepositorName("   "));
        assertTrue(BankTransferPaymentAccessPolicy.isValidDepositorName("가".repeat(100)));
        assertFalse(BankTransferPaymentAccessPolicy.isValidDepositorName("가".repeat(101)));
    }
}
