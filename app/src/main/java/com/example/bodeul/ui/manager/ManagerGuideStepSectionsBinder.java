package com.example.bodeul.ui.manager;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.google.android.material.textfield.TextInputLayout;

/** 현재 stepCode에 필요한 작업 카드만 남기고 단계별 안내 문구를 적용한다. */
final class ManagerGuideStepSectionsBinder {
    private final Context context;
    private final View cardMap;
    private final TextView actionsTitle;
    private final TextView actionsHelper;
    private final View cardLocation;
    private final View groupLocation;
    private final View groupGuardian;
    private final View cardNotes;
    private final View groupFieldNote;
    private final View groupPreConsultationConfirmation;
    private final View groupMedicationNote;
    private final View groupPharmacy;
    private final TextView fieldNoteTitle;
    private final TextInputLayout fieldNoteInput;
    private final View cardReport;
    private final View groupReportSummary;
    private final View groupReportMedication;
    private final View groupReportNextVisit;

    ManagerGuideStepSectionsBinder(Context context, View root) {
        this.context = context;
        cardMap = root.findViewById(R.id.cardGuideMap);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        cardLocation = root.findViewById(R.id.cardGuideLocationActions);
        groupLocation = root.findViewById(R.id.groupGuideLocation);
        groupGuardian = root.findViewById(R.id.groupGuideGuardian);
        cardNotes = root.findViewById(R.id.cardGuideNotesActions);
        groupFieldNote = root.findViewById(R.id.groupGuideFieldNote);
        groupPreConsultationConfirmation = root.findViewById(
                R.id.groupGuidePreConsultationConfirmation);
        groupMedicationNote = root.findViewById(R.id.groupGuideMedicationNote);
        groupPharmacy = root.findViewById(R.id.groupGuidePharmacy);
        fieldNoteTitle = root.findViewById(R.id.textGuideFieldNoteTitle);
        fieldNoteInput = root.findViewById(R.id.layoutGuideFieldNoteInput);
        cardReport = root.findViewById(R.id.cardGuideReportActions);
        groupReportSummary = root.findViewById(R.id.groupGuideReportSummary);
        groupReportMedication = root.findViewById(R.id.groupGuideReportMedication);
        groupReportNextVisit = root.findViewById(R.id.groupGuideReportNextVisit);
    }

    void bind(ManagerGuideSectionVisibility visibility, String stepCode) {
        setVisible(cardMap, visibility.isMapVisible());
        setVisible(cardLocation, visibility.hasLocationSection());
        setVisible(groupLocation, visibility.isLocationVisible());
        setVisible(groupGuardian, visibility.isGuardianVisible());
        setVisible(cardNotes, visibility.hasNotesSection());
        setVisible(groupFieldNote, visibility.isFieldNoteVisible());
        setVisible(
                groupPreConsultationConfirmation,
                "PRE_CONSULTATION".equals(stepCode == null ? "" : stepCode.trim()));
        setVisible(groupMedicationNote, visibility.isMedicationNoteVisible());
        setVisible(groupPharmacy, visibility.isPharmacyVisible());
        setVisible(cardReport, visibility.hasReportSection());
        setVisible(groupReportSummary, visibility.isReportSummaryVisible());
        setVisible(groupReportMedication, visibility.isReportMedicationVisible());
        setVisible(groupReportNextVisit, visibility.isNextVisitVisible());

        int actionVisibility = visibility.hasActionSection() ? View.VISIBLE : View.GONE;
        actionsTitle.setVisibility(actionVisibility);
        actionsHelper.setVisibility(actionVisibility);
        actionsTitle.setText(R.string.guide_current_actions_title);
        actionsHelper.setText(R.string.guide_current_actions_helper);
        bindFieldNoteCopy(stepCode == null ? "" : stepCode.trim());
    }

    private void bindFieldNoteCopy(String code) {
        switch (code) {
            case "RECEPTION_QUEUE":
                setFieldNoteCopy(R.string.guide_field_reception_title, R.string.guide_field_reception_hint);
                break;
            case "VITALS_CHECK":
                setFieldNoteCopy(R.string.guide_field_vitals_title, R.string.guide_field_vitals_hint);
                break;
            case "PRE_CONSULTATION":
                setFieldNoteCopy(R.string.guide_field_pre_consultation_title, R.string.guide_field_pre_consultation_hint);
                break;
            case "CONSULTATION_SUPPORT":
                setFieldNoteCopy(R.string.guide_field_consultation_title, R.string.guide_field_consultation_hint);
                break;
            case "CONSULTATION_SUMMARY":
                setFieldNoteCopy(R.string.guide_report_section, R.string.guide_report_summary_hint);
                break;
            case "PAYMENT_EVIDENCE":
                setFieldNoteCopy(R.string.guide_field_payment_title, R.string.guide_field_payment_hint);
                break;
            case "PRESCRIPTION_DOCUMENTS":
                setFieldNoteCopy(R.string.guide_field_prescription_title, R.string.guide_field_prescription_hint);
                break;
            default:
                setFieldNoteCopy(R.string.guide_photo_section, R.string.guide_photo_hint);
                break;
        }
    }

    private void setFieldNoteCopy(int titleResId, int hintResId) {
        fieldNoteTitle.setText(titleResId);
        fieldNoteInput.setHint(context.getString(hintResId));
    }

    private void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
