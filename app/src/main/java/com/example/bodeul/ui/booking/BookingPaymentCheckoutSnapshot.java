package com.example.bodeul.ui.booking;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingRequestDraft;

/**
 * 결제 승인 화면에서 필요한 예약 결제 요약을 인텐트로 전달한다.
 */
public final class BookingPaymentCheckoutSnapshot {
    private static final String EXTRA_HOSPITAL_NAME = "hospitalName";
    private static final String EXTRA_DEPARTMENT_NAME = "departmentName";
    private static final String EXTRA_APPOINTMENT_AT = "appointmentAt";
    private static final String EXTRA_MEETING_PLACE = "meetingPlace";
    private static final String EXTRA_PAYMENT_METHOD_CODE = "paymentMethodCode";
    private static final String EXTRA_COUPON_CODE = "couponCode";
    private static final String EXTRA_FINAL_PRICE = "finalPrice";

    private final String hospitalName;
    private final String departmentName;
    private final String appointmentAt;
    private final String meetingPlace;
    private final BookingPaymentMethod paymentMethod;
    private final BookingCouponType couponType;
    private final int finalPrice;

    private BookingPaymentCheckoutSnapshot(
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            BookingPaymentMethod paymentMethod,
            BookingCouponType couponType,
            int finalPrice
    ) {
        this.hospitalName = normalize(hospitalName);
        this.departmentName = normalize(departmentName);
        this.appointmentAt = normalize(appointmentAt);
        this.meetingPlace = normalize(meetingPlace);
        this.paymentMethod = paymentMethod == null ? BookingPaymentMethod.CARD : paymentMethod;
        this.couponType = couponType == null ? BookingCouponType.NONE : couponType;
        this.finalPrice = Math.max(finalPrice, 0);
    }

    public static BookingPaymentCheckoutSnapshot fromDraft(BookingRequestDraft draft) {
        return new BookingPaymentCheckoutSnapshot(
                draft.getHospitalName(),
                draft.getDepartmentName(),
                draft.getAppointmentAt(),
                draft.getMeetingPlace(),
                draft.getPaymentMethod(),
                draft.getCouponType(),
                draft.getPriceSummary().getFinalPrice()
        );
    }

    public static BookingPaymentCheckoutSnapshot fromIntent(@NonNull Intent intent) {
        return new BookingPaymentCheckoutSnapshot(
                intent.getStringExtra(EXTRA_HOSPITAL_NAME),
                intent.getStringExtra(EXTRA_DEPARTMENT_NAME),
                intent.getStringExtra(EXTRA_APPOINTMENT_AT),
                intent.getStringExtra(EXTRA_MEETING_PLACE),
                BookingPaymentMethod.fromValue(intent.getStringExtra(EXTRA_PAYMENT_METHOD_CODE)),
                BookingCouponType.fromValue(intent.getStringExtra(EXTRA_COUPON_CODE)),
                intent.getIntExtra(EXTRA_FINAL_PRICE, 0)
        );
    }

    public void writeToIntent(Intent intent) {
        intent.putExtra(EXTRA_HOSPITAL_NAME, hospitalName);
        intent.putExtra(EXTRA_DEPARTMENT_NAME, departmentName);
        intent.putExtra(EXTRA_APPOINTMENT_AT, appointmentAt);
        intent.putExtra(EXTRA_MEETING_PLACE, meetingPlace);
        intent.putExtra(EXTRA_PAYMENT_METHOD_CODE, paymentMethod.name());
        intent.putExtra(EXTRA_COUPON_CODE, couponType.name());
        intent.putExtra(EXTRA_FINAL_PRICE, finalPrice);
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getAppointmentAt() {
        return appointmentAt;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public BookingPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BookingCouponType getCouponType() {
        return couponType;
    }

    public int getFinalPrice() {
        return finalPrice;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
