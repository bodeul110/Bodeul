package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.SessionReport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 없이도 예약 단계 복약 정보와 현장 리포트의 기본 차이를 정리한다.
 */
public final class MedicationComparisonDisplayHelper {
    private static final List<String> SCHEDULE_KEYWORDS = Arrays.asList(
            "아침", "점심", "저녁", "자기전", "취침전", "기상후",
            "식전", "식후", "매일", "격일", "필요시", "복용", "1일", "하루"
    );

    private static final Pattern DOSE_PATTERN = Pattern.compile(
            "(\\d+\\s*(?:mg|ml|g|mcg|정|알|캡슐|포|봉))|(반\\s*정|한\\s*알|두\\s*알|세\\s*알)",
            Pattern.CASE_INSENSITIVE
    );

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
        String comparisonDetail = buildComparisonDetail(
                context,
                baseline,
                medicationName,
                changeSummary,
                scheduleNote,
                reportNote
        );
        boolean scheduleChanged = hasScheduleDifference(baseline, scheduleNote, reportNote);
        boolean doseChanged = hasDoseDifference(
                baseline,
                medicationName,
                changeSummary,
                scheduleNote,
                reportNote
        );

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
                    context.getString((scheduleChanged || doseChanged)
                            ? R.string.medication_compare_status_changed
                            : R.string.medication_compare_status_same),
                    comparisonDetail,
                    context.getString(scheduleChanged
                            ? R.string.medication_compare_follow_up_schedule_changed
                            : (doseChanged
                            ? R.string.medication_compare_follow_up_dose_changed
                            : R.string.medication_compare_follow_up_schedule_only))
            );
        }

        if (doseChanged) {
            return new MedicationComparisonSummary(
                    context.getString(R.string.medication_compare_status_changed),
                    comparisonDetail,
                    context.getString(R.string.medication_compare_follow_up_dose_changed)
            );
        }

        return new MedicationComparisonSummary(
                context.getString(R.string.medication_compare_status_same),
                comparisonDetail,
                context.getString(R.string.medication_compare_follow_up_same)
        );
    }

    @Nullable
    private static String buildComparisonDetail(
            Context context,
            String baseline,
            String medicationName,
            String changeSummary,
            String scheduleNote,
            String reportNote
    ) {
        List<String> segments = new ArrayList<>();

        String medicationDetail = buildMedicationDifferenceDetail(context, baseline, medicationName);
        if (!TextUtils.isEmpty(medicationDetail)) {
            segments.add(medicationDetail);
        }

        String doseDetail = buildDoseDifferenceDetail(
                context,
                baseline,
                medicationName,
                changeSummary,
                scheduleNote,
                reportNote
        );
        if (!TextUtils.isEmpty(doseDetail)) {
            segments.add(doseDetail);
        }

        String scheduleDetail = buildScheduleDifferenceDetail(context, baseline, scheduleNote, reportNote);
        if (!TextUtils.isEmpty(scheduleDetail)) {
            segments.add(scheduleDetail);
        }

        if (segments.isEmpty()) {
            return null;
        }
        return TextUtils.join(" / ", segments);
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

    @Nullable
    private static String buildScheduleDifferenceDetail(
            Context context,
            String baseline,
            String scheduleNote,
            String reportNote
    ) {
        List<String> baselineScheduleTokens = tokenizeScheduleKeywords(baseline);
        List<String> reportScheduleTokens = tokenizeScheduleKeywords(scheduleNote + " " + reportNote);
        if (baselineScheduleTokens.isEmpty() || reportScheduleTokens.isEmpty()) {
            if (baselineScheduleTokens.isEmpty() && reportScheduleTokens.isEmpty()) {
                return null;
            }
        }

        List<String> kept = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String reportToken : reportScheduleTokens) {
            if (baselineScheduleTokens.contains(reportToken)) {
                if (!kept.contains(reportToken)) {
                    kept.add(reportToken);
                }
            } else if (!added.contains(reportToken)) {
                added.add(reportToken);
            }
        }

        for (String baselineToken : baselineScheduleTokens) {
            if (!reportScheduleTokens.contains(baselineToken) && !missing.contains(baselineToken)) {
                missing.add(baselineToken);
            }
        }

        List<String> segments = new ArrayList<>();
        if (!kept.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_schedule_kept,
                    joinLimited(kept)
            ));
        }
        if (!added.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_schedule_added,
                    joinLimited(added)
            ));
        }
        if (!missing.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_schedule_missing,
                    joinLimited(missing)
            ));
        }
        if (segments.isEmpty()) {
            return null;
        }
        return context.getString(
                R.string.medication_compare_schedule_prefix,
                TextUtils.join(", ", segments)
        );
    }

    @Nullable
    private static String buildDoseDifferenceDetail(
            Context context,
            String baseline,
            String medicationName,
            String changeSummary,
            String scheduleNote,
            String reportNote
    ) {
        List<String> baselineDoseTokens = tokenizeDoseKeywords(baseline);
        List<String> reportDoseTokens = tokenizeDoseKeywords(
                medicationName + " " + changeSummary + " " + scheduleNote + " " + reportNote
        );
        if (baselineDoseTokens.isEmpty() && reportDoseTokens.isEmpty()) {
            return null;
        }

        List<String> kept = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String reportToken : reportDoseTokens) {
            if (baselineDoseTokens.contains(reportToken)) {
                if (!kept.contains(reportToken)) {
                    kept.add(reportToken);
                }
            } else if (!added.contains(reportToken)) {
                added.add(reportToken);
            }
        }

        for (String baselineToken : baselineDoseTokens) {
            if (!reportDoseTokens.contains(baselineToken) && !missing.contains(baselineToken)) {
                missing.add(baselineToken);
            }
        }

        List<String> segments = new ArrayList<>();
        if (!kept.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_dose_kept,
                    joinLimited(kept)
            ));
        }
        if (!added.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_dose_added,
                    joinLimited(added)
            ));
        }
        if (!missing.isEmpty()) {
            segments.add(context.getString(
                    R.string.medication_compare_dose_missing,
                    joinLimited(missing)
            ));
        }
        if (segments.isEmpty()) {
            return null;
        }
        return context.getString(
                R.string.medication_compare_dose_prefix,
                TextUtils.join(", ", segments)
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

    private static List<String> tokenizeScheduleKeywords(String value) {
        List<String> tokens = new ArrayList<>();
        String normalizedValue = normalize(value);
        if (TextUtils.isEmpty(normalizedValue)) {
            return tokens;
        }
        for (String keyword : SCHEDULE_KEYWORDS) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedValue.contains(normalizedKeyword) && !tokens.contains(keyword)) {
                tokens.add(keyword);
            }
        }
        return tokens;
    }

    private static List<String> tokenizeDoseKeywords(String value) {
        List<String> tokens = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return tokens;
        }
        Matcher matcher = DOSE_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = safeTrim(matcher.group());
            if (!TextUtils.isEmpty(token) && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean hasScheduleDifference(String baseline, String scheduleNote, String reportNote) {
        List<String> baselineScheduleTokens = tokenizeScheduleKeywords(baseline);
        List<String> reportScheduleTokens = tokenizeScheduleKeywords(scheduleNote + " " + reportNote);
        if (baselineScheduleTokens.isEmpty() || reportScheduleTokens.isEmpty()) {
            return false;
        }
        for (String token : reportScheduleTokens) {
            if (!baselineScheduleTokens.contains(token)) {
                return true;
            }
        }
        for (String token : baselineScheduleTokens) {
            if (!reportScheduleTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDoseDifference(
            String baseline,
            String medicationName,
            String changeSummary,
            String scheduleNote,
            String reportNote
    ) {
        List<String> baselineDoseTokens = tokenizeDoseKeywords(baseline);
        List<String> reportDoseTokens = tokenizeDoseKeywords(
                medicationName + " " + changeSummary + " " + scheduleNote + " " + reportNote
        );
        if (baselineDoseTokens.isEmpty() || reportDoseTokens.isEmpty()) {
            return false;
        }
        for (String token : reportDoseTokens) {
            if (!baselineDoseTokens.contains(token)) {
                return true;
            }
        }
        for (String token : baselineDoseTokens) {
            if (!reportDoseTokens.contains(token)) {
                return true;
            }
        }
        return false;
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
