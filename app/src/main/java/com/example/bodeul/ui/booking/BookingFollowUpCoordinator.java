package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 완료된 예약 상세를 후기·정산·SOS 후속 화면 모델로 조합한다.
 */
public final class BookingFollowUpCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BookingFollowUpCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public BookingFollowUpScreenModel buildScreenModel(
            User currentUser,
            AppointmentRequestDetail detail,
            boolean isFirebaseBacked,
            @Nullable AppointmentFollowUpReviewRating selectedRating,
            AppointmentFollowUpRecord followUpRecord
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        AppointmentFollowUpReviewRating effectiveRating = selectedRating != null
                ? selectedRating
                : followUpRecord.getReviewRating();
        return new BookingFollowUpScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.booking_follow_up_hero_badge),
                context.getString(
                        R.string.booking_follow_up_hero_title,
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                buildHeroBody(detail),
                context.getString(R.string.booking_follow_up_review_title),
                buildReviewBody(currentUser, detail),
                createRatingOptions(effectiveRating),
                buildReviewSummary(currentUser, effectiveRating),
                buildSavedState(followUpRecord),
                context.getString(followUpRecord.hasSavedReview()
                        ? R.string.booking_follow_up_review_action_update
                        : R.string.booking_follow_up_review_action_save),
                selectedRating != null,
                createSettlementLines(detail),
                buildSettlementSavedState(followUpRecord),
                context.getString(R.string.booking_follow_up_settlement_action_confirm),
                true,
                context.getString(R.string.booking_follow_up_settlement_action_help),
                true,
                context.getString(R.string.booking_follow_up_emergency_title),
                buildEmergencyBody(detail),
                createEmergencyLines(currentUser, detail),
                buildEmergencySavedState(followUpRecord),
                detail.getManager() != null && !TextUtils.isEmpty(detail.getManager().getPhone())
        );
    }

    public String createSettlementSaveNote(
            AppointmentRequestDetail detail,
            AppointmentFollowUpSettlementStatus status
    ) {
        return formatter.buildSettlementFollowUpNote(detail.getAppointmentRequest(), status);
    }

    private String buildHeroBody(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        SessionReport report = detail.getSessionReport();
        String summary = report == null || TextUtils.isEmpty(report.getSummary())
                ? context.getString(R.string.booking_follow_up_report_pending)
                : report.getSummary();
        return context.getString(
                R.string.booking_follow_up_hero_body,
                request.getAppointmentAt(),
                TextUtils.isEmpty(request.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing)
                        : request.getMeetingPlace(),
                summary
        );
    }

    private String buildReviewBody(User currentUser, AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        String patientName = buildPatientName(detail, request);
        return context.getString(
                currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_review_body_guardian
                        : R.string.booking_follow_up_review_body_patient,
                patientName
        );
    }

    private List<BookingFollowUpRatingOptionModel> createRatingOptions(
            @Nullable AppointmentFollowUpReviewRating selectedRating
    ) {
        List<BookingFollowUpRatingOptionModel> items = new ArrayList<>();
        for (AppointmentFollowUpReviewRating rating : AppointmentFollowUpReviewRating.values()) {
            items.add(new BookingFollowUpRatingOptionModel(
                    rating,
                    getRatingTitle(rating),
                    getRatingBody(rating),
                    rating == selectedRating
            ));
        }
        return items;
    }

    private String buildReviewSummary(
            User currentUser,
            @Nullable AppointmentFollowUpReviewRating selectedRating
    ) {
        if (selectedRating == null) {
            return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                    ? R.string.booking_follow_up_review_summary_empty_guardian
                    : R.string.booking_follow_up_review_summary_empty_patient);
        }
        return context.getString(
                R.string.booking_follow_up_review_summary_format,
                getRatingTitle(selectedRating),
                getRatingSummary(currentUser, selectedRating)
        );
    }

    private String buildSavedState(AppointmentFollowUpRecord followUpRecord) {
        if (!followUpRecord.hasSavedReview()) {
            return context.getString(R.string.booking_follow_up_review_saved_empty);
        }
        return context.getString(
                R.string.booking_follow_up_review_saved_value,
                formatter.formatTimestamp(followUpRecord.getReviewSavedAtMillis())
        );
    }

    private String buildSettlementSavedState(AppointmentFollowUpRecord followUpRecord) {
        if (!followUpRecord.hasSavedSettlement()) {
            return context.getString(R.string.booking_follow_up_settlement_saved_empty);
        }
        return context.getString(
                R.string.booking_follow_up_settlement_saved_value,
                formatter.formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus()),
                formatter.formatTimestamp(followUpRecord.getSettlementSavedAtMillis())
        );
    }

    private List<BookingStatusLineItem> createSettlementLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        SessionReport report = detail.getSessionReport();
        List<BookingStatusLineItem> items = new ArrayList<>();
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_settlement_line_amount),
                formatter.formatPrice(request.getFinalPrice()),
                true
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_settlement_line_method),
                formatter.toPaymentMethodLabel(request.getPaymentMethodCode()),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_settlement_line_status),
                buildSettlementStatus(request),
                false
        ));
        addOptionalLine(
                items,
                R.string.booking_follow_up_settlement_line_provider,
                request.getPaymentProviderLabel(),
                false
        );
        addOptionalLine(
                items,
                R.string.booking_follow_up_settlement_line_approval,
                request.getPaymentApprovalCode(),
                false
        );
        addOptionalLine(
                items,
                R.string.booking_follow_up_settlement_line_approved_at,
                request.getPaymentApprovedAt(),
                false
        );
        addOptionalLine(
                items,
                R.string.booking_follow_up_settlement_line_next_visit,
                report == null ? "" : report.getNextVisitAt(),
                false
        );
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_settlement_line_note),
                formatter.buildSettlementNote(request),
                false
        ));
        return items;
    }

    private String buildEmergencyBody(AppointmentRequestDetail detail) {
        CompanionSession session = detail.getSession();
        if (session != null && !TextUtils.isEmpty(session.getLocationSummary())) {
            return context.getString(
                    R.string.booking_follow_up_emergency_body_location,
                    session.getLocationSummary()
            );
        }
        return context.getString(R.string.booking_follow_up_emergency_body_default);
    }

    private String buildEmergencySavedState(AppointmentFollowUpRecord followUpRecord) {
        if (!followUpRecord.hasSavedSupportEscalation()) {
            return context.getString(R.string.booking_follow_up_support_saved_empty);
        }
        return context.getString(
                R.string.booking_follow_up_support_saved_value,
                formatter.formatFollowUpSupportEscalationStatus(
                        followUpRecord.getSupportEscalationStatus()
                ),
                formatter.formatTimestamp(followUpRecord.getSupportEscalatedAtMillis())
        );
    }

    private List<BookingStatusLineItem> createEmergencyLines(
            User currentUser,
            AppointmentRequestDetail detail
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<BookingStatusLineItem> items = new ArrayList<>();
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_manager),
                buildContactText(detail.getManager(), context.getString(R.string.booking_status_manager_pending)),
                true
        ));
        items.add(new BookingStatusLineItem(
                currentUser.getRole() == UserRole.GUARDIAN
                        ? context.getString(R.string.booking_status_line_patient)
                        : context.getString(R.string.booking_status_line_guardian),
                currentUser.getRole() == UserRole.GUARDIAN
                        ? buildLinkedContact(
                                detail.getPatient(),
                                request.getPatientName(),
                                request.getPatientPhone(),
                                request.getPatientEmail()
                        )
                        : buildLinkedContact(
                                detail.getGuardian(),
                                request.getGuardianName(),
                                request.getGuardianPhone(),
                                request.getGuardianEmail()
                        ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_emergency_line_place),
                TextUtils.isEmpty(request.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing)
                        : request.getMeetingPlace(),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_follow_up_emergency_line_steps),
                context.getString(R.string.booking_follow_up_emergency_steps_value),
                false
        ));
        return items;
    }

    private String buildSettlementStatus(AppointmentRequest request) {
        if (!TextUtils.isEmpty(request.getPaymentStatusCode())) {
            switch (BookingPaymentStatus.fromValue(request.getPaymentStatusCode())) {
                case AUTHORIZED:
                    return context.getString(R.string.booking_follow_up_settlement_status_authorized);
                case DEFERRED:
                    return context.getString(R.string.booking_follow_up_settlement_status_deferred);
                case PENDING:
                default:
                    return context.getString(R.string.booking_follow_up_settlement_status_pending);
            }
        }
        if (BookingPaymentMethod.fromValue(request.getPaymentMethodCode()) == BookingPaymentMethod.ON_SITE) {
            return context.getString(R.string.booking_follow_up_settlement_status_on_site);
        }
        return context.getString(R.string.booking_follow_up_settlement_status_pending);
    }

    private void addOptionalLine(List<BookingStatusLineItem> items, int labelResId, String value, boolean emphasized) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        items.add(new BookingStatusLineItem(
                context.getString(labelResId),
                value,
                emphasized
        ));
    }

    private String buildPatientName(AppointmentRequestDetail detail, AppointmentRequest request) {
        if (detail.getPatient() != null && !TextUtils.isEmpty(detail.getPatient().getName())) {
            return detail.getPatient().getName();
        }
        if (!TextUtils.isEmpty(request.getPatientName())) {
            return request.getPatientName();
        }
        return context.getString(R.string.login_role_patient);
    }

    private String buildLinkedContact(
            @Nullable User user,
            String fallbackName,
            String fallbackPhone,
            String fallbackEmail
    ) {
        if (user != null) {
            return buildContactText(user, context.getString(R.string.booking_status_contact_missing));
        }
        String value = buildContactValue(fallbackName, fallbackPhone, fallbackEmail);
        return TextUtils.isEmpty(value)
                ? context.getString(R.string.booking_status_contact_missing)
                : value;
    }

    private String buildContactText(@Nullable User user, String emptyFallback) {
        if (user == null) {
            return emptyFallback;
        }
        String value = buildContactValue(user.getName(), user.getPhone(), user.getEmail());
        return TextUtils.isEmpty(value) ? emptyFallback : value;
    }

    private String buildContactValue(String name, String phone, String email) {
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, phone);
        }
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(email)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, email);
        }
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        if (!TextUtils.isEmpty(phone)) {
            return phone;
        }
        if (!TextUtils.isEmpty(email)) {
            return email;
        }
        return "";
    }

    private String getRatingTitle(AppointmentFollowUpReviewRating rating) {
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

    private String getRatingBody(AppointmentFollowUpReviewRating rating) {
        switch (rating) {
            case EXCELLENT:
                return context.getString(R.string.booking_follow_up_rating_excellent_body);
            case GOOD:
                return context.getString(R.string.booking_follow_up_rating_good_body);
            case DISAPPOINTING:
                return context.getString(R.string.booking_follow_up_rating_disappointing_body);
            case NEED_HELP:
                return context.getString(R.string.booking_follow_up_rating_need_help_body);
            case OK:
            default:
                return context.getString(R.string.booking_follow_up_rating_ok_body);
        }
    }

    private String getRatingSummary(User currentUser, AppointmentFollowUpReviewRating rating) {
        switch (rating) {
            case EXCELLENT:
                return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_rating_excellent_summary_guardian
                        : R.string.booking_follow_up_rating_excellent_summary_patient);
            case GOOD:
                return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_rating_good_summary_guardian
                        : R.string.booking_follow_up_rating_good_summary_patient);
            case DISAPPOINTING:
                return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_rating_disappointing_summary_guardian
                        : R.string.booking_follow_up_rating_disappointing_summary_patient);
            case NEED_HELP:
                return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_rating_need_help_summary_guardian
                        : R.string.booking_follow_up_rating_need_help_summary_patient);
            case OK:
            default:
                return context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_follow_up_rating_ok_summary_guardian
                        : R.string.booking_follow_up_rating_ok_summary_patient);
        }
    }
}
