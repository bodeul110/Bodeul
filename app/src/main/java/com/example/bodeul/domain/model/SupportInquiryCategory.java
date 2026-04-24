package com.example.bodeul.domain.model;

/**
 * 매니저가 남기는 지원 문의 유형 코드다.
 */
public enum SupportInquiryCategory {
    MATCHING("matching"),
    DOCUMENT("document"),
    SETTLEMENT("settlement"),
    OTHER("other");

    private final String value;

    SupportInquiryCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SupportInquiryCategory fromValue(String value) {
        if (value == null) {
            return MATCHING;
        }
        for (SupportInquiryCategory category : values()) {
            if (category.value.equalsIgnoreCase(value)
                    || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        return MATCHING;
    }
}
