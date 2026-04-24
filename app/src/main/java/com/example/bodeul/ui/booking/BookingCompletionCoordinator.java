package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;

/**
 * 예약 완료 화면에 필요한 표시 문구를 조합한다.
 */
public final class BookingCompletionCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BookingCompletionCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context;
        this.formatter = formatter;
    }

    public BookingCompletionScreenModel buildScreenModel(
            BookingCompletionSnapshot snapshot,
            boolean isUpdated
    ) {
        String badge = context.getString(isUpdated
                ? R.string.booking_completion_badge_updated
                : R.string.booking_completion_badge_submitted);
        String title = context.getString(isUpdated
                ? R.string.booking_completion_title_updated
                : R.string.booking_completion_title_submitted);
        String body = context.getString(isUpdated
                ? R.string.booking_completion_body_updated
                : R.string.booking_completion_body_submitted);
        String hospital = context.getString(
                R.string.booking_status_hospital_value,
                snapshot.getHospitalName(),
                snapshot.getDepartmentName()
        );
        String optionSummary = context.getString(
                R.string.booking_completion_option_value,
                formatter.toTripTypeLabel(snapshot.getTripTypeCode()),
                formatter.toMobilityLabel(snapshot.getMobilitySupportCode())
        );
        String paymentSummary = context.getString(
                R.string.booking_completion_payment_value,
                formatter.toPaymentMethodLabel(snapshot.getPaymentMethodCode()),
                formatter.toCouponLabel(snapshot.getCouponCode()),
                formatter.formatPrice(snapshot.getFinalPrice())
        );
        if (!TextUtils.isEmpty(snapshot.getPaymentStatusCode())) {
            StringBuilder paymentBuilder = new StringBuilder(paymentSummary);
            paymentBuilder.append('\n').append(context.getString(
                    R.string.booking_completion_payment_status_value,
                    formatter.toPaymentStatusLabel(snapshot.getPaymentStatusCode())
            ));
            if (!TextUtils.isEmpty(snapshot.getPaymentProviderLabel())) {
                paymentBuilder.append('\n').append(context.getString(
                        R.string.booking_completion_payment_provider_value,
                        snapshot.getPaymentProviderLabel()
                ));
            }
            if (!TextUtils.isEmpty(snapshot.getPaymentApprovalCode())) {
                paymentBuilder.append('\n').append(context.getString(
                        R.string.booking_completion_payment_code_value,
                        snapshot.getPaymentApprovalCode()
                ));
            }
            if (!TextUtils.isEmpty(snapshot.getPaymentApprovedAt())) {
                paymentBuilder.append('\n').append(context.getString(
                        R.string.booking_completion_payment_time_value,
                        snapshot.getPaymentApprovedAt()
                ));
            }
            paymentSummary = paymentBuilder.toString();
        }
        String note = TextUtils.isEmpty(snapshot.getSpecialNotes())
                ? context.getString(R.string.booking_completion_note_empty)
                : snapshot.getSpecialNotes();
        return new BookingCompletionScreenModel(
                badge,
                title,
                body,
                snapshot.getRequestId(),
                snapshot.getAppointmentAt(),
                hospital,
                snapshot.getMeetingPlace(),
                optionSummary,
                paymentSummary,
                note,
                !TextUtils.isEmpty(snapshot.getSpecialNotes())
        );
    }
}
