package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationPriority;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.AdminAuditLogEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 후속 알림 및 감사 로그 섹션에서 쓰는 문구 규칙을 담당한다.
 */
public final class AdminActionCenterPresentationFormatter {
    private final Context context;
    private final SimpleDateFormat timestampFormatter;

    public AdminActionCenterPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String buildSummary(
            int notificationCount,
            int unreadCount,
            int unresolvedCount,
            int auditCount
    ) {
        if (notificationCount == 0 && auditCount == 0) {
            return context.getString(R.string.admin_action_center_summary_empty);
        }
        return context.getString(
                R.string.admin_action_center_summary,
                notificationCount,
                unreadCount,
                unresolvedCount,
                auditCount
        );
    }

    public String getNotificationBadgeText() {
        return context.getString(R.string.admin_action_center_badge_notification);
    }

    public String getAuditBadgeText() {
        return context.getString(R.string.admin_action_center_badge_audit);
    }

    public String getUnreadStateText() {
        return context.getString(R.string.admin_action_center_state_unread);
    }

    public String getReadStateText() {
        return context.getString(R.string.admin_action_center_state_read);
    }

    public String getResolvedStateText() {
        return context.getString(R.string.admin_action_center_state_resolved);
    }

    public String getAuditStateText() {
        return context.getString(R.string.admin_action_center_state_audit);
    }

    public String getPriorityLabel(AdminActionNotificationPriority priority) {
        switch (priority) {
            case IMMEDIATE:
                return context.getString(R.string.admin_action_center_priority_immediate);
            case ACTION_REQUIRED:
                return context.getString(R.string.admin_action_center_priority_action_required);
            case ARCHIVED:
                return context.getString(R.string.admin_action_center_priority_archived);
            case MONITORING:
            default:
                return context.getString(R.string.admin_action_center_priority_monitoring);
        }
    }

    public String getMarkReadActionText() {
        return context.getString(R.string.admin_action_center_action_mark_read);
    }

    public String getResolveActionText() {
        return context.getString(R.string.admin_action_center_action_resolve);
    }

    public String getReopenActionText() {
        return context.getString(R.string.admin_action_center_action_reopen);
    }

    public String buildFilterButtonText(String label, int count) {
        return context.getString(R.string.admin_action_center_filter_button, label, count);
    }

    public String getFilterLabel(AdminActionCenterFilter filter) {
        switch (filter) {
            case UNREAD:
                return context.getString(R.string.admin_action_center_filter_unread);
            case UNRESOLVED:
                return context.getString(R.string.admin_action_center_filter_unresolved);
            case RESOLVED:
                return context.getString(R.string.admin_action_center_filter_resolved);
            case AUDIT:
                return context.getString(R.string.admin_action_center_filter_audit);
            case ALL:
            default:
                return context.getString(R.string.admin_action_center_filter_all);
        }
    }

    public String buildFilteredEmptyText(AdminActionCenterFilter filter) {
        return context.getString(
                R.string.admin_action_center_filtered_empty,
                getFilterLabel(filter)
        );
    }

    public String getEmptyStateText() {
        return context.getString(R.string.admin_action_center_empty);
    }

    public String buildMeta(AdminActionSourceType sourceType, String actorName, long createdAtMillis) {
        return context.getString(
                R.string.admin_action_center_meta,
                toSourceText(sourceType),
                TextUtils.isEmpty(actorName)
                        ? context.getString(R.string.admin_action_center_actor_fallback)
                        : actorName.trim(),
                formatTimestamp(createdAtMillis)
        );
    }

    public String formatTimestamp(long createdAtMillis) {
        if (createdAtMillis <= 0L) {
            return context.getString(R.string.admin_operation_action_pending);
        }
        return timestampFormatter.format(new Date(createdAtMillis));
    }

    public String resolveNotificationTitle(AdminActionNotification notification) {
        if (TextUtils.isEmpty(notification.getTitle())) {
            return toSourceText(notification.getSourceType());
        }
        return notification.getTitle().trim();
    }

    public String resolveNotificationBody(AdminActionNotification notification) {
        if (TextUtils.isEmpty(notification.getBody())) {
            return context.getString(R.string.admin_action_center_body_empty);
        }
        return summarize(notification.getBody());
    }

    public String resolveAuditTitle(AdminAuditLogEntry auditLog) {
        if (TextUtils.isEmpty(auditLog.getActionSummary())) {
            return toSourceText(auditLog.getSourceType());
        }
        return auditLog.getActionSummary().trim();
    }

    public String resolveAuditBody(AdminAuditLogEntry auditLog) {
        if (TextUtils.isEmpty(auditLog.getNote())) {
            return context.getString(R.string.admin_action_center_audit_note_empty);
        }
        return summarize(auditLog.getNote());
    }

    private String toSourceText(AdminActionSourceType sourceType) {
        switch (sourceType) {
            case SETTLEMENT:
                return context.getString(R.string.admin_action_center_source_settlement);
            case EMERGENCY:
                return context.getString(R.string.admin_action_center_source_emergency);
            case SUPPORT:
            default:
                return context.getString(R.string.admin_action_center_source_support);
        }
    }

    private String summarize(String value) {
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }
}
