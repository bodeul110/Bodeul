package com.example.bodeul.domain.model;

import androidx.annotation.Nullable;

/**
 * 종료 후 후기 화면에서 사용하는 만족도 코드다.
 */
public enum AppointmentFollowUpReviewRating {
    EXCELLENT("excellent"),
    GOOD("good"),
    OK("ok"),
    DISAPPOINTING("disappointing"),
    NEED_HELP("need_help");

    private final String value;

    AppointmentFollowUpReviewRating(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static AppointmentFollowUpReviewRating fromValue(@Nullable String value) {
        for (AppointmentFollowUpReviewRating item : values()) {
            if (item.value.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return item;
            }
        }
        return null;
    }
}
