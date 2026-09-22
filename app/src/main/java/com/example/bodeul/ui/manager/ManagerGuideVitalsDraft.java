package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** 기초 측정값을 기존 현장 메모 계약에 단위가 포함된 텍스트로 직렬화한다. */
final class ManagerGuideVitalsDraft {
    static final String HEADER = "[기초 측정]";
    private static final String BLOOD_PRESSURE_PREFIX = "혈압: ";
    private static final String HEART_RATE_PREFIX = "심박수: ";
    private static final String WEIGHT_PREFIX = "체중: ";

    final String systolic;
    final String diastolic;
    final String heartRate;
    final String weight;
    final boolean structured;

    private ManagerGuideVitalsDraft(
            String systolic,
            String diastolic,
            String heartRate,
            String weight,
            boolean structured
    ) {
        this.systolic = systolic;
        this.diastolic = diastolic;
        this.heartRate = heartRate;
        this.weight = weight;
        this.structured = structured;
    }

    static ManagerGuideVitalsDraft empty() {
        return new ManagerGuideVitalsDraft("", "", "", "", false);
    }

    /** 입력 중인 값을 검증·정규화하지 않고 보존한다. 혈압 한쪽만 입력된 초안도 유지해야 한다. */
    static ManagerGuideVitalsDraft fromInputs(
            @Nullable String systolic,
            @Nullable String diastolic,
            @Nullable String heartRate,
            @Nullable String weight
    ) {
        return new ManagerGuideVitalsDraft(
                raw(systolic), raw(diastolic), raw(heartRate), raw(weight), true);
    }

    static ManagerGuideVitalsDraft parse(@Nullable String rawNote) {
        String note = normalize(rawNote);
        if (!note.startsWith(HEADER)) {
            return empty();
        }
        String systolic = "";
        String diastolic = "";
        String heartRate = "";
        String weight = "";
        for (String rawLine : note.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith(BLOOD_PRESSURE_PREFIX) && line.endsWith(" mmHg")) {
                String values = line.substring(
                        BLOOD_PRESSURE_PREFIX.length(), line.length() - " mmHg".length());
                String[] pair = values.split("/", -1);
                if (pair.length == 2) {
                    systolic = normalize(pair[0]);
                    diastolic = normalize(pair[1]);
                }
            } else if (line.startsWith(HEART_RATE_PREFIX) && line.endsWith(" bpm")) {
                heartRate = normalize(line.substring(
                        HEART_RATE_PREFIX.length(), line.length() - " bpm".length()));
            } else if (line.startsWith(WEIGHT_PREFIX) && line.endsWith(" kg")) {
                weight = normalize(line.substring(
                        WEIGHT_PREFIX.length(), line.length() - " kg".length()));
            }
        }
        return new ManagerGuideVitalsDraft(
                systolic, diastolic, heartRate, weight, true);
    }

    static String format(
            @Nullable String systolic,
            @Nullable String diastolic,
            @Nullable String heartRate,
            @Nullable String weight
    ) {
        String safeSystolic = normalize(systolic);
        String safeDiastolic = normalize(diastolic);
        String safeHeartRate = normalize(heartRate);
        String safeWeight = normalizeWeight(weight);
        if (safeSystolic.isEmpty() && safeDiastolic.isEmpty()
                && safeHeartRate.isEmpty() && safeWeight.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(HEADER);
        if (!safeSystolic.isEmpty() && !safeDiastolic.isEmpty()) {
            result.append('\n').append(BLOOD_PRESSURE_PREFIX)
                    .append(safeSystolic).append('/').append(safeDiastolic)
                    .append(" mmHg");
        }
        if (!safeHeartRate.isEmpty()) {
            result.append('\n').append(HEART_RATE_PREFIX)
                    .append(safeHeartRate).append(" bpm");
        }
        if (!safeWeight.isEmpty()) {
            result.append('\n').append(WEIGHT_PREFIX)
                    .append(safeWeight).append(" kg");
        }
        return result.toString();
    }

    static boolean isPositiveInteger(@Nullable String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return true;
        }
        try {
            return Integer.parseInt(normalized) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean isPositiveDecimal(@Nullable String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return true;
        }
        try {
            return Double.parseDouble(normalized) > 0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String normalizeWeight(@Nullable String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            return new BigDecimal(normalized).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return normalized;
        }
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String raw(@Nullable String value) {
        return value == null ? "" : value;
    }
}
