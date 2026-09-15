package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingHospitalSelectorLayoutPolicyTest {
    @Test
    public void smallHeightHidesRegionsToKeepSearchResultsAvailable() {
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(440, 1f, 1f, false));
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(600, 1f, 1f, false));
    }

    @Test
    public void largeFontNeedsMoreSpaceBeforeShowingRegions() {
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(600, 1f, 1.5f, false));
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(900, 1f, 1.5f, false));
    }

    @Test
    public void initialUnmeasuredStateDoesNotHideRegions() {
        assertTrue(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(0, 1f, 1f, false));
    }

    @Test
    public void keyboardAlwaysHidesRegionsEvenBeforeLayoutMeasurement() {
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(900, 1f, 1f, true));
        assertFalse(BookingHospitalSelectorLayoutPolicy.showRegionShortcuts(0, 1f, 1f, true));
    }
}
