package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerCurrentLocationReaderTest {
    @Test
    public void fallbackMustBeNoOlderThanFiveMinutes() {
        long now = 1_000_000L;

        assertTrue(ManagerCurrentLocationReader.isFreshFallback(now - 300_000L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(now - 300_001L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(0L, now));
    }

    @Test
    public void smallFutureClockDifferenceIsAccepted() {
        long now = 1_000_000L;

        assertTrue(ManagerCurrentLocationReader.isFreshFallback(now + 1_000L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(now + 300_001L, now));
    }
}
