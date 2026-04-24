package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminEmergencyIssueRecord;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminSettlementRecord;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 관리자 운영/정산 섹션에서 반복되는 표기 규칙을 모은다.
 */
public final class AdminOperationsPresentationFormatter {
    private final Context context;
    private final BookingPresentationFormatter bookingFormatter;
    private final SimpleDateFormat timestampFormatter;

    public AdminOperationsPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
        this.bookingFormatter = new BookingPresentationFormatter(this.context);
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String formatPrice(int price) {
        return bookingFormatter.formatPrice(price);
    }

    public String formatPaymentMethod(String rawCode) {
        return bookingFormatter.toPaymentMethodLabel(rawCode);
    }

    public String formatSessionStatus(SessionStatus status) {
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

    public String formatSettlementStatus(AppointmentRequest request) {
        BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(request.getPaymentMethodCode());
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(request.getPaymentStatusCode());
        if (paymentMethod == BookingPaymentMethod.ON_SITE) {
            return context.getString(R.string.booking_follow_up_settlement_status_on_site);
        }
        if (paymentStatus == BookingPaymentStatus.AUTHORIZED) {
            return context.getString(R.string.booking_follow_up_settlement_status_authorized);
        }
        if (paymentStatus == BookingPaymentStatus.DEFERRED) {
            return context.getString(R.string.booking_follow_up_settlement_status_deferred);
        }
        return context.getString(R.string.booking_follow_up_settlement_status_pending);
    }

    public String formatSettlementNote(AppointmentRequest request) {
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

    public String formatManagerName(@Nullable User manager) {
        return manager == null
                ? context.getString(R.string.admin_manager_pending)
                : manager.getName();
    }

    public String formatFallbackValue(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(R.string.admin_request_detail_missing);
        }
        return value.trim();
    }

    public String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.admin_operation_action_pending);
        }
        return timestampFormatter.format(new Date(timestampMillis));
    }

    public String formatSettlementRecordStatus(@Nullable AdminSettlementStatus status) {
        if (status == null) {
            return context.getString(R.string.admin_settlement_follow_up_pending);
        }
        switch (status) {
            case CONFIRMED:
                return context.getString(R.string.admin_settlement_follow_up_confirmed);
            case NEEDS_REVIEW:
                return context.getString(R.string.admin_settlement_follow_up_recheck);
            case PENDING:
            default:
                return context.getString(R.string.admin_settlement_follow_up_pending);
        }
    }

    public String formatEmergencyIssueStatus(@Nullable AdminEmergencyIssueStatus status) {
        if (status == null) {
            return context.getString(R.string.admin_emergency_issue_empty);
        }
        if (status == AdminEmergencyIssueStatus.RESOLVED) {
            return context.getString(R.string.admin_emergency_issue_resolved);
        }
        return context.getString(R.string.admin_emergency_issue_reported);
    }

    public String formatFollowUpReview(@Nullable AppointmentFollowUpReviewRating rating) {
        return bookingFormatter.formatFollowUpReviewRating(rating);
    }

    public String formatFollowUpSettlementStatus(@Nullable AppointmentFollowUpSettlementStatus status) {
        return bookingFormatter.formatFollowUpSettlementStatus(status);
    }

    public String formatFollowUpSupportStatus(
            @Nullable AppointmentFollowUpSupportEscalationStatus status
    ) {
        return bookingFormatter.formatFollowUpSupportEscalationStatus(status);
    }

    public String toMonitoringFilterLabel(AdminMonitoringFilter filter) {
        switch (filter) {
            case EMERGENCY:
                return context.getString(R.string.admin_monitoring_filter_emergency);
            case PAYMENT:
                return context.getString(R.string.admin_monitoring_filter_payment);
            case MATCHED:
                return context.getString(R.string.admin_monitoring_filter_matched);
            case IN_PROGRESS:
                return context.getString(R.string.admin_monitoring_filter_in_progress);
            case ALL:
            default:
                return context.getString(R.string.admin_monitoring_filter_all);
        }
    }

    public String toSettlementFilterLabel(AdminSettlementFilter filter) {
        switch (filter) {
            case USER_HELP:
                return context.getString(R.string.admin_settlement_filter_user_help);
            case ADMIN_PENDING:
                return context.getString(R.string.admin_settlement_filter_admin_pending);
            case NEEDS_REVIEW:
                return context.getString(R.string.admin_settlement_filter_needs_review);
            case CONFIRMED:
                return context.getString(R.string.admin_settlement_filter_confirmed);
            case ALL:
            default:
                return context.getString(R.string.admin_settlement_filter_all);
        }
    }

    public String buildMonitoringAlertSummary(
            int totalCount,
            int emergencyCount,
            int paymentCount,
            int matchedCount,
            int inProgressCount,
            AdminMonitoringFilter selectedFilter,
            int visibleCount
    ) {
        if (totalCount <= 0) {
            return "";
        }
        return context.getString(
                R.string.admin_monitoring_alert_summary,
                emergencyCount,
                paymentCount,
                matchedCount,
                inProgressCount,
                toMonitoringFilterLabel(selectedFilter),
                visibleCount,
                totalCount
        );
    }

    public String buildSettlementAlertSummary(
            int totalCount,
            int urgentSupportCount,
            int userHelpCount,
            int adminPendingCount,
            int confirmedCount,
            AdminSettlementFilter selectedFilter,
            int visibleCount
    ) {
        if (totalCount <= 0) {
            return "";
        }
        return context.getString(
                R.string.admin_settlement_alert_summary,
                urgentSupportCount,
                userHelpCount,
                adminPendingCount,
                confirmedCount,
                toSettlementFilterLabel(selectedFilter),
                visibleCount,
                totalCount
        );
    }

    public String buildMonitoringActivityText(@Nullable AdminEmergencyIssueRecord issueRecord) {
        if (issueRecord == null) {
            return "";
        }
        return context.getString(
                R.string.admin_operation_activity_format,
                formatEmergencyIssueStatus(issueRecord.getStatus()),
                formatTimestamp(issueRecord.getHandledAtMillis())
        );
    }

    public String buildSettlementActivityText(
            @Nullable AppointmentFollowUpRecord followUpRecord,
            @Nullable AdminSettlementRecord settlementRecord
    ) {
        String latestLabel = "";
        long latestTimestamp = 0L;

        if (followUpRecord != null && followUpRecord.hasSavedSupportEscalation()) {
            latestLabel = context.getString(
                    R.string.admin_operation_activity_follow_up_support,
                    formatFollowUpSupportStatus(followUpRecord.getSupportEscalationStatus())
            );
            latestTimestamp = followUpRecord.getSupportEscalatedAtMillis();
        }
        if (followUpRecord != null && followUpRecord.hasSavedSettlement()
                && followUpRecord.getSettlementSavedAtMillis() >= latestTimestamp) {
            latestLabel = context.getString(
                    R.string.admin_operation_activity_follow_up_settlement,
                    formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus())
            );
            latestTimestamp = followUpRecord.getSettlementSavedAtMillis();
        }
        if (followUpRecord != null && followUpRecord.hasSavedReview()
                && followUpRecord.getReviewSavedAtMillis() >= latestTimestamp) {
            latestLabel = context.getString(
                    R.string.admin_operation_activity_follow_up_review,
                    formatFollowUpReview(followUpRecord.getReviewRating())
            );
            latestTimestamp = followUpRecord.getReviewSavedAtMillis();
        }
        if (settlementRecord != null && settlementRecord.getHandledAtMillis() >= latestTimestamp) {
            latestLabel = context.getString(
                    R.string.admin_operation_activity_admin_settlement,
                    formatSettlementRecordStatus(settlementRecord.getStatus())
            );
            latestTimestamp = settlementRecord.getHandledAtMillis();
        }

        if (TextUtils.isEmpty(latestLabel)) {
            return "";
        }
        return context.getString(
                R.string.admin_operation_activity_format,
                latestLabel,
                formatTimestamp(latestTimestamp)
        );
    }
}
