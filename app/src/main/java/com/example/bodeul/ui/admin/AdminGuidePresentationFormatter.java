package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;

/**
 * 관리자 병원 가이드 영역의 문구 규칙을 담당한다.
 */
public final class AdminGuidePresentationFormatter {
    private final Context context;

    public AdminGuidePresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String buildGuideTitle(HospitalGuide guide) {
        return context.getString(
                R.string.admin_guide_title,
                guide.getHospitalName(),
                guide.getDepartmentName()
        );
    }

    public String buildGuideCount(HospitalGuide guide) {
        return context.getString(R.string.admin_guide_count, guide.getSteps().size());
    }

    public String buildGuidePreview(HospitalGuide guide) {
        StringBuilder builder = new StringBuilder();
        int previewCount = Math.min(guide.getSteps().size(), 2);
        for (int index = 0; index < previewCount; index++) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(guide.getSteps().get(index).getTitle());
        }
        return context.getString(R.string.admin_guide_preview, builder.toString());
    }

    public String buildEditableGuideSteps(HospitalGuide guide) {
        StringBuilder builder = new StringBuilder();
        for (GuideStep step : guide.getSteps()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (!TextUtils.isEmpty(step.getDescription())) {
                builder.append(step.getTitle()).append(": ").append(step.getDescription());
            } else {
                builder.append(step.getTitle());
            }
        }
        return builder.toString();
    }

    public AdminGuideFormModel createFormModel(HospitalGuide editingGuide, boolean loading) {
        boolean editing = editingGuide != null;
        boolean canEditTarget = !loading && !editing;
        boolean canEditSteps = !loading;
        if (!editing) {
            return new AdminGuideFormModel(
                    context.getString(R.string.admin_guide_form_title),
                    context.getString(R.string.admin_guide_form_badge),
                    context.getString(R.string.admin_guide_form_helper),
                    context.getString(R.string.admin_guide_submit),
                    false,
                    canEditTarget,
                    canEditSteps
            );
        }

        return new AdminGuideFormModel(
                context.getString(R.string.admin_guide_form_edit_title),
                context.getString(R.string.admin_guide_form_edit_badge),
                context.getString(
                        R.string.admin_guide_form_edit_helper,
                        editingGuide.getHospitalName(),
                        editingGuide.getDepartmentName()
                ),
                context.getString(R.string.admin_guide_submit_update),
                true,
                canEditTarget,
                canEditSteps
        );
    }
}
