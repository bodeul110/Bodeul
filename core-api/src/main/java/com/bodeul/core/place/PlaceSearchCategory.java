package com.bodeul.core.place;

import java.util.Locale;

public enum PlaceSearchCategory {
    HOSPITAL("HP8"),
    PHARMACY("PM9");

    private final String kakaoCategoryCode;

    PlaceSearchCategory(String kakaoCategoryCode) {
        this.kakaoCategoryCode = kakaoCategoryCode;
    }

    String kakaoCategoryCode() {
        return kakaoCategoryCode;
    }

    static PlaceSearchCategory fromRequestValue(String value) {
        if (value == null) {
            throw PlaceSearchException.invalidRequest("검색 범주가 필요합니다.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PlaceSearchException.invalidRequest("검색 범주는 HOSPITAL 또는 PHARMACY만 사용할 수 있습니다.");
        }
    }
}
