package com.example.bodeul.ui.manager;

import java.util.List;

/**
 * 매니저 동행 가이드 화면 전체를 한 번에 렌더링하기 위한 모델이다.
 */
public final class ManagerGuideScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String heroNote;
    private final List<ManagerGuideStageModel> stages;
    private final ManagerGuideFocusModel focusModel;
    private final String locationSummary;
    private final String guardianUpdate;
    private final String fieldPhotoNote;
    private final String medicationNote;
    private final String reportSummary;
    private final String reportTreatment;
    private final String nextVisitAt;
    private final String advanceButtonLabel;
    private final boolean advanceEnabled;
    private final String reportButtonLabel;
    private final boolean inputsEnabled;

    public ManagerGuideScreenModel(
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String heroNote,
            List<ManagerGuideStageModel> stages,
            ManagerGuideFocusModel focusModel,
            String locationSummary,
            String guardianUpdate,
            String fieldPhotoNote,
            String medicationNote,
            String reportSummary,
            String reportTreatment,
            String nextVisitAt,
            String advanceButtonLabel,
            boolean advanceEnabled,
            String reportButtonLabel,
            boolean inputsEnabled
    ) {
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.heroNote = heroNote;
        this.stages = stages;
        this.focusModel = focusModel;
        this.locationSummary = locationSummary;
        this.guardianUpdate = guardianUpdate;
        this.fieldPhotoNote = fieldPhotoNote;
        this.medicationNote = medicationNote;
        this.reportSummary = reportSummary;
        this.reportTreatment = reportTreatment;
        this.nextVisitAt = nextVisitAt;
        this.advanceButtonLabel = advanceButtonLabel;
        this.advanceEnabled = advanceEnabled;
        this.reportButtonLabel = reportButtonLabel;
        this.inputsEnabled = inputsEnabled;
    }

    public String getModeLabel() {
        return modeLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroBadge() {
        return heroBadge;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public String getHeroBody() {
        return heroBody;
    }

    public String getHeroNote() {
        return heroNote;
    }

    public List<ManagerGuideStageModel> getStages() {
        return stages;
    }

    public ManagerGuideFocusModel getFocusModel() {
        return focusModel;
    }

    public String getLocationSummary() {
        return locationSummary;
    }

    public String getGuardianUpdate() {
        return guardianUpdate;
    }

    public String getFieldPhotoNote() {
        return fieldPhotoNote;
    }

    public String getMedicationNote() {
        return medicationNote;
    }

    public String getReportSummary() {
        return reportSummary;
    }

    public String getReportTreatment() {
        return reportTreatment;
    }

    public String getNextVisitAt() {
        return nextVisitAt;
    }

    public String getAdvanceButtonLabel() {
        return advanceButtonLabel;
    }

    public boolean isAdvanceEnabled() {
        return advanceEnabled;
    }

    public String getReportButtonLabel() {
        return reportButtonLabel;
    }

    public boolean isInputsEnabled() {
        return inputsEnabled;
    }
}
