package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.SessionReport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OCR 없이도 예약 단계 복약 정보와 현장 리포트의 기본 차이를 정리한다.
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
        String comparisonDetail = buildMedicationDifferenceDetail(context, baseline, medicationName);

        if (TextUtils.isEmpty(medicationName)
                && TextUtils.isEmpty(changeSummary)
                && TextUtils.isEmpty(scheduleNote)
                && TextUtils.isEmpty(reportNote)) {
            return null;
        }

        if (TextUtils.isEmpty(baseline)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_new_guidance),
                    comparisonDetail,
                    context.getString(R.string.medication_compare_follow_up_missing_request)
            );
        }

        if (!TextUtils.isEmpty(changeSummary)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_changed),
                    comparisonDetail,
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
                    comparisonDetail,
                    context.getString(R.string.medication_compare_follow_up_name_mismatch)
            );
        }

        if (!TextUtils.isEmpty(scheduleNote)) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_same),
                    comparisonDetail,
                    context.getString(R.string.medication_compare_follow_up_schedule_only)
            );
        }

        return new MedicationComparisonSummary(
                context.getString(R.string.medication_compare_status_same),
                comparisonDetail,
                context.getString(R.string.medication_compare_follow_up_same)
        );
    }

    @Nullable
    private static String buildMedicationDifferenceDetail(
            Context context,
            String baseline,
            String medicationName
    ) {
        List<String> baselineTokens = tokenizeMedicationNames(baseline);
        List<String> reportTokens = tokenizeMedicationNames(medicationName);
        if (baselineTokens.isEmpty() || reportTokens.isEmpty()) {
            return null;
        }

        Set<String> matchedBaseline = new LinkedHashSet<>();
        Set<String> matchedReport = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();

        for (String reportToken : reportTokens) {
            String baselineMatch = findBestMatch(reportToken, baselineTokens, matchedBaseline);
            if (baselineMatch == null) {
                continue;
            }
            matchedBaseline.add(baselineMatch);
            matchedReport.add(reportToken);
            kept.add(reportToken);
        }

        List<String> added = new ArrayList<>();
        for (String reportToken : reportTokens) {
            if (!matchedReport.contains(reportToken)) {
                added.add(reportToken);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String baselineToken : baselineTokens) {
            if (!matchedBaseline.contains(baselineToken)) {
                missing.add(baselineToken);
            }
        }

        List<String> segments = new ArrayList<>();
        if (!kept.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_detail_kept,
                    joinLimited(kept)
            ));
        }
        if (!added.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_detail_added,
                    joinLimited(added)
            ));
        }
        if (!missing.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_detail_missing,
                    joinLimited(missing)
            ));
        }
        if (segments.isEmpty()) {
            return null;
        }
        return TextUtils.join(" / ", segments);
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

    private static List<String> tokenizeMedicationNames(String value) {
        List<String> tokens = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return tokens;
        }
        String[] rawTokens = value.split("[\\n,;/|]+");
        for (String rawToken : rawTokens) {
            String token = safeTrim(rawToken);
            if (TextUtils.isEmpty(token)) {
                continue;
            }
            if (token.length() > 24 && token.contains(" ")) {
                // 긴 자유 서술 메모는 약품명 목록 비교에서 제외한다.
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    @Nullable
    private static String findBestMatch(
            String reportToken,
            List<String> baselineTokens,
            Set<String> matchedBaseline
    ) {
        String normalizedReport = normalize(reportToken);
        if (TextUtils.isEmpty(normalizedReport)) {
            return null;
        }
        for (String baselineToken : baselineTokens) {
            if (matchedBaseline.contains(baselineToken)) {
                continue;
            }
            String normalizedBaseline = normalize(baselineToken);
            if (TextUtils.isEmpty(normalizedBaseline)) {
                continue;
            }
            if (normalizedBaseline.equals(normalizedReport)
                    || normalizedBaseline.contains(normalizedReport)
                    || normalizedReport.contains(normalizedBaseline)) {
                return baselineToken;
            }
        }
        return null;
    }

    private static String joinLimited(List<String> tokens) {
        List<String> displayTokens = new ArrayList<>();
        int limit = Math.min(tokens.size(), 3);
        for (int index = 0; index < limit; index++) {
            displayTokens.add(tokens.get(index));
        }
        if (tokens.size() > limit) {
            displayTokens.add("+" + (tokens.size() - limit));
        }
        return TextUtils.join(", ", displayTokens);
    }

    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
