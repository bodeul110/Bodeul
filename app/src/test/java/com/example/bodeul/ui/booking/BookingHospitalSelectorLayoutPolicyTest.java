package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingHospitalSelectorLayoutPolicyTest {
    @Test
    public void smallHeightHidesRegionsToKeepSearchResultsAvailable() {
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(440, 1f, 1f));
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(600, 1f, 1f));
    }

    @Test
    public void largeFontNeedsMoreSpaceBeforeShowingRegions() {
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(600, 1f, 1.5f));
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(750, 1f, 1.5f));
    }

    @Test
    public void initialUnmeasuredStateDoesNotHideRegions() {
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(0, 1f, 1f));
    }
}
