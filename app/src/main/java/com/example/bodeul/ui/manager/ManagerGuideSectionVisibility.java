package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;

/** 현재 가이드 한 단계에서 실제로 사용할 프론트엔드 작업 영역을 정의한다. */
final class ManagerGuideSectionVisibility {
    private final boolean mapVisible;
    private final boolean locationVisible;
    private final boolean guardianVisible;
    private final boolean fieldNoteVisible;
    private final boolean medicationNoteVisible;
    private final boolean pharmacyVisible;
    private final boolean reportSummaryVisible;
    private final boolean reportMedicationVisible;
    private final boolean nextVisitVisible;

    private ManagerGuideSectionVisibility(
            boolean mapVisible,
            boolean locationVisible,
            boolean guardianVisible,
            boolean fieldNoteVisible,
            boolean medicationNoteVisible,
            boolean pharmacyVisible,
            boolean reportSummaryVisible,
            boolean reportMedicationVisible,
            boolean nextVisitVisible
    ) {
        this.mapVisible = mapVisible;
        this.locationVisible = locationVisible;
        this.guardianVisible = guardianVisible;
        this.fieldNoteVisible = fieldNoteVisible;
        this.medicationNoteVisible = medicationNoteVisible;
        this.pharmacyVisible = pharmacyVisible;
        this.reportSummaryVisible = reportSummaryVisible;
        this.reportMedicationVisible = reportMedicationVisible;
        this.nextVisitVisible = nextVisitVisible;
    }

    static ManagerGuideSectionVisibility forStep(@Nullable GuideStep step) {
        if (step == null) {
            return hidden();
        }
        String code = step.getCode() == null ? "" : step.getCode().trim();
        if (code.isEmpty()) {
            return fromLegacyOrder(step.getOrder());
        }
        switch (code) {
            case "MEETING_CONFIRMATION":
                return only();
            case "HOSPITAL_ROUTE":
                return of(true, true, false, false, false, false, false, false, false);
            case "RECEPTION_QUEUE":
            case "VITALS_CHECK":
            case "PRE_CONSULTATION":
            case "PAYMENT_EVIDENCE":
            case "PRESCRIPTION_DOCUMENTS":
                return of(false, false, false, true, false, false, false, false, false);
            case "CONSULTATION_SUPPORT":
                return of(false, false, true, true, false, false, false, false, false);
            case "CONSULTATION_SUMMARY":
                return of(false, false, false, true, false, false, false, false, false);
            case "PHARMACY_ROUTE":
                return of(true, false, false, false, false, false, false, false, false);
            case "MEDICATION_CONFIRMATION":
                return of(false, false, false, false, true, true, false, false, false);
            case "CARE_COMPLETION":
                return of(false, false, true, false, false, false, false, false, false);
            case "MANAGER_JOURNAL":
                return of(false, false, false, false, false, false, true, true, true);
            case "LEGACY_CORE_PATIENT_CONTACT":
                return of(true, true, false, false, false, false, false, false, false);
            case "LEGACY_CORE_RECEPTION_PREPARATION":
            case "LEGACY_CORE_RECEPTION":
            case "LEGACY_CORE_PAYMENT":
                return of(false, false, false, true, false, false, false, false, false);
            case "LEGACY_CORE_CONSULTATION":
                return of(false, false, true, true, false, false, false, false, false);
            case "LEGACY_CORE_PHARMACY":
                return of(true, false, false, false, true, true, false, false, false);
            case "LEGACY_CORE_RETURN_AND_CLOSE":
                return of(false, false, true, false, false, false, true, true, true);
            default:
                // 유효하지만 아직 모르는 서버 단계는 순번으로 의미를 추정하지 않는다.
                return commonFieldNote();
        }
    }

    private static ManagerGuideSectionVisibility fromLegacyOrder(int order) {
        switch (order) {
            case 0:
                return hidden();
            case 1:
                return of(true, true, false, false, false, false, false, false, false);
            case 2:
            case 3:
                return of(false, false, false, true, false, false, false, false, false);
            case 4:
                return of(false, false, true, true, false, false, false, false, false);
            case 5:
                return of(false, false, false, true, false, false, false, false, false);
            case 6:
                return of(true, false, false, false, true, true, false, false, false);
            case 7:
                return of(false, false, true, false, false, false, true, true, true);
            default:
                return commonFieldNote();
        }
    }

    private static ManagerGuideSectionVisibility commonFieldNote() {
        return of(false, false, false, true, false, false, false, false, false);
    }

    private static ManagerGuideSectionVisibility only() {
        return of(false, false, false, false, false, false, false, false, false);
    }

    private static ManagerGuideSectionVisibility of(
            boolean map,
            boolean location,
            boolean guardian,
            boolean fieldNote,
            boolean medicationNote,
            boolean pharmacy,
            boolean reportSummary,
            boolean reportMedication,
            boolean nextVisit
    ) {
        return new ManagerGuideSectionVisibility(
                map, location, guardian, fieldNote, medicationNote, pharmacy,
                reportSummary, reportMedication, nextVisit
        );
    }

    static ManagerGuideSectionVisibility hidden() { return only(); }
    ManagerGuideSectionVisibility withReportSection() {
        return of(
                mapVisible,
                locationVisible,
                guardianVisible,
                fieldNoteVisible,
                medicationNoteVisible,
                pharmacyVisible,
                true,
                true,
                true
        );
    }
    boolean isMapVisible() { return mapVisible; }
    boolean isLocationVisible() { return locationVisible; }
    boolean isGuardianVisible() { return guardianVisible; }
    boolean isFieldNoteVisible() { return fieldNoteVisible; }
    boolean isMedicationNoteVisible() { return medicationNoteVisible; }
    boolean isPharmacyVisible() { return pharmacyVisible; }
    boolean isReportSummaryVisible() { return reportSummaryVisible; }
    boolean isReportMedicationVisible() { return reportMedicationVisible; }
    boolean isNextVisitVisible() { return nextVisitVisible; }
    boolean hasLocationSection() { return locationVisible || guardianVisible; }
    boolean hasNotesSection() { return fieldNoteVisible || medicationNoteVisible || pharmacyVisible; }
    boolean hasReportSection() { return reportSummaryVisible || reportMedicationVisible || nextVisitVisible; }
    boolean hasActionSection() { return hasLocationSection() || hasNotesSection() || hasReportSection(); }
}
