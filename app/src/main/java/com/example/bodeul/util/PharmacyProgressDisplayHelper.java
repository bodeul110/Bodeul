package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;

/**
 * 약국 진행 세부 단계를 화면별로 같은 문구로 보여주기 위한 helper이다.
 */
public final class PharmacyProgressDisplayHelper {
    private PharmacyProgressDisplayHelper() {
    }

    public static boolean shouldShowDetail(@Nullable CompanionSession session) {
        return session != null
                && (session.hasAnyPharmacyProgress()
                || !TextUtils.isEmpty(session.getPharmacySummary()));
    }

    public static String buildStepSummary(Context context, @Nullable CompanionSession session) {
        if (session == null) {
            return context.getString(R.string.pharmacy_progress_step_summary_pending);
        }
        return context.getString(
                R.string.pharmacy_progress_step_summary_format,
                resolveStageLabel(context, session.isPrescriptionCollected()),
                resolveStageLabel(context, session.isPharmacyCompleted()),
                resolveStageLabel(context, session.isMedicationGuidanceCompleted())
        );
    }

    public static String buildOverallStateLabel(Context context, @Nullable CompanionSession session) {
        if (session == null) {
            return context.getString(R.string.guide_pharmacy_state_pending);
        }
        if (session.isPrescriptionCollected()
                && session.isPharmacyCompleted()
                && session.isMedicationGuidanceCompleted()) {
            return context.getString(R.string.guide_pharmacy_state_completed);
        }
        if (shouldShowDetail(session)) {
            return context.getString(R.string.guide_pharmacy_state_pending);
        }
        return context.getString(R.string.guide_pharmacy_state_pending);
    }

    private static String resolveStageLabel(Context context, boolean completed) {
        return context.getString(completed
                ? R.string.pharmacy_progress_stage_completed
                : R.string.pharmacy_progress_stage_pending);
    }
}
