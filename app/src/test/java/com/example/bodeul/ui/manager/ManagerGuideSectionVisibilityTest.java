package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.GuideStep;

import org.junit.Test;

public class ManagerGuideSectionVisibilityTest {
    @Test
    public void forStep_limitsActionsToExactCurrentStep() {
        ManagerGuideSectionVisibility pharmacyRoute = ManagerGuideSectionVisibility.forStep(
                new GuideStep("PHARMACY_ROUTE", 9, "약국 이동", ""));
        ManagerGuideSectionVisibility medication = ManagerGuideSectionVisibility.forStep(
                new GuideStep("MEDICATION_CONFIRMATION", 11, "복약 확인", ""));
        ManagerGuideSectionVisibility journal = ManagerGuideSectionVisibility.forStep(
                new GuideStep("MANAGER_JOURNAL", 13, "매니저 일지", ""));

        assertTrue(pharmacyRoute.isMapVisible());
        assertFalse(pharmacyRoute.hasNotesSection());
        assertFalse(pharmacyRoute.hasReportSection());

        assertFalse(medication.isMapVisible());
        assertTrue(medication.isMedicationNoteVisible());
        assertTrue(medication.isPharmacyVisible());
        assertFalse(medication.hasReportSection());

        assertTrue(journal.isReportSummaryVisible());
        assertTrue(journal.isReportMedicationVisible());
        assertTrue(journal.isNextVisitVisible());
    }

    @Test
    public void forStep_doesNotInferUnknownCodeMeaningFromOrder() {
        ManagerGuideSectionVisibility extensionAtFirstOrder = ManagerGuideSectionVisibility.forStep(
                new GuideStep("UNLISTED_EXTENSION", 1, "확장", ""));
        ManagerGuideSectionVisibility extensionAtFinalOrder = ManagerGuideSectionVisibility.forStep(
                new GuideStep("UNLISTED_EXTENSION", 7, "확장", ""));

        assertCommonFieldNoteOnly(extensionAtFirstOrder);
        assertCommonFieldNoteOnly(extensionAtFinalOrder);
    }

    @Test
    public void forStep_usesOrderFallbackWhenCodeIsEmpty() {
        ManagerGuideSectionVisibility legacyFirst = ManagerGuideSectionVisibility.forStep(
                new GuideStep("", 1, "환자 접촉", ""));
        ManagerGuideSectionVisibility legacyFinal = ManagerGuideSectionVisibility.forStep(
                new GuideStep("", 7, "복귀 및 종료", ""));

        assertTrue(legacyFirst.isMapVisible());
        assertTrue(legacyFirst.isLocationVisible());
        assertTrue(legacyFinal.isGuardianVisible());
        assertTrue(legacyFinal.hasReportSection());
    }

    @Test
    public void forStep_hidesActionsWhileGuideIsPreparing() {
        ManagerGuideSectionVisibility preparing = ManagerGuideSectionVisibility.forStep(
                new GuideStep("", 0, "가이드 준비 중", ""));

        assertFalse(preparing.hasActionSection());
        assertFalse(preparing.isMapVisible());
    }

    private void assertCommonFieldNoteOnly(ManagerGuideSectionVisibility visibility) {
        assertTrue(visibility.isFieldNoteVisible());
        assertFalse(visibility.isMapVisible());
        assertFalse(visibility.hasLocationSection());
        assertFalse(visibility.isMedicationNoteVisible());
        assertFalse(visibility.isPharmacyVisible());
        assertFalse(visibility.hasReportSection());
    }
}
