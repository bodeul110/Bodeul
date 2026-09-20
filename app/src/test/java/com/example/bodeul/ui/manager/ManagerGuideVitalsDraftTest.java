package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuideVitalsDraftTest {

    @Test
    public void formatAndParse_preserveValuesAndUnits() {
        String note = ManagerGuideVitalsDraft.format("120", "80", "72", "68.50");

        assertEquals(
                "[기초 측정]\n혈압: 120/80 mmHg\n심박수: 72 bpm\n체중: 68.5 kg",
                note);
        ManagerGuideVitalsDraft parsed = ManagerGuideVitalsDraft.parse(note);
        assertTrue(parsed.structured);
        assertEquals("120", parsed.systolic);
        assertEquals("80", parsed.diastolic);
        assertEquals("72", parsed.heartRate);
        assertEquals("68.5", parsed.weight);
    }

    @Test
    public void parse_unstructuredLegacyNoteDoesNotPretendToBeVitals() {
        ManagerGuideVitalsDraft parsed = ManagerGuideVitalsDraft.parse("환자가 어지러움을 호소함");

        assertFalse(parsed.structured);
        assertEquals("", parsed.systolic);
        assertEquals("", parsed.diastolic);
    }

    @Test
    public void validation_acceptsEmptyOptionalValuesAndRejectsNonPositiveValues() {
        assertEquals("", ManagerGuideVitalsDraft.format("", "", "", ""));
        assertTrue(ManagerGuideVitalsDraft.isPositiveInteger(""));
        assertTrue(ManagerGuideVitalsDraft.isPositiveInteger("72"));
        assertFalse(ManagerGuideVitalsDraft.isPositiveInteger("0"));
        assertFalse(ManagerGuideVitalsDraft.isPositiveInteger("7.2"));
        assertTrue(ManagerGuideVitalsDraft.isPositiveDecimal("68.5"));
        assertFalse(ManagerGuideVitalsDraft.isPositiveDecimal("-1"));
    }
}
