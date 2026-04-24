package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 관리자 서류 검토 영역의 문구와 색상 규칙을 모은다.
 */
public final class AdminManagerDocumentPresentationFormatter {
    private final Context context;
    private final SimpleDateFormat timestampFormatter;

    public AdminManagerDocumentPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String buildTitle(User manager) {
        return context.getString(R.string.admin_manager_document_title, manager.getName());
    }

    public String toStatusLabel(ManagerDocumentStatus status) {
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

    public int getStatusBackgroundColorResId(ManagerDocumentStatus status) {
        switch (status) {
            case APPROVED:
                return R.color.bodeul_success;
            case REJECTED:
                return R.color.bodeul_warning;
            case PENDING_REVIEW:
                return R.color.bodeul_primary;
            case NOT_SUBMITTED:
            default:
                return R.color.bodeul_surface_alt;
        }
    }

    public int getStatusTextColorResId(ManagerDocumentStatus status) {
        switch (status) {
            case APPROVED:
            case PENDING_REVIEW:
                return R.color.white;
            case REJECTED:
            case NOT_SUBMITTED:
            default:
                return R.color.bodeul_text_primary;
        }
    }

    public String buildSummaryText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getDocumentSummary())) {
            return context.getString(R.string.admin_manager_document_summary_empty);
        }
        return context.getString(
                R.string.admin_manager_document_summary_value,
                profile.getDocumentSummary()
        );
    }

    public String buildAvailabilityText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getAvailabilitySummary())) {
            return context.getString(R.string.admin_manager_document_availability_empty);
        }
        return context.getString(
                R.string.admin_manager_document_availability_value,
                profile.getAvailabilitySummary()
        );
    }

    public String buildReviewNoteText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getDocumentReviewNote())) {
            return context.getString(R.string.admin_manager_document_review_note_empty);
        }
        return context.getString(
                R.string.admin_manager_document_review_note_value,
                profile.getDocumentReviewNote()
        );
    }

    public String buildTimelineText(ManagerHomeProfile profile) {
        long submittedAtMillis = profile.getDocumentUpdatedAtMillis();
        long reviewedAtMillis = profile.getDocumentReviewedAtMillis();
        if (submittedAtMillis <= 0L
                && reviewedAtMillis <= 0L
                && TextUtils.isEmpty(profile.getDocumentReviewedByName())) {
            return context.getString(R.string.admin_manager_document_timeline_empty);
        }

        String reviewerName = TextUtils.isEmpty(profile.getDocumentReviewedByName())
                ? context.getString(R.string.admin_manager_document_timeline_none)
                : profile.getDocumentReviewedByName();
        return context.getString(
                R.string.admin_manager_document_timeline_value,
                formatTimestamp(submittedAtMillis),
                formatTimestamp(reviewedAtMillis),
                reviewerName
        );
    }

    public String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.admin_manager_document_timeline_none);
        }
        return timestampFormatter.format(timestampMillis);
    }

    public String toHistoryBadgeLabel(ManagerDocumentHistoryEventType eventType) {
        switch (eventType) {
            case APPROVED:
                return context.getString(R.string.admin_manager_document_history_approved);
            case REJECTED:
                return context.getString(R.string.admin_manager_document_history_rejected);
            case SUBMITTED:
            default:
                return context.getString(R.string.admin_manager_document_history_submitted);
        }
    }

    public int getHistoryBadgeBackgroundColorResId(ManagerDocumentHistoryEventType eventType) {
        switch (eventType) {
            case APPROVED:
                return R.color.bodeul_success;
            case REJECTED:
                return R.color.bodeul_warning;
            case SUBMITTED:
            default:
                return R.color.bodeul_primary;
        }
    }

    public int getHistoryBadgeTextColorResId(ManagerDocumentHistoryEventType eventType) {
        switch (eventType) {
            case REJECTED:
                return R.color.bodeul_text_primary;
            case APPROVED:
            case SUBMITTED:
            default:
                return R.color.white;
        }
    }

    public String buildHistoryActorText(String actorName) {
        return context.getString(R.string.admin_manager_document_history_actor, actorName);
    }

    public String buildHistoryBody(ManagerDocumentHistoryEntry historyEntry) {
        String detail = historyEntry.getEventType() == ManagerDocumentHistoryEventType.SUBMITTED
                ? historyEntry.getSummary()
                : historyEntry.getReviewNote();
        if (TextUtils.isEmpty(detail)) {
            detail = context.getString(R.string.admin_manager_document_history_body_empty);
        }

        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.APPROVED) {
            return context.getString(R.string.admin_manager_document_history_approved_body, detail);
        }
        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.REJECTED) {
            return context.getString(R.string.admin_manager_document_history_rejected_body, detail);
        }
        return context.getString(R.string.admin_manager_document_history_submitted_body, detail);
    }
}
