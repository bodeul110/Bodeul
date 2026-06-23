package com.example.bodeul.util;

import androidx.annotation.Nullable;

/**
 * 외부 저장소에서 읽은 enum 문자열을 예외 없이 파싱한다.
 */
public final class SafeEnumParser {
    private SafeEnumParser() {
    }

    @Nullable
    public static <T extends Enum<T>> T parseOrNull(Class<T> enumClass, @Nullable String rawValue) {
        if (enumClass == null || rawValue == null) {
            return null;
        }

        String normalizedValue = rawValue.trim();
        if (normalizedValue.isEmpty()) {
            return null;
        }

        try {
            return Enum.valueOf(enumClass, normalizedValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static <T extends Enum<T>> T parseOrDefault(
            Class<T> enumClass,
            @Nullable String rawValue,
            T fallbackValue
    ) {
        T parsedValue = parseOrNull(enumClass, rawValue);
        return parsedValue == null ? fallbackValue : parsedValue;
    }
}
