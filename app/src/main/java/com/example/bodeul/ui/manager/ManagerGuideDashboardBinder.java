package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * 동행 가이드 화면 모델을 실제 뷰에 렌더링한다.
 */
public final class ManagerGuideDashboardBinder {
    private final LayoutInflater inflater;
    private final ManagerGuideStageItemBinder stageItemBinder;
    private final TextView textGuideMode;
    private final TextView textGuideTitle;
    private final TextView textGuideSubtitle;
    private final TextView textGuideHeroBadge;
    private final TextView textGuideHeroTitle;
    private final TextView textGuideHeroBody;
    private final TextView textGuideHeroNote;
    private final LinearLayout guideStageRailContainer;
    private final TextView textGuideFocusBadge;
    private final TextView textGuideFocusTitle;
    private final TextView textGuideFocusBody;
    private final TextView textGuideFocusPreviewLabel;
    private final TextView textGuideFocusPreviewBody;
    private final View viewGuideFocusPreview;
    private final TextInputEditText inputGuideLocationSummary;
    private final TextInputEditText inputGuardianUpdate;
    private final TextInputEditText inputGuidePhotoNote;
    private final TextInputEditText inputMedicationNote;
    private final TextInputEditText inputReportSummary;
    private final TextInputEditText inputReportTreatment;
    private final TextInputEditText inputNextVisit;
    private final MaterialButton buttonAdvanceGuide;
    private final MaterialButton buttonSaveLocationSummary;
    private final MaterialButton buttonSaveGuardianUpdate;
    private final MaterialButton buttonSaveGuidePhotoNote;
    private final MaterialButton buttonSaveMedicationNote;
    private final MaterialButton buttonSubmitReport;

    public ManagerGuideDashboardBinder(
            LayoutInflater inflater,
            ManagerGuideStageItemBinder stageItemBinder,
            TextView textGuideMode,
            TextView textGuideTitle,
            TextView textGuideSubtitle,
            TextView textGuideHeroBadge,
            TextView textGuideHeroTitle,
            TextView textGuideHeroBody,
            TextView textGuideHeroNote,
            LinearLayout guideStageRailContainer,
            TextView textGuideFocusBadge,
            TextView textGuideFocusTitle,
            TextView textGuideFocusBody,
            TextView textGuideFocusPreviewLabel,
            TextView textGuideFocusPreviewBody,
            View viewGuideFocusPreview,
            TextInputEditText inputGuideLocationSummary,
            TextInputEditText inputGuardianUpdate,
            TextInputEditText inputGuidePhotoNote,
            TextInputEditText inputMedicationNote,
            TextInputEditText inputReportSummary,
            TextInputEditText inputReportTreatment,
            TextInputEditText inputNextVisit,
            MaterialButton buttonAdvanceGuide,
            MaterialButton buttonSaveLocationSummary,
            MaterialButton buttonSaveGuardianUpdate,
            MaterialButton buttonSaveGuidePhotoNote,
            MaterialButton buttonSaveMedicationNote,
            MaterialButton buttonSubmitReport
    ) {
        this.inflater = inflater;
        this.stageItemBinder = stageItemBinder;
        this.textGuideMode = textGuideMode;
        this.textGuideTitle = textGuideTitle;
        this.textGuideSubtitle = textGuideSubtitle;
        this.textGuideHeroBadge = textGuideHeroBadge;
        this.textGuideHeroTitle = textGuideHeroTitle;
        this.textGuideHeroBody = textGuideHeroBody;
        this.textGuideHeroNote = textGuideHeroNote;
        this.guideStageRailContainer = guideStageRailContainer;
        this.textGuideFocusBadge = textGuideFocusBadge;
        this.textGuideFocusTitle = textGuideFocusTitle;
        this.textGuideFocusBody = textGuideFocusBody;
        this.textGuideFocusPreviewLabel = textGuideFocusPreviewLabel;
        this.textGuideFocusPreviewBody = textGuideFocusPreviewBody;
        this.viewGuideFocusPreview = viewGuideFocusPreview;
        this.inputGuideLocationSummary = inputGuideLocationSummary;
        this.inputGuardianUpdate = inputGuardianUpdate;
        this.inputGuidePhotoNote = inputGuidePhotoNote;
        this.inputMedicationNote = inputMedicationNote;
        this.inputReportSummary = inputReportSummary;
        this.inputReportTreatment = inputReportTreatment;
        this.inputNextVisit = inputNextVisit;
        this.buttonAdvanceGuide = buttonAdvanceGuide;
        this.buttonSaveLocationSummary = buttonSaveLocationSummary;
        this.buttonSaveGuardianUpdate = buttonSaveGuardianUpdate;
        this.buttonSaveGuidePhotoNote = buttonSaveGuidePhotoNote;
        this.buttonSaveMedicationNote = buttonSaveMedicationNote;
        this.buttonSubmitReport = buttonSubmitReport;
    }

    public void bindScreen(ManagerGuideScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textGuideMode, screenModel.getModeLabel());
        textGuideTitle.setText(screenModel.getTitle());
        textGuideSubtitle.setText(screenModel.getSubtitle());
        textGuideHeroBadge.setText(screenModel.getHeroBadge());
        textGuideHeroTitle.setText(screenModel.getHeroTitle());
        textGuideHeroBody.setText(screenModel.getHeroBody());
        textGuideHeroNote.setText(screenModel.getHeroNote());

        bindStages(screenModel.getStages());
        bindFocus(screenModel.getFocusModel());

        setTextIfDifferent(inputGuideLocationSummary, screenModel.getLocationSummary());
        setTextIfDifferent(inputGuardianUpdate, screenModel.getGuardianUpdate());
        setTextIfDifferent(inputGuidePhotoNote, screenModel.getFieldPhotoNote());
        setTextIfDifferent(inputMedicationNote, screenModel.getMedicationNote());
        setTextIfDifferent(inputReportSummary, screenModel.getReportSummary());
        setTextIfDifferent(inputReportTreatment, screenModel.getReportTreatment());
        setTextIfDifferent(inputNextVisit, screenModel.getNextVisitAt());

        boolean inputsEnabled = screenModel.isInputsEnabled();
        inputGuideLocationSummary.setEnabled(inputsEnabled);
        inputGuardianUpdate.setEnabled(inputsEnabled);
        inputGuidePhotoNote.setEnabled(inputsEnabled);
        inputMedicationNote.setEnabled(inputsEnabled);
        inputReportSummary.setEnabled(inputsEnabled);
        inputReportTreatment.setEnabled(inputsEnabled);
        inputNextVisit.setEnabled(inputsEnabled);
        buttonSaveLocationSummary.setEnabled(inputsEnabled);
        buttonSaveGuardianUpdate.setEnabled(inputsEnabled);
        buttonSaveGuidePhotoNote.setEnabled(inputsEnabled);
        buttonSaveMedicationNote.setEnabled(inputsEnabled);
        buttonSubmitReport.setEnabled(inputsEnabled);

        buttonAdvanceGuide.setText(screenModel.getAdvanceButtonLabel());
        buttonAdvanceGuide.setEnabled(screenModel.isAdvanceEnabled());
        buttonSubmitReport.setText(screenModel.getReportButtonLabel());
    }

    private void bindStages(List<ManagerGuideStageModel> stages) {
        guideStageRailContainer.removeAllViews();
        for (ManagerGuideStageModel stage : stages) {
            View stageView = inflater.inflate(R.layout.item_manager_guide_stage, guideStageRailContainer, false);
            stageItemBinder.bind(stageView, stage);
            guideStageRailContainer.addView(stageView);
        }
    }

    private void bindFocus(ManagerGuideFocusModel focusModel) {
        textGuideFocusBadge.setText(focusModel.getBadge());
        textGuideFocusTitle.setText(focusModel.getTitle());
        textGuideFocusBody.setText(focusModel.getBody());
        textGuideFocusPreviewLabel.setText(focusModel.getPreviewLabel());
        textGuideFocusPreviewBody.setText(focusModel.getPreviewBody());
        viewGuideFocusPreview.setBackgroundResource(focusModel.getPreviewBackgroundResId());
    }

    private void setTextIfDifferent(TextInputEditText editText, String value) {
        String safeValue = value == null ? "" : value;
        String currentValue = editText.getText() == null ? "" : editText.getText().toString();
        if (!TextUtils.equals(currentValue, safeValue)) {
            editText.setText(safeValue);
        }
    }
}
