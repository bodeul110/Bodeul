package com.example.bodeul.domain.model;

/**
 * 병원 동행 이동 범위를 구분한다.
 */
public enum BookingTripType {
    ONE_WAY(0),
    ROUND_TRIP(22000);

    private final int surchargePrice;

    BookingTripType(int surchargePrice) {
        this.surchargePrice = surchargePrice;
    }

    public int getSurchargePrice() {
        return surchargePrice;
    }

    public static BookingTripType fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return ONE_WAY;
        }
        try {
            return BookingTripType.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return ONE_WAY;
        }
    }
}
