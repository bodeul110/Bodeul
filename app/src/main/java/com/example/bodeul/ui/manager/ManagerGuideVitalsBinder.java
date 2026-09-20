package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.textfield.TextInputEditText;

/** Figma Step 04 기초 측정 전용 위계와 현장 메모 직렬화를 담당한다. */
final class ManagerGuideVitalsBinder {
    private final View content;
    private final View toolbar;
    private final View defaultToolbar;
    private final LinearLayout scrollContent;
    private final View mode;
    private final View subtitle;
    private final View meetingOverview;
    private final View legacySummary;
    private final View legacyFocus;
    private final View actionsTitle;
    private final View actionsHelper;
    private final View fieldNoteCard;
    private final TextView helper;
    private final TextInputEditText systolic;
    private final TextInputEditText diastolic;
    private final TextInputEditText heartRate;
    private final TextInputEditText weight;

    private String boundSessionId = "";
    private String boundNote = "";

    ManagerGuideVitalsBinder(View root) {
        content = root.findViewById(R.id.managerGuideVitalsContent);
        toolbar = root.findViewById(R.id.guideVitalsToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        fieldNoteCard = root.findViewById(R.id.cardGuideNotesActions);
        helper = root.findViewById(R.id.textGuideVitalsHelper);
        systolic = root.findViewById(R.id.inputGuideVitalsSystolic);
        diastolic = root.findViewById(R.id.inputGuideVitalsDiastolic);
        heartRate = root.findViewById(R.id.inputGuideVitalsHeartRate);
        weight = root.findViewById(R.id.inputGuideVitalsWeight);
    }

    void bind(ManagerGuideScreenModel model, ManagerDashboard dashboard, boolean mutationInFlight) {
        boolean vitalsStep = "VITALS_CHECK".equals(model.getCurrentStepCode());
        content.setVisibility(vitalsStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(vitalsStep ? View.VISIBLE : View.GONE);
        if (!vitalsStep) {
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        meetingOverview.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        fieldNoteCard.setVisibility(View.GONE);

        String patientName = dashboard != null && dashboard.getPatient() != null
                ? dashboard.getPatient().getName() : "";
        AppointmentRequest request = dashboard == null ? null : dashboard.getAppointmentRequest();
        if (TextUtils.isEmpty(patientName) && request != null) {
            patientName = request.getPatientName();
        }
        helper.setText(TextUtils.isEmpty(patientName)
                ? helper.getContext().getString(R.string.guide_vitals_helper_unknown)
                : helper.getContext().getString(R.string.guide_vitals_helper, patientName));

        String sessionId = dashboard == null || dashboard.getSession() == null
                ? "" : dashboard.getSession().getId();
        String note = model.getFieldPhotoNote() == null ? "" : model.getFieldPhotoNote().trim();
        if (!TextUtils.equals(boundSessionId, sessionId) || !TextUtils.equals(boundNote, note)) {
            bindDraft(ManagerGuideVitalsDraft.parse(note));
            boundSessionId = sessionId;
            boundNote = note;
        }

        setInputsEnabled(model.isInputsEnabled() && !mutationInFlight);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        boundSessionId = "";
        boundNote = "";
    }

    void setInputsEnabled(boolean enabled) {
        systolic.setEnabled(enabled);
        diastolic.setEnabled(enabled);
        heartRate.setEnabled(enabled);
        weight.setEnabled(enabled);
    }

    String buildNote() {
        clearErrors();
        String systolicValue = valueOf(systolic);
        String diastolicValue = valueOf(diastolic);
        String heartRateValue = valueOf(heartRate);
        String weightValue = valueOf(weight);
        if (systolicValue.isEmpty() != diastolicValue.isEmpty()) {
            TextInputEditText target = systolicValue.isEmpty() ? systolic : diastolic;
            target.setError(target.getContext().getString(R.string.guide_vitals_pair_required));
            target.requestFocus();
            return null;
        }
        if (!ManagerGuideVitalsDraft.isPositiveInteger(systolicValue)) {
            return showNumberError(systolic);
        }
        if (!ManagerGuideVitalsDraft.isPositiveInteger(diastolicValue)) {
            return showNumberError(diastolic);
        }
        if (!ManagerGuideVitalsDraft.isPositiveInteger(heartRateValue)) {
            return showNumberError(heartRate);
        }
        if (!ManagerGuideVitalsDraft.isPositiveDecimal(weightValue)) {
            return showNumberError(weight);
        }

        boolean allEmpty = systolicValue.isEmpty() && diastolicValue.isEmpty()
                && heartRateValue.isEmpty() && weightValue.isEmpty();
        if (allEmpty && !boundNote.isEmpty()
                && !ManagerGuideVitalsDraft.parse(boundNote).structured) {
            return boundNote;
        }
        return ManagerGuideVitalsDraft.format(
                systolicValue, diastolicValue, heartRateValue, weightValue);
    }

    private String showNumberError(TextInputEditText input) {
        input.setError(input.getContext().getString(R.string.guide_vitals_number_invalid));
        input.requestFocus();
        return null;
    }

    private void bindDraft(ManagerGuideVitalsDraft draft) {
        setTextIfDifferent(systolic, draft.systolic);
        setTextIfDifferent(diastolic, draft.diastolic);
        setTextIfDifferent(heartRate, draft.heartRate);
        setTextIfDifferent(weight, draft.weight);
    }

    private void setTextIfDifferent(TextInputEditText input, String value) {
        String current = input.getText() == null ? "" : input.getText().toString();
        if (!TextUtils.equals(current, value)) {
            input.setText(value);
        }
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void clearErrors() {
        systolic.setError(null);
        diastolic.setError(null);
        heartRate.setError(null);
        weight.setError(null);
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
