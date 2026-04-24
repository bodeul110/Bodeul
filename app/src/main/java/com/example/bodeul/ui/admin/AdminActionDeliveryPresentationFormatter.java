package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminActionDeliveryChannel;
import com.example.bodeul.domain.model.AdminActionDeliveryPriority;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionDeliverySlaStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryState;
import com.example.bodeul.domain.model.AdminActionDeliveryStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryTrigger;
import com.example.bodeul.domain.model.AdminActionSourceType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 운영 알림 전송 현황 섹션의 문구 규칙을 담당한다.
 */
public final class AdminActionDeliveryPresentationFormatter {
    private final Context context;
    private final SimpleDateFormat timestampFormatter;

    public AdminActionDeliveryPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String buildSummary(
            int totalCount,
            int pendingCount,
            int followUpCount,
            int completedCount,
            int pushCount,
            int feedCount
    ) {
        if (totalCount == 0) {
            return context.getString(R.string.admin_action_delivery_summary_empty);
        }
        return context.getString(
                R.string.admin_action_delivery_summary,
                totalCount,
                pendingCount,
                followUpCount,
                completedCount,
                pushCount,
                feedCount
        );
    }

    public String getChannelText(AdminActionDeliveryChannel channel) {
        switch (channel) {
            case APP_PUSH:
                return context.getString(R.string.admin_action_delivery_channel_push);
            case OPERATIONS_FEED:
            default:
                return context.getString(R.string.admin_action_delivery_channel_feed);
        }
    }

    public String getStatusText(AdminActionDeliveryStatus status) {
        switch (status) {
            case CONFIRMED:
                return context.getString(R.string.admin_action_delivery_status_confirmed);
            case SKIPPED:
                return context.getString(R.string.admin_action_delivery_status_skipped);
            case FAILED:
                return context.getString(R.string.admin_action_delivery_status_failed);
            case SENT:
            default:
                return context.getString(R.string.admin_action_delivery_status_sent);
        }
    }

    public String getStateText(AdminActionDeliveryState state) {
        switch (state) {
            case PENDING_CONFIRMATION:
                return context.getString(R.string.admin_action_delivery_state_pending);
            case FOLLOW_UP_REQUIRED:
                return context.getString(R.string.admin_action_delivery_state_follow_up);
            case SKIPPED:
                return context.getString(R.string.admin_action_delivery_state_skipped);
            case DELIVERED:
            default:
                return context.getString(R.string.admin_action_delivery_state_delivered);
        }
    }

    public String buildTitle(AdminActionDeliveryRecord record) {
        if (!TextUtils.isEmpty(record.getTitle())) {
            return record.getTitle();
        }
        return getSourceText(record.getSourceType());
    }

    public String buildBody(AdminActionDeliveryRecord record) {
        String primaryText;
        if (!TextUtils.isEmpty(record.getNote())) {
            primaryText = record.getNote();
        } else if (!TextUtils.isEmpty(record.getBody())) {
            primaryText = record.getBody();
        } else {
            primaryText = context.getString(R.string.admin_action_delivery_body_empty);
        }

        String detailText = buildDetailText(record);
        if (TextUtils.isEmpty(detailText)) {
            return primaryText;
        }
        return primaryText + "\n" + detailText;
    }

    public String buildMeta(AdminActionDeliveryRecord record) {
        return context.getString(
                R.string.admin_action_delivery_meta,
                getPriorityText(record.getPriority()),
                getTriggerText(record.getTrigger()),
                context.getString(
                        R.string.admin_action_delivery_attempt,
                        record.getAttemptCount(),
                        record.getMaxAttemptCount()
                ),
                formatTimestamp(resolveReferenceTimestamp(record))
        );
    }

    private String buildDetailText(AdminActionDeliveryRecord record) {
        if (record.getNextRetryAtMillis() > 0L) {
            return context.getString(
                    R.string.admin_action_delivery_detail_retry,
                    formatTimestamp(record.getNextRetryAtMillis()),
                    getSlaText(record.getSlaStatus())
            );
        }
        if (record.getConfirmedAtMillis() > 0L) {
            return context.getString(
                    R.string.admin_action_delivery_detail_confirmed,
                    formatTimestamp(record.getConfirmedAtMillis()),
                    getSlaText(record.getSlaStatus())
            );
        }
        if (record.getState() == AdminActionDeliveryState.PENDING_CONFIRMATION
                && record.getSlaDueAtMillis() > 0L) {
            return context.getString(
                    R.string.admin_action_delivery_detail_pending,
                    formatTimestamp(record.getSlaDueAtMillis()),
                    getSlaText(record.getSlaStatus())
            );
        }
        return context.getString(
                R.string.admin_action_delivery_detail_completed,
                getSlaText(record.getSlaStatus())
        );
    }

    private String getSourceText(AdminActionSourceType sourceType) {
        switch (sourceType) {
            case SETTLEMENT:
                return context.getString(R.string.admin_action_delivery_source_settlement);
            case EMERGENCY:
                return context.getString(R.string.admin_action_delivery_source_emergency);
            case SUPPORT:
            default:
                return context.getString(R.string.admin_action_delivery_source_support);
        }
    }

    private String getTriggerText(AdminActionDeliveryTrigger trigger) {
        switch (trigger) {
            case NOTIFICATION_READ:
                return context.getString(R.string.admin_action_delivery_trigger_read);
            case NOTIFICATION_RESOLVED:
                return context.getString(R.string.admin_action_delivery_trigger_resolved);
            case NOTIFICATION_REOPENED:
                return context.getString(R.string.admin_action_delivery_trigger_reopened);
            case NOTIFICATION_CREATED:
            default:
                return context.getString(R.string.admin_action_delivery_trigger_created);
        }
    }

    private String getPriorityText(AdminActionDeliveryPriority priority) {
        switch (priority) {
            case IMMEDIATE:
                return context.getString(R.string.admin_action_delivery_priority_immediate);
            case ACTION_REQUIRED:
                return context.getString(R.string.admin_action_delivery_priority_action_required);
            case ARCHIVED:
                return context.getString(R.string.admin_action_delivery_priority_archived);
            case MONITORING:
            default:
                return context.getString(R.string.admin_action_delivery_priority_monitoring);
        }
    }

    private String getSlaText(AdminActionDeliverySlaStatus status) {
        switch (status) {
            case ATTENTION_REQUIRED:
                return context.getString(R.string.admin_action_delivery_sla_attention);
            case ON_TRACK:
                return context.getString(R.string.admin_action_delivery_sla_on_track);
            case COMPLETED:
            default:
                return context.getString(R.string.admin_action_delivery_sla_completed);
        }
    }

    private long resolveReferenceTimestamp(AdminActionDeliveryRecord record) {
        if (record.getConfirmedAtMillis() > 0L) {
            return record.getConfirmedAtMillis();
        }
        if (record.getProcessedAtMillis() > 0L) {
            return record.getProcessedAtMillis();
        }
        return record.getCreatedAtMillis();
    }

    private String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.admin_operation_action_pending);
        }
        return timestampFormatter.format(new Date(timestampMillis));
    }
}
