package com.example.bodeul.ui.booking;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingPaymentMethod;

/**
 * 결제 승인 화면에 필요한 안내 문구와 표시 값을 만든다.
 */
public final class BookingPaymentApprovalCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BookingPaymentApprovalCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public BookingPaymentApprovalScreenModel createScreenModel(BookingPaymentCheckoutSnapshot snapshot) {
        boolean deferredPayment = snapshot.getPaymentMethod() == BookingPaymentMethod.ON_SITE;
        String providerLabel = resolveProviderLabel(snapshot.getPaymentMethod());
        return new BookingPaymentApprovalScreenModel(
                context.getString(R.string.booking_payment_approval_badge),
                context.getString(deferredPayment
                        ? R.string.booking_payment_approval_title_deferred
                        : R.string.booking_payment_approval_title_authorized),
                context.getString(
                        deferredPayment
                                ? R.string.booking_payment_approval_body_deferred
                                : R.string.booking_payment_approval_body_authorized,
                        providerLabel
                ),
                context.getString(deferredPayment
                        ? R.string.booking_payment_approval_button_deferred
                        : R.string.booking_payment_approval_button_authorized),
                formatter.toPaymentMethodLabel(snapshot.getPaymentMethod().name()),
                formatter.toCouponLabel(snapshot.getCouponType().name()),
                formatter.formatPrice(snapshot.getFinalPrice()),
                snapshot.getAppointmentAt(),
                context.getString(
                        R.string.booking_status_hospital_value,
                        snapshot.getHospitalName(),
                        snapshot.getDepartmentName()
                ),
                snapshot.getMeetingPlace(),
                context.getString(
                        deferredPayment
                                ? R.string.booking_payment_approval_consent_deferred
                                : R.string.booking_payment_approval_consent_authorized,
                        providerLabel
                ),
                deferredPayment,
                providerLabel
        );
    }

    private String resolveProviderLabel(BookingPaymentMethod paymentMethod) {
        switch (paymentMethod) {
            case EASY_PAY:
                return context.getString(R.string.booking_payment_provider_easy_pay);
            case ON_SITE:
                return context.getString(R.string.booking_payment_provider_on_site);
            case CARD:
            default:
                return context.getString(R.string.booking_payment_provider_card);
        }
    }
}
