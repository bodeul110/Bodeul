package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuideMemoSummaryDisplayPolicyTest {
    @Test
    public void careCompletionShowsMemoBeforeEndCareRequest() {
        assertTrue(ManagerGuideMemoSummaryDisplayPolicy.shouldShow(
                ManagerGuidePrimaryAction.END_CARE));
    }

    @Test
    public void journalScreenDoesNotRenderMaskedPostCareMemo() {
        assertFalse(ManagerGuideMemoSummaryDisplayPolicy.shouldShow(
                ManagerGuidePrimaryAction.SUBMIT_REPORT));
        assertFalse(ManagerGuideMemoSummaryDisplayPolicy.shouldShow(
                ManagerGuidePrimaryAction.ADVANCE));
        assertFalse(ManagerGuideMemoSummaryDisplayPolicy.shouldShow(null));
    }
}
