package com.example.bodeul.domain.model;

/**
 * 예약 신청 화면에 보여줄 비용 요약 정보다.
 */
public final class BookingPriceSummary {
    private final int basePrice;
    private final int optionSurchargePrice;
    private final int couponDiscountPrice;
    private final int finalPrice;

    public BookingPriceSummary(
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice
    ) {
        this.basePrice = basePrice;
        this.optionSurchargePrice = optionSurchargePrice;
        this.couponDiscountPrice = couponDiscountPrice;
        this.finalPrice = finalPrice;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public int getOptionSurchargePrice() {
        return optionSurchargePrice;
    }

    public int getCouponDiscountPrice() {
        return couponDiscountPrice;
    }

    public int getFinalPrice() {
        return finalPrice;
    }

    public static BookingPriceSummary empty() {
        return new BookingPriceSummary(0, 0, 0, 0);
    }
}
