package com.example.bodeul.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.MedicationComparisonDecision;

/**
 * 복약 대조 판단 코드를 사용자 화면용 한국어 문구로 변환한다.
 */
public final class MedicationComparisonDecisionDisplayHelper {
    private MedicationComparisonDecisionDisplayHelper() {
    }

    @Nullable
    public static String toLabel(Context context, @Nullable MedicationComparisonDecision decision) {
        if (decision == null) {
            return null;
        }
        switch (decision) {
            case MATCHED:
                return context.getString(R.string.medication_comparison_decision_matched);
            case CHANGED:
                return context.getString(R.string.medication_comparison_decision_changed);
            case RECHECK_REQUIRED:
                return context.getString(R.string.medication_comparison_decision_recheck_required);
            default:
                return null;
        }
    }
}
