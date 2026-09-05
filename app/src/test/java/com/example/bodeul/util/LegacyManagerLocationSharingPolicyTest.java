package com.example.bodeul.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LegacyManagerLocationSharingPolicyTest {
    @Test
    public void resolve_keepsLegacyManagerLocationDisabledByDefault() {
        assertFalse(LegacyManagerLocationSharingPolicy.resolve(false));
    }

    @Test
    public void resolve_allowsExplicitDevelopmentOptIn() {
        assertTrue(LegacyManagerLocationSharingPolicy.resolve(true));
    }
}
