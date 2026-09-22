package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuideCurrentLocationPolicyTest {
    @Test
    public void meetingStepShowsCurrentLocationAction() {
        assertTrue(ManagerGuideCurrentLocationPolicy.isAvailableFor("MEETING_CONFIRMATION"));
        assertTrue(ManagerGuideCurrentLocationPolicy.isAvailableFor(" MEETING_CONFIRMATION "));
    }

    @Test
    public void otherStepsHideCurrentLocationAction() {
        assertFalse(ManagerGuideCurrentLocationPolicy.isAvailableFor("ROUTE_GUIDANCE"));
        assertFalse(ManagerGuideCurrentLocationPolicy.isAvailableFor(""));
        assertFalse(ManagerGuideCurrentLocationPolicy.isAvailableFor(null));
    }
}
