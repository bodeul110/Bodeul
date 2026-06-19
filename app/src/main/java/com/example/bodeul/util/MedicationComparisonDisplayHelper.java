package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.SessionReport;

import java.util.Locale;

/**
 * OCR 없이도 예약 단계 복약 정보와 현장 리포트를 비교해 확인 포인트를 만든다.
 */
public final class MedicationComparisonDisplayHelper {
    private MedicationComparisonDisplayHelper() {
    }

    @Nullable
    public static MedicationComparisonSummary buildSummary(
            Context context,
            AppointmentRequest request,
            @Nullable SessionReport report
    ) {
        if (report == null) {
            return null;
        }

        String baseline = safeTrim(request.getMedicationSummary());
        String medicationName = safeTrim(report.getMedicationName());
        String changeSummary = safeTrim(report.getMedicationChangeSummary());
        String scheduleNote = safeTrim(report.getMedicationScheduleNote());
        String reportNote = safeTrim(report.getMedicationNotes());

        if (TextUtils.isEmpty(medicationName)
                && TextUtils.isEmpty(changeSummary)
                && TextUtils.isEmpty(scheduleNote)
                && TextUtils.isEmpty(reportNote)) {
            return null;
        }

        if (TextUtils.isEmpty(baseline)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_new_guidance),
                    context.getString(R.string.medication_compare_follow_up_missing_request)
            );
        }

        if (!TextUtils.isEmpty(changeSummary)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_changed),
                    TextUtils.isEmpty(scheduleNote)
                            ? context.getString(R.string.medication_compare_follow_up_change_only)
                            : context.getString(R.string.medication_compare_follow_up_change_and_schedule)
            );
        }

        if (!TextUtils.isEmpty(medicationName)
                && !containsNormalized(baseline, medicationName)
                && !containsNormalized(medicationName, baseline)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_recheck),
                    context.getString(R.string.medication_compare_follow_up_name_mismatch)
            );
        }

        if (!TextUtils.isEmpty(scheduleNote)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_same),
                    context.getString(R.string.medication_compare_follow_up_schedule_only)
            );
        }

        return new MedicationComparisonSummary(
                context.getString(R.string.medication_compare_status_same),
                context.getString(R.string.medication_compare_follow_up_same)
        );
    }

    private static boolean containsNormalized(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        if (TextUtils.isEmpty(normalizedSource) || TextUtils.isEmpty(normalizedTarget)) {
            return false;
        }
        return normalizedSource.contains(normalizedTarget);
    }

    private static String normalize(String value) {
        String lowered = safeTrim(value).toLowerCase(Locale.KOREA);
        return lowered.replaceAll("[\\s,./()\\-]", "");
    }

    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
