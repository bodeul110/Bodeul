package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingManagerGenderPreference;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.BookingTripType;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;

import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 예약 화면에 필요한 라벨과 금액 표시 규칙을 한곳에 모은다.
 */
public final class BookingPresentationFormatter {
    private final Context context;
    private final NumberFormat numberFormat;
    private final SimpleDateFormat timestampFormatter;

    public BookingPresentationFormatter(Context context) {
        this.context = context;
        this.numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_requested);
        }
    }

    public String toRoleLabel(UserRole role) {
        if (role == UserRole.GUARDIAN) {
            return context.getString(R.string.login_role_guardian);
        }
        return context.getString(R.string.login_role_patient);
    }

    public String toCounterpartRoleLabel(UserRole requesterRole) {
        return requesterRole == UserRole.GUARDIAN
                ? context.getString(R.string.login_role_patient)
                : context.getString(R.string.login_role_guardian);
    }

    public String toMobilityLabel(String code) {
        switch (BookingMobilitySupport.fromValue(code)) {
            case WALKING_AID:
                return context.getString(R.string.booking_mobility_walking_aid);
            case WHEELCHAIR:
                return context.getString(R.string.booking_mobility_wheelchair);
            case INDEPENDENT:
            default:
                return context.getString(R.string.booking_mobility_independent);
        }
    }

    public String toTripTypeLabel(String code) {
        switch (BookingTripType.fromValue(code)) {
            case ROUND_TRIP:
                return context.getString(R.string.booking_trip_round_trip);
            case ONE_WAY:
            default:
                return context.getString(R.string.booking_trip_one_way);
        }
    }

    public String toManagerGenderPreferenceLabel(String code) {
        switch (BookingManagerGenderPreference.fromValue(code)) {
            case FEMALE:
                return context.getString(R.string.booking_manager_gender_female);
            case MALE:
                return context.getString(R.string.booking_manager_gender_male);
            case ANY:
            default:
                return context.getString(R.string.booking_manager_gender_any);
        }
    }

    public String toPaymentMethodLabel(String code) {
        switch (BookingPaymentMethod.fromValue(code)) {
            case EASY_PAY:
                return context.getString(R.string.booking_payment_easy_pay);
            case ON_SITE:
                return context.getString(R.string.booking_payment_on_site);
            case CARD:
            default:
                return context.getString(R.string.booking_payment_card);
        }
    }

    public String toPaymentStatusLabel(String code) {
        switch (BookingPaymentStatus.fromValue(code)) {
            case AUTHORIZED:
                return context.getString(R.string.booking_payment_status_authorized);
            case DEFERRED:
                return context.getString(R.string.booking_payment_status_deferred);
            case PENDING:
            default:
                return context.getString(R.string.booking_payment_status_pending);
        }
    }

    public String toSessionStatusLabel(SessionStatus status) {
        switch (status) {
            case READY:
                return context.getString(R.string.guardian_report_session_ready);
            case WAITING:
                return context.getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return context.getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return context.getString(R.string.guardian_report_session_payment);
            case CANCELED:
                return context.getString(R.string.guardian_report_session_canceled);
            case COMPLETED:
                return context.getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return context.getString(R.string.guardian_report_session_meeting);
        }
    }

    public String toCouponLabel(String code) {
        switch (BookingCouponType.fromValue(code)) {
            case FIRST_VISIT:
                return context.getString(R.string.booking_coupon_first_visit);
            case FAMILY:
                return context.getString(R.string.booking_coupon_family);
            case NONE:
            default:
                return context.getString(R.string.booking_coupon_none);
        }
    }

    public String formatPrice(int price) {
        return context.getString(R.string.booking_price_value_format, numberFormat.format(Math.max(price, 0)));
    }

    public String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.booking_follow_up_status_pending);
        }
        return timestampFormatter.format(new Date(timestampMillis));
    }

    public String formatFollowUpReviewRating(AppointmentFollowUpReviewRating rating) {
        if (rating == null) {
            return context.getString(R.string.booking_follow_up_status_pending);
        }
        switch (rating) {
            case EXCELLENT:
                return context.getString(R.string.booking_follow_up_rating_excellent_title);
            case GOOD:
                return context.getString(R.string.booking_follow_up_rating_good_title);
            case DISAPPOINTING:
                return context.getString(R.string.booking_follow_up_rating_disappointing_title);
            case NEED_HELP:
                return context.getString(R.string.booking_follow_up_rating_need_help_title);
            case OK:
            default:
                return context.getString(R.string.booking_follow_up_rating_ok_title);
        }
    }

    public String formatFollowUpSettlementStatus(AppointmentFollowUpSettlementStatus status) {
        if (status == null) {
            return context.getString(R.string.booking_follow_up_status_pending);
        }
        if (status == AppointmentFollowUpSettlementStatus.CONFIRMED) {
            return context.getString(R.string.booking_follow_up_settlement_follow_up_confirmed);
        }
        return context.getString(R.string.booking_follow_up_settlement_follow_up_needs_help);
    }

    public String formatFollowUpSupportEscalationStatus(
            AppointmentFollowUpSupportEscalationStatus status
    ) {
        if (status == null) {
            return context.getString(R.string.booking_follow_up_status_pending);
        }
        switch (status) {
            case MANAGER_CALLED:
                return context.getString(R.string.booking_follow_up_support_status_manager_called);
            case DIALED_119:
                return context.getString(R.string.booking_follow_up_support_status_dialed_119);
            case GUIDE_VIEWED:
            default:
                return context.getString(R.string.booking_follow_up_support_status_guide_viewed);
        }
    }

    public String buildFollowUpSummary(AppointmentFollowUpRecord followUpRecord) {
        if (followUpRecord == null || !followUpRecord.hasAnySavedAction()) {
            return context.getString(R.string.booking_follow_up_summary_pending);
        }

        List<String> parts = new ArrayList<>();
        if (followUpRecord.hasSavedReview()) {
            parts.add(context.getString(
                    R.string.booking_follow_up_summary_review_format,
                    formatFollowUpReviewRating(followUpRecord.getReviewRating())
            ));
        }
        if (followUpRecord.hasSavedSettlement()) {
            parts.add(context.getString(
                    R.string.booking_follow_up_summary_settlement_format,
                    formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus())
            ));
        }
        if (followUpRecord.hasSavedSupportEscalation()) {
            parts.add(context.getString(
                    R.string.booking_follow_up_summary_support_format,
                    formatFollowUpSupportEscalationStatus(followUpRecord.getSupportEscalationStatus())
            ));
        }
        if (parts.isEmpty()) {
            return context.getString(R.string.booking_follow_up_summary_pending);
        }
        return TextUtils.join(" · ", parts);
    }

    public String buildSettlementFollowUpNote(
            AppointmentRequest request,
            AppointmentFollowUpSettlementStatus status
    ) {
        String paymentSummary = buildSettlementNote(request);
        if (status == AppointmentFollowUpSettlementStatus.CONFIRMED) {
            return context.getString(
                    R.string.booking_follow_up_settlement_follow_up_note_confirm_format,
                    paymentSummary
            );
        }
        return context.getString(
                R.string.booking_follow_up_settlement_follow_up_note_help_format,
                paymentSummary
        );
    }

    public String buildSettlementNote(AppointmentRequest request) {
        BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(request.getPaymentMethodCode());
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(request.getPaymentStatusCode());
        if (paymentMethod == BookingPaymentMethod.ON_SITE) {
            return context.getString(R.string.booking_follow_up_settlement_note_on_site);
        }
        if (paymentStatus == BookingPaymentStatus.AUTHORIZED) {
            return context.getString(R.string.booking_follow_up_settlement_note_authorized);
        }
        if (paymentStatus == BookingPaymentStatus.DEFERRED) {
            return context.getString(R.string.booking_follow_up_settlement_note_deferred);
        }
        return context.getString(R.string.booking_follow_up_settlement_note_pending);
    }

    public String buildLinkedParticipantLine(AppointmentRequest request, UserRole requesterRole) {
        boolean requesterIsGuardian = requesterRole == UserRole.GUARDIAN;
        String linkedName = requesterIsGuardian ? request.getPatientName() : request.getGuardianName();
        String linkedPhone = requesterIsGuardian ? request.getPatientPhone() : request.getGuardianPhone();
        String linkedEmail = requesterIsGuardian ? request.getPatientEmail() : request.getGuardianEmail();
        String linkedUserId = requesterIsGuardian ? request.getPatientUserId() : request.getGuardianUserId();

        String primaryValue;
        if (!TextUtils.isEmpty(linkedName) && !TextUtils.isEmpty(linkedPhone)) {
            primaryValue = context.getString(R.string.booking_linked_value_name_phone, linkedName, linkedPhone);
        } else if (!TextUtils.isEmpty(linkedName) && !TextUtils.isEmpty(linkedEmail)) {
            primaryValue = context.getString(R.string.booking_linked_value_name_phone, linkedName, linkedEmail);
        } else if (!TextUtils.isEmpty(linkedName)) {
            primaryValue = linkedName;
        } else if (!TextUtils.isEmpty(linkedPhone)) {
            primaryValue = linkedPhone;
        } else if (!TextUtils.isEmpty(linkedEmail)) {
            primaryValue = linkedEmail;
        } else {
            return context.getString(R.string.booking_linked_pending);
        }

        if (TextUtils.isEmpty(linkedUserId)) {
            return context.getString(R.string.booking_linked_pending_format, primaryValue);
        }
        return primaryValue;
    }
}
