package com.example.bodeul.ui.admin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminSettlementActionPolicyTest {
    @Test
    public void bankTransferAndServerManagedStatuses_blockLegacyFirestoreAction() {
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction(
                "BANK_TRANSFER",
                "AWAITING_DEPOSIT"
        ));
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction(
                "BANK_TRANSFER",
                "PENDING"
        ));
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction(
                "CARD",
                "DEPOSIT_CONFIRMED"
        ));
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction(
                "CARD",
                "REFUND_REQUESTED"
        ));
    }

    @Test
    public void unknownOrMissingCodes_failClosed() {
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction(null, "PENDING"));
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction("CARD", null));
        assertFalse(AdminSettlementActionPolicy.canUseLegacyFirestoreAction("UNSUPPORTED", "PENDING"));
    }

    @Test
    public void legacyNonBankStatuses_keepExistingSettlementFollowUpAction() {
        assertTrue(AdminSettlementActionPolicy.canUseLegacyFirestoreAction("CARD", "PENDING"));
        assertTrue(AdminSettlementActionPolicy.canUseLegacyFirestoreAction("EASY_PAY", "AUTHORIZED"));
        assertTrue(AdminSettlementActionPolicy.canUseLegacyFirestoreAction("ON_SITE", "DEFERRED"));
    }
}
