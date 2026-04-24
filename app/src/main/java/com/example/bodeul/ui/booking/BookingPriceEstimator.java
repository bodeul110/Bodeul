package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingTripType;

/**
 * 예약 화면의 예상 비용을 단순 규칙으로 계산한다.
 */
public final class BookingPriceEstimator {
    private static final int BASE_PRICE = 69000;

    public BookingPriceSummary estimate(
            BookingTripType tripType,
            BookingMobilitySupport mobilitySupport,
            BookingCouponType couponType
    ) {
        int optionSurchargePrice = tripType.getSurchargePrice() + mobilitySupport.getSurchargePrice();
        int subtotal = BASE_PRICE + optionSurchargePrice;
        int couponDiscountPrice = Math.min(subtotal, couponType.getDiscountPrice());
        int finalPrice = subtotal - couponDiscountPrice;
        return new BookingPriceSummary(
                BASE_PRICE,
                optionSurchargePrice,
                couponDiscountPrice,
                finalPrice
        );
    }
}
