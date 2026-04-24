package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 후속 알림이 어떤 채널로 어떻게 전달됐는지 기록한다.
 */
public final class AdminActionDeliveryRecord {
    private final String id;
    private final String notificationId;
    private final AdminActionSourceType sourceType;
    private final AdminActionDeliveryTrigger trigger;
    private final AdminActionDeliveryChannel channel;
    private final AdminActionDeliveryStatus status;
    private final String requestId;
    private final String inquiryId;
    private final String title;
    private final String body;
    private final String targetLabel;
    private final String note;
    private final long createdAtMillis;
    private final long processedAtMillis;
    private final int attemptCount;
    private final int maxAttemptCount;
    private final long confirmedAtMillis;
    private final long nextRetryAtMillis;
    private final long slaDueAtMillis;
    private final AdminActionDeliveryState state;
    private final AdminActionDeliveryPriority priority;
    private final List<AdminActionDeliveryFilterKey> filterKeys;
    private final AdminActionDeliverySlaStatus slaStatus;

    public AdminActionDeliveryRecord(
            String id,
            String notificationId,
            AdminActionSourceType sourceType,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String targetLabel,
            String note,
            long createdAtMillis,
            long processedAtMillis
    ) {
        this(
                id,
                notificationId,
                sourceType,
                trigger,
                channel,
                status,
                requestId,
                inquiryId,
                title,
                body,
                targetLabel,
                note,
                createdAtMillis,
                processedAtMillis,
                1,
                AdminActionDeliveryContract.resolveDefaultMaxAttempts(channel),
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null
        );
    }

    public AdminActionDeliveryRecord(
            String id,
            String notificationId,
            AdminActionSourceType sourceType,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String targetLabel,
            String note,
            long createdAtMillis,
            long processedAtMillis,
            int attemptCount,
            int maxAttemptCount,
            long confirmedAtMillis,
            long nextRetryAtMillis,
            long slaDueAtMillis,
            AdminActionDeliveryState state,
            AdminActionDeliveryPriority priority,
            List<AdminActionDeliveryFilterKey> filterKeys,
            AdminActionDeliverySlaStatus slaStatus
    ) {
        this.id = id == null ? "" : id;
        this.notificationId = notificationId == null ? "" : notificationId;
        this.sourceType = sourceType == null ? AdminActionSourceType.SUPPORT : sourceType;
        this.trigger = trigger == null ? AdminActionDeliveryTrigger.NOTIFICATION_CREATED : trigger;
        this.channel = channel == null ? AdminActionDeliveryChannel.OPERATIONS_FEED : channel;
        this.status = status == null ? AdminActionDeliveryStatus.SENT : status;
        this.requestId = requestId == null ? "" : requestId;
        this.inquiryId = inquiryId == null ? "" : inquiryId;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.targetLabel = targetLabel == null ? "" : targetLabel;
        this.note = note == null ? "" : note;
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
        this.processedAtMillis = Math.max(processedAtMillis, 0L);
        this.attemptCount = Math.max(attemptCount, 1);
        this.maxAttemptCount = Math.max(
                maxAttemptCount,
                AdminActionDeliveryContract.resolveDefaultMaxAttempts(this.channel)
        );
        this.confirmedAtMillis = Math.max(
                confirmedAtMillis,
                AdminActionDeliveryContract.resolveConfirmedAt(this.status, this.processedAtMillis)
        );
        this.nextRetryAtMillis = Math.max(
                nextRetryAtMillis,
                AdminActionDeliveryContract.resolveNextRetryAt(
                        this.channel,
                        this.status,
                        this.attemptCount,
                        this.maxAttemptCount,
                        this.processedAtMillis
                )
        );
        this.slaDueAtMillis = Math.max(
                slaDueAtMillis,
                AdminActionDeliveryContract.resolveSlaDueAt(
                        this.sourceType,
                        this.channel,
                        this.status,
                        this.createdAtMillis,
                        this.processedAtMillis
                )
        );
        this.state = state == null
                ? AdminActionDeliveryContract.resolveState(
                        this.channel,
                        this.status,
                        this.confirmedAtMillis,
                        this.nextRetryAtMillis,
                        this.slaDueAtMillis
                )
                : state;
        this.priority = priority == null
                ? AdminActionDeliveryContract.resolvePriority(this.sourceType, this.state)
                : priority;
        List<AdminActionDeliveryFilterKey> resolvedFilterKeys =
                filterKeys == null || filterKeys.isEmpty()
                        ? AdminActionDeliveryContract.resolveFilterKeys(this.state)
                        : Collections.unmodifiableList(new ArrayList<>(filterKeys));
        this.filterKeys = resolvedFilterKeys;
        this.slaStatus = slaStatus == null
                ? AdminActionDeliveryContract.resolveSlaStatus(this.state)
                : slaStatus;
    }

    public String getId() {
        return id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public AdminActionSourceType getSourceType() {
        return sourceType;
    }

    public AdminActionDeliveryTrigger getTrigger() {
        return trigger;
    }

    public AdminActionDeliveryChannel getChannel() {
        return channel;
    }

    public AdminActionDeliveryStatus getStatus() {
        return status;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInquiryId() {
        return inquiryId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getNote() {
        return note;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getProcessedAtMillis() {
        return processedAtMillis;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttemptCount() {
        return maxAttemptCount;
    }

    public long getConfirmedAtMillis() {
        return confirmedAtMillis;
    }

    public long getNextRetryAtMillis() {
        return nextRetryAtMillis;
    }

    public long getSlaDueAtMillis() {
        return slaDueAtMillis;
    }

    public AdminActionDeliveryState getState() {
        return state;
    }

    public AdminActionDeliveryPriority getPriority() {
        return priority;
    }

    public List<AdminActionDeliveryFilterKey> getFilterKeys() {
        return filterKeys;
    }

    public AdminActionDeliverySlaStatus getSlaStatus() {
        return slaStatus;
    }

    public boolean hasFilterKey(AdminActionDeliveryFilterKey filterKey) {
        return filterKey != null && filterKeys.contains(filterKey);
    }
}
