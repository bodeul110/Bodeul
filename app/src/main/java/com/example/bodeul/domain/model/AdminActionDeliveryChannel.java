package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림이 전달되는 채널을 정의한다.
 */
public enum AdminActionDeliveryChannel {
    APP_PUSH("app_push"),
    OPERATIONS_FEED("operations_feed");

    private final String value;

    AdminActionDeliveryChannel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AdminActionDeliveryChannel fromValue(String value) {
        if (value == null) {
            return OPERATIONS_FEED;
        }
        for (AdminActionDeliveryChannel channel : values()) {
            if (channel.value.equalsIgnoreCase(value.trim())) {
                return channel;
            }
        }
        return OPERATIONS_FEED;
    }
}
