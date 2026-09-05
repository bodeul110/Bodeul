package com.example.bodeul.domain.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminActionNotificationMutationPolicyTest {
    @Test
    public void legacyEmergencySource_blocksNewStateMutations() {
        assertFalse(AdminActionNotificationMutationPolicy.canMutate(
                AdminActionSourceType.EMERGENCY
        ));
        assertFalse(AdminActionNotificationMutationPolicy.canMutate(null));
    }

    @Test
    public void currentMvpSources_keepExistingActions() {
        assertTrue(AdminActionNotificationMutationPolicy.canMutate(
                AdminActionSourceType.SETTLEMENT
        ));
        assertTrue(AdminActionNotificationMutationPolicy.canMutate(
                AdminActionSourceType.SUPPORT
        ));
    }
}
