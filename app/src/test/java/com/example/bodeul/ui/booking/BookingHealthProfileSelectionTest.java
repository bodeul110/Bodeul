package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.BookingMobilitySupport;

import org.junit.Test;

public class BookingHealthProfileSelectionTest {
    @Test
    public void constructor_normalizesValuesAndKeepsMobility() {
        BookingHealthProfileSelection selection = new BookingHealthProfileSelection(
                "  고혈압  ",
                "  혈압약  ",
                "  낯가림이 심함  ",
                BookingMobilitySupport.WALKING_AID
        );

        assertEquals("고혈압", selection.getPatientConditionSummary());
        assertEquals("혈압약", selection.getMedicationSummary());
        assertEquals("낯가림이 심함", selection.getSpecialNotes());
        assertEquals(BookingMobilitySupport.WALKING_AID, selection.getMobilitySupport());
        assertTrue(selection.hasRequiredCondition());
    }

    @Test
    public void missingValues_useSafeDefaultsAndRemainIncomplete() {
        BookingHealthProfileSelection selection = new BookingHealthProfileSelection(
                null,
                null,
                null,
                null
        );

        assertEquals("", selection.getPatientConditionSummary());
        assertEquals("", selection.getMedicationSummary());
        assertEquals("", selection.getSpecialNotes());
        assertEquals(BookingMobilitySupport.INDEPENDENT, selection.getMobilitySupport());
        assertFalse(selection.hasRequiredCondition());
    }
}
