package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 매니저 홈에서 반복되는 문구 조합과 상태 라벨 변환을 담당한다.
 */
public final class ManagerHomePresentationFormatter {
    private final Context context;

    public ManagerHomePresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String summarizeCardText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }

    public String buildActionDescription(String savedValue, int emptyResId) {
        if (TextUtils.isEmpty(savedValue)) {
            return context.getString(emptyResId);
        }
        return summarizeCardText(savedValue);
    }

    public String buildDocumentStatusText(ManagerHomeProfile profile) {
        String statusLabel = toDocumentStatusLabel(profile.getDocumentStatus());
        if (TextUtils.isEmpty(profile.getDocumentReviewNote())) {
            return context.getString(R.string.manager_action_docs_status_plain, statusLabel);
        }
        return context.getString(
                R.string.manager_action_docs_status_with_note,
                statusLabel,
                summarizeCardText(profile.getDocumentReviewNote())
        );
    }

    public String buildDocumentReviewNote(String reviewNote) {
        if (TextUtils.isEmpty(reviewNote)) {
            return context.getString(R.string.manager_profile_review_note_empty);
        }
        return summarizeCardText(reviewNote);
    }

    public String buildDocumentTimelineText(ManagerHomeProfile profile) {
        if (profile.getDocumentUpdatedAtMillis() <= 0L
                && profile.getDocumentReviewedAtMillis() <= 0L
                && TextUtils.isEmpty(profile.getDocumentReviewedByName())) {
            return context.getString(R.string.manager_profile_timeline_empty);
        }

        String reviewedBy = TextUtils.isEmpty(profile.getDocumentReviewedByName())
                ? context.getString(R.string.manager_profile_timeline_pending)
                : profile.getDocumentReviewedByName();
        return context.getString(
                R.string.manager_profile_timeline_value,
                formatTimestamp(profile.getDocumentUpdatedAtMillis()),
                formatTimestamp(profile.getDocumentReviewedAtMillis()),
                reviewedBy
        );
    }

    public String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.manager_profile_timeline_pending);
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        return formatter.format(new Date(timestampMillis));
    }

    public String toDocumentStatusLabel(ManagerDocumentStatus status) {
        switch (status) {
            case APPROVED:
                return context.getString(R.string.manager_document_status_approved);
            case REJECTED:
                return context.getString(R.string.manager_document_status_rejected);
            case PENDING_REVIEW:
                return context.getString(R.string.manager_document_status_pending_review);
            case NOT_SUBMITTED:
            default:
                return context.getString(R.string.manager_document_status_not_submitted);
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

    public String buildLiveFeedSubtitle(ManagerDashboard dashboard) {
        return context.getString(
                R.string.manager_home_live_subtitle,
                dashboard.getAppointmentRequest().getHospitalName(),
                dashboard.getAppointmentRequest().getDepartmentName()
        );
    }

    public String buildLiveFeedNote(ManagerDashboard dashboard) {
        String specialNotes = summarizeCardText(dashboard.getAppointmentRequest().getSpecialNotes());
        if (specialNotes.isEmpty()) {
            specialNotes = context.getString(R.string.manager_home_live_note_empty);
        }
        return context.getString(R.string.manager_home_live_note, specialNotes);
    }

    public String buildLiveFeedFooter(ManagerDashboard dashboard) {
        return context.getString(
                R.string.manager_home_live_footer,
                dashboard.getGuardian().getName(),
                dashboard.getGuardian().getPhone()
        );
    }
}
