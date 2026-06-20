package com.example.bodeul.ui.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.HospitalGuide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 병원 가이드 목록과 편집 폼 상태를 한 곳에서 관리한다.
 */
final class AdminGuideSectionController {
    interface Listener {
        boolean isInteractionBlocked();

        void onSaveGuide(
                String hospitalName,
                String departmentName,
                List<String> stepLines,
                boolean editing
        );

        void onDeleteGuide(HospitalGuide guide);

        void renderEmptyText(LinearLayout container, int titleResId, int messageResId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final LinearLayout guideListContainer;
    private final TextInputLayout hospitalLayout;
    private final TextInputLayout departmentLayout;
    private final TextInputLayout stepsLayout;
    private final TextInputEditText hospitalInput;
    private final TextInputEditText departmentInput;
    private final TextInputEditText stepsInput;
    private final MaterialButton submitButton;
    private final MaterialButton cancelButton;
    private final AdminGuideCoordinator guideCoordinator;
    private final AdminGuideCardBinder guideCardBinder;
    private final AdminGuideFormBinder guideFormBinder;
    private final Listener listener;

    private final List<HospitalGuide> guidesSnapshot = new ArrayList<>();
    @Nullable
    private HospitalGuide editingGuide;
    private boolean loading;

    AdminGuideSectionController(
            Context context,
            LayoutInflater inflater,
            LinearLayout guideListContainer,
            TextInputLayout hospitalLayout,
            TextInputLayout departmentLayout,
            TextInputLayout stepsLayout,
            TextInputEditText hospitalInput,
            TextInputEditText departmentInput,
            TextInputEditText stepsInput,
            MaterialButton submitButton,
            MaterialButton cancelButton,
            AdminGuideCoordinator guideCoordinator,
            AdminGuideCardBinder guideCardBinder,
            AdminGuideFormBinder guideFormBinder,
            Listener listener
    ) {
        this.context = context;
        this.inflater = inflater;
        this.guideListContainer = guideListContainer;
        this.hospitalLayout = hospitalLayout;
        this.departmentLayout = departmentLayout;
        this.stepsLayout = stepsLayout;
        this.hospitalInput = hospitalInput;
        this.departmentInput = departmentInput;
        this.stepsInput = stepsInput;
        this.submitButton = submitButton;
        this.cancelButton = cancelButton;
        this.guideCoordinator = guideCoordinator;
        this.guideCardBinder = guideCardBinder;
        this.guideFormBinder = guideFormBinder;
        this.listener = listener;
    }

    void bindGuides(List<HospitalGuide> guides, boolean loading) {
        this.loading = loading;
        guidesSnapshot.clear();
        guidesSnapshot.addAll(guides);
        renderGuides();
        bindFormMode();
    }

    void bindLoading(boolean loading) {
        this.loading = loading;
        bindFormMode();
    }

    void showEmptyPanel() {
        guidesSnapshot.clear();
        editingGuide = null;
        clearGuideForm();
        clearGuideErrors();
        bindFormMode();
        guideListContainer.removeAllViews();
        listener.renderEmptyText(
                guideListContainer,
                R.string.admin_guide_list_title,
                R.string.admin_guide_empty
        );
    }

    void submitGuide() {
        if (listener.isInteractionBlocked()) {
            return;
        }

        boolean editing = editingGuide != null;
        clearGuideErrors();
        String hospitalName = valueOf(hospitalInput);
        String departmentName = valueOf(departmentInput);
        String rawSteps = valueOf(stepsInput);
        boolean valid = validateRequired(hospitalLayout, hospitalName)
                && validateRequired(departmentLayout, departmentName)
                && validateRequired(stepsLayout, rawSteps);
        if (!valid) {
            return;
        }

        List<String> stepLines = parseStepLines(rawSteps);
        if (stepLines.isEmpty()) {
            stepsLayout.setError(context.getString(R.string.error_required_field));
            return;
        }

        listener.onSaveGuide(hospitalName, departmentName, stepLines, editing);
    }

    void exitEditMode() {
        editingGuide = null;
        clearGuideForm();
        clearGuideErrors();
        bindFormMode();
    }

    void onGuideSaved(boolean editing) {
        if (editing) {
            exitEditMode();
            return;
        }
        clearGuideForm();
        clearGuideErrors();
        bindFormMode();
    }

    void onGuideDeleted(String guideId) {
        if (editingGuide != null && editingGuide.getId().equals(guideId)) {
            exitEditMode();
        }
    }

    private void renderGuides() {
        guideListContainer.removeAllViews();
        List<AdminGuideCardModel> guideCards = guideCoordinator.createGuideCards(guidesSnapshot);
        if (guideCards.isEmpty()) {
            listener.renderEmptyText(
                    guideListContainer,
                    R.string.admin_guide_list_title,
                    R.string.admin_guide_empty
            );
            return;
        }

        for (AdminGuideCardModel guideCard : guideCards) {
            View itemView = inflater.inflate(R.layout.item_admin_guide, guideListContainer, false);
            guideCardBinder.bind(itemView, guideCard, new AdminGuideCardBinder.Listener() {
                @Override
                public void onEditGuide(String guideId) {
                    startGuideEdit(findGuideById(guideId));
                }

                @Override
                public void onDeleteGuide(String guideId) {
                    confirmGuideDelete(findGuideById(guideId));
                }
            });
            guideListContainer.addView(itemView);
        }
    }

    private void startGuideEdit(@Nullable HospitalGuide guide) {
        if (loading || guide == null) {
            return;
        }
        editingGuide = guide;
        hospitalInput.setText(guide.getHospitalName());
        departmentInput.setText(guide.getDepartmentName());
        stepsInput.setText(guideCoordinator.buildEditableGuideSteps(guide));
        clearGuideErrors();
        bindFormMode();
    }

    private void confirmGuideDelete(@Nullable HospitalGuide guide) {
        if (listener.isInteractionBlocked() || guide == null) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.admin_guide_delete_dialog_title)
                .setMessage(context.getString(
                        R.string.admin_guide_delete_dialog_body,
                        guide.getHospitalName(),
                        guide.getDepartmentName()
                ))
                .setNegativeButton(R.string.admin_guide_delete_dialog_keep, null)
                .setPositiveButton(
                        R.string.admin_guide_delete_dialog_confirm,
                        (dialogInterface, which) -> listener.onDeleteGuide(guide)
                )
                .show();
    }

    private void bindFormMode() {
        guideFormBinder.bind(
                guideCoordinator.createGuideFormModel(editingGuide, loading)
        );
    }

    private void clearGuideErrors() {
        hospitalLayout.setError(null);
        departmentLayout.setError(null);
        stepsLayout.setError(null);
    }

    private void clearGuideForm() {
        hospitalInput.setText(null);
        departmentInput.setText(null);
        stepsInput.setText(null);
    }

    @Nullable
    private HospitalGuide findGuideById(String guideId) {
        for (HospitalGuide guide : guidesSnapshot) {
            if (guide.getId().equals(guideId)) {
                return guide;
            }
        }
        return null;
    }

    private List<String> parseStepLines(String rawSteps) {
        List<String> stepLines = new ArrayList<>();
        String[] lines = rawSteps.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                stepLines.add(trimmed);
            }
        }
        return stepLines;
    }

    private boolean validateRequired(TextInputLayout layout, String value) {
        if (value.isEmpty()) {
            layout.setError(context.getString(R.string.error_required_field));
            return false;
        }
        return true;
    }

    private static String valueOf(@Nullable TextInputEditText editText) {
        return editText == null || editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }
}
