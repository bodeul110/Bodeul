package com.example.bodeul.domain.model;

/**
 * 관리자 후속 처리 기록이 어떤 운영 축에서 발생했는지 나타낸다.
 */
public enum AdminActionSourceType {
    SETTLEMENT("SETTLEMENT"),
    EMERGENCY("EMERGENCY"),
    SUPPORT("SUPPORT");

    private final String value;

    AdminActionSourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionSourceType fromValue(String value) {
        if (value == null) {
            return SUPPORT;
        }
        for (AdminActionSourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return SUPPORT;
    }
}
