package com.example.bodeul.ui.manager;

import androidx.lifecycle.SavedStateHandle;

import com.example.bodeul.domain.model.MedicationComparisonDecision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class ManagerGuideViewModelSavedStateTest {

    @Test
    public void reportDraftSurvivesViewModelRecreationState() {
        SavedStateHandle state = new SavedStateHandle();
        ManagerGuideViewModel.ReportDraft draft = new ManagerGuideViewModel.ReportDraft(
                "session-1",
                "요약",
                "진료",
                "복약",
                "약 이름",
                "변경",
                "일정",
                MedicationComparisonDecision.RECHECK_REQUIRED,
                "재확인",
                "다음 방문");

        ManagerGuideViewModel.saveReportDraft(state, draft);
        ManagerGuideViewModel.ReportDraft restored =
                ManagerGuideViewModel.restoreReportDraft(state);

        assertNotNull(restored);
        assertEquals("요약", restored.summary);
        assertEquals("진료", restored.treatment);
        assertEquals("복약", restored.medication);
        assertEquals(MedicationComparisonDecision.RECHECK_REQUIRED,
                restored.medicationComparisonDecision);
        assertEquals("다음 방문", restored.nextVisit);
    }

    @Test
    public void artifactRequestIdIsReusedOnlyForTheSameLogicalSelection() {
        SavedStateHandle state = new SavedStateHandle();

        String first = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/1");
        String retry = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/1");
        String replacement = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/2");

        assertEquals(first, retry);
        assertNotEquals(first, replacement);
    }
}
