package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Date;

public class BankTransferPaymentCoordinatorTest {
    @Test
    public void parseIsoTimestamp_acceptsUtcOffsetAndLongFraction() {
        Date utc = BankTransferPaymentCoordinator.parseIsoTimestamp(
                "2026-09-03T03:00:00Z");
        Date offset = BankTransferPaymentCoordinator.parseIsoTimestamp(
                "2026-09-03T12:00:00+09:00");
        Date fraction = BankTransferPaymentCoordinator.parseIsoTimestamp(
                "2026-09-03T03:00:00.123456Z");

        assertNotNull(utc);
        assertNotNull(offset);
        assertNotNull(fraction);
        assertEquals(utc.getTime(), offset.getTime());
        assertEquals(123L, fraction.getTime() - utc.getTime());
    }

    @Test
    public void parseIsoTimestamp_rejectsInvalidValues() {
        assertNull(BankTransferPaymentCoordinator.parseIsoTimestamp(""));
        assertNull(BankTransferPaymentCoordinator.parseIsoTimestamp(
                "2026-09-03T03:00:00.Z"));
        assertNull(BankTransferPaymentCoordinator.parseIsoTimestamp("잘못된 시각"));
    }
}
