package com.example.bodeul.domain.model;

/**
 * 환자의 이동 보조 필요 수준을 표현한다.
 */
public enum BookingMobilitySupport {
    INDEPENDENT(0),
    WALKING_AID(8000),
    WHEELCHAIR(15000);

    private final int surchargePrice;

    BookingMobilitySupport(int surchargePrice) {
        this.surchargePrice = surchargePrice;
    }

    public int getSurchargePrice() {
        return surchargePrice;
    }

    public static BookingMobilitySupport fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return INDEPENDENT;
        }
        try {
            return BookingMobilitySupport.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return INDEPENDENT;
        }
    }
}
