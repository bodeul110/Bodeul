package com.example.bodeul.domain.model;

/**
 * 희망 매니저 성별 선호를 표현한다.
 */
public enum BookingManagerGenderPreference {
    ANY,
    FEMALE,
    MALE;

    public static BookingManagerGenderPreference fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return ANY;
        }
        try {
            return BookingManagerGenderPreference.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return ANY;
        }
    }
}
