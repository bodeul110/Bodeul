package com.example.bodeul.util;

import java.util.Locale;

/**
 * 인증 화면과 저장소가 같은 규칙으로 사용자 프로필 값을 정리하도록 돕는다.
 */
public final class UserProfileSanitizer {
    private UserProfileSanitizer() {
    }

    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceAll("\\s+", " ");
    }

    public static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String rawPhone) {
        String digits = normalizePhoneDigits(rawPhone);
        if (digits.isEmpty()) {
            return "";
        }

        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }

        if (digits.length() == 10) {
            if (digits.startsWith("02")) {
                return digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6);
            }
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }

        if (digits.length() == 9 && digits.startsWith("02")) {
            return digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
        }

        return digits;
    }

    public static boolean isValidPhone(String rawPhone) {
        String digits = normalizePhoneDigits(rawPhone);
        if (digits.isEmpty() || !digits.startsWith("0")) {
            return false;
        }
        return digits.length() >= 9 && digits.length() <= 11;
    }

    private static String normalizePhoneDigits(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }

        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
