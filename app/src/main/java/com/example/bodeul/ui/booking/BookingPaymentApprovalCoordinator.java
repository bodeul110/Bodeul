package com.example.bodeul.ui.booking;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingPaymentMethod;

/**
 * MVP 결제 확인 화면에 필요한 안내 문구와 표시 값을 만든다.
 */
public final class BookingPaymentApprovalCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BookingPaymentApprovalCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public BookingPaymentApprovalScreenModel createScreenModel(BookingPaymentCheckoutSnapshot snapshot) {
        BookingPaymentConfirmationMode confirmationMode = BookingPaymentConfirmationMode.fromPaymentMethod(
                snapshot.getPaymentMethod()
        );
        String providerLabel = resolveProviderLabel(snapshot.getPaymentMethod());
        String badge;
        String title;
        String body;
        String approvalButtonText;
        String consentText;
        switch (confirmationMode) {
            case BANK_TRANSFER_SYNTHETIC:
                badge = context.getString(R.string.booking_payment_approval_badge_bank_transfer);
                title = context.getString(R.string.booking_payment_approval_title_bank_transfer);
                body = context.getString(R.string.booking_payment_approval_body_bank_transfer);
                approvalButtonText = context.getString(R.string.booking_payment_approval_button_bank_transfer);
                consentText = context.getString(R.string.booking_payment_approval_consent_bank_transfer);
                break;
            case DEFERRED:
                badge = context.getString(R.string.booking_payment_approval_badge);
                title = context.getString(R.string.booking_payment_approval_title_deferred);
                body = context.getString(R.string.booking_payment_approval_body_deferred, providerLabel);
                approvalButtonText = context.getString(R.string.booking_payment_approval_button_deferred);
                consentText = context.getString(
                        R.string.booking_payment_approval_consent_deferred,
                        providerLabel
                );
                break;
            case BLOCKED:
                badge = context.getString(R.string.booking_payment_approval_badge_blocked);
                title = context.getString(R.string.booking_payment_approval_title_blocked);
                body = context.getString(R.string.booking_payment_approval_body_blocked);
                approvalButtonText = context.getString(R.string.booking_payment_approval_button_blocked);
                consentText = context.getString(R.string.booking_payment_approval_consent_blocked);
                break;
            case SIMULATION:
            default:
                badge = context.getString(R.string.booking_payment_approval_badge);
                title = context.getString(R.string.booking_payment_approval_title_authorized);
                body = context.getString(R.string.booking_payment_approval_body_authorized, providerLabel);
                approvalButtonText = context.getString(R.string.booking_payment_approval_button_authorized);
                consentText = context.getString(
                        R.string.booking_payment_approval_consent_authorized,
                        providerLabel
                );
                break;
        }
        return new BookingPaymentApprovalScreenModel(
                badge,
                title,
                body,
                approvalButtonText,
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
                consentText,
                confirmationMode,
                providerLabel
        );
    }

    private String resolveProviderLabel(BookingPaymentMethod paymentMethod) {
        switch (paymentMethod) {
            case BANK_TRANSFER:
                return context.getString(R.string.booking_payment_provider_bank_transfer_simulation);
            case EASY_PAY:
                return context.getString(R.string.booking_payment_provider_easy_pay_simulation);
            case ON_SITE:
                return context.getString(R.string.booking_payment_provider_on_site);
            case UNKNOWN:
                return context.getString(R.string.booking_payment_provider_unknown);
            case CARD:
            default:
                return context.getString(R.string.booking_payment_provider_card_simulation);
        }
    }
}
