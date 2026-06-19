package com.example.bodeul.domain.model;

/**
 * 환자와 보호자가 남기는 문의 유형 코드다.
 */
public enum ClientSupportCategory {
    RESERVATION("reservation"),
    PROGRESS("progress"),
    REPORT("report"),
    SETTLEMENT("settlement"),
    OTHER("other");

    private final String value;

    ClientSupportCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClientSupportCategory fromValue(String value) {
        if (value == null) {
            return RESERVATION;
        }
        for (ClientSupportCategory category : values()) {
            if (category.value.equalsIgnoreCase(value)) {
                return category;
            }
        }
        return RESERVATION;
    }
}
