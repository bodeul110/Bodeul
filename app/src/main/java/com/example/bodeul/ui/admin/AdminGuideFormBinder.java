package com.example.bodeul.ui.admin;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 관리자 병원 가이드 폼 상태를 뷰에 바인딩한다.
 */
public final class AdminGuideFormBinder {
    private final TextView titleView;
    private final TextView badgeView;
    private final TextView helperView;
    private final TextInputLayout hospitalLayout;
    private final TextInputLayout departmentLayout;
    private final TextInputLayout stepsLayout;
    private final TextInputEditText hospitalInput;
    private final TextInputEditText departmentInput;
    private final TextInputEditText stepsInput;
    private final MaterialButton submitButton;
    private final MaterialButton cancelButton;

    public AdminGuideFormBinder(
            TextView titleView,
            TextView badgeView,
            TextView helperView,
            TextInputLayout hospitalLayout,
            TextInputLayout departmentLayout,
            TextInputLayout stepsLayout,
            TextInputEditText hospitalInput,
            TextInputEditText departmentInput,
            TextInputEditText stepsInput,
            MaterialButton submitButton,
            MaterialButton cancelButton
    ) {
        this.titleView = titleView;
        this.badgeView = badgeView;
        this.helperView = helperView;
        this.hospitalLayout = hospitalLayout;
        this.departmentLayout = departmentLayout;
        this.stepsLayout = stepsLayout;
        this.hospitalInput = hospitalInput;
        this.departmentInput = departmentInput;
        this.stepsInput = stepsInput;
        this.submitButton = submitButton;
        this.cancelButton = cancelButton;
    }

    public void bind(AdminGuideFormModel model) {
        titleView.setText(model.getTitleText());
        badgeView.setText(model.getBadgeText());
        helperView.setText(model.getHelperText());
        submitButton.setText(model.getSubmitButtonText());
        cancelButton.setVisibility(model.isCancelVisible() ? View.VISIBLE : View.GONE);

        hospitalLayout.setEnabled(model.canEditTarget());
        departmentLayout.setEnabled(model.canEditTarget());
        stepsLayout.setEnabled(model.canEditSteps());
        hospitalInput.setEnabled(model.canEditTarget());
        departmentInput.setEnabled(model.canEditTarget());
        stepsInput.setEnabled(model.canEditSteps());
        submitButton.setEnabled(model.canEditSteps());
        cancelButton.setEnabled(model.canEditSteps());
    }
}
