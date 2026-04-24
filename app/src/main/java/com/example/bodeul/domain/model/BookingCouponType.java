package com.example.bodeul.domain.model;

/**
 * 예약 단계에서 선택할 수 있는 쿠폰 종류다.
 */
public enum BookingCouponType {
    NONE(0),
    FIRST_VISIT(5000),
    FAMILY(10000);

    private final int discountPrice;

    BookingCouponType(int discountPrice) {
        this.discountPrice = discountPrice;
    }

    public int getDiscountPrice() {
        return discountPrice;
    }

    public static BookingCouponType fromValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return NONE;
        }
        try {
            return BookingCouponType.valueOf(rawValue.trim());
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
