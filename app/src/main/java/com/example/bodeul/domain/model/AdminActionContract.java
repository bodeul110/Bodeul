package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 후속 처리 응답의 정렬 규칙과 공용 요약 값을 한 곳에서 맞춘다.
 */
public final class AdminActionContract {
    private AdminActionContract() {
    }

    public static List<AdminActionNotification> sortNotifications(
            List<AdminActionNotification> notifications
    ) {
        List<AdminActionNotification> sorted = notifications == null
                ? new ArrayList<>()
                : new ArrayList<>(notifications);
        sorted.sort((left, right) -> {
            int priorityCompare = Integer.compare(
                    right.getPriority().getSortOrder(),
                    left.getPriority().getSortOrder()
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis());
        });
        return Collections.unmodifiableList(sorted);
    }

    public static List<AdminAuditLogEntry> sortAuditLogs(List<AdminAuditLogEntry> auditLogs) {
        List<AdminAuditLogEntry> sorted = auditLogs == null
                ? new ArrayList<>()
                : new ArrayList<>(auditLogs);
        sorted.sort((left, right) -> Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
        return Collections.unmodifiableList(sorted);
    }

    public static List<AdminActionDeliveryRecord> sortDeliveries(
            List<AdminActionDeliveryRecord> deliveries
    ) {
        List<AdminActionDeliveryRecord> sorted = deliveries == null
                ? new ArrayList<>()
                : new ArrayList<>(deliveries);
        sorted.sort((left, right) -> {
            int priorityCompare = Integer.compare(
                    right.getPriority().getSortOrder(),
                    left.getPriority().getSortOrder()
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            long rightTimestamp = Math.max(right.getProcessedAtMillis(), right.getCreatedAtMillis());
            long leftTimestamp = Math.max(left.getProcessedAtMillis(), left.getCreatedAtMillis());
            return Long.compare(rightTimestamp, leftTimestamp);
        });
        return Collections.unmodifiableList(sorted);
    }

    public static AdminActionOverview createOverview(
            List<AdminActionNotification> notifications,
            List<AdminAuditLogEntry> auditLogs,
            List<AdminActionDeliveryRecord> deliveries
    ) {
        int unreadNotificationCount = 0;
        int unresolvedNotificationCount = 0;
        int resolvedNotificationCount = 0;
        if (notifications != null) {
            for (AdminActionNotification notification : notifications) {
                if (notification.hasFilterKey(AdminActionNotificationFilterKey.UNREAD)) {
                    unreadNotificationCount++;
                }
                if (notification.hasFilterKey(AdminActionNotificationFilterKey.UNRESOLVED)) {
                    unresolvedNotificationCount++;
                }
                if (notification.hasFilterKey(AdminActionNotificationFilterKey.RESOLVED)) {
                    resolvedNotificationCount++;
                }
            }
        }

        int pendingDeliveryCount = 0;
        int followUpDeliveryCount = 0;
        int completedDeliveryCount = 0;
        int appPushDeliveryCount = 0;
        int operationsFeedDeliveryCount = 0;
        if (deliveries != null) {
            for (AdminActionDeliveryRecord delivery : deliveries) {
                if (delivery.hasFilterKey(AdminActionDeliveryFilterKey.PENDING_CONFIRMATION)) {
                    pendingDeliveryCount++;
                }
                if (delivery.hasFilterKey(AdminActionDeliveryFilterKey.FOLLOW_UP_REQUIRED)) {
                    followUpDeliveryCount++;
                }
                if (delivery.hasFilterKey(AdminActionDeliveryFilterKey.COMPLETED)) {
                    completedDeliveryCount++;
                }
                if (delivery.getChannel() == AdminActionDeliveryChannel.APP_PUSH) {
                    appPushDeliveryCount++;
                } else if (delivery.getChannel() == AdminActionDeliveryChannel.OPERATIONS_FEED) {
                    operationsFeedDeliveryCount++;
                }
            }
        }

        return new AdminActionOverview(
                notifications == null ? 0 : notifications.size(),
                unreadNotificationCount,
                unresolvedNotificationCount,
                resolvedNotificationCount,
                auditLogs == null ? 0 : auditLogs.size(),
                deliveries == null ? 0 : deliveries.size(),
                pendingDeliveryCount,
                followUpDeliveryCount,
                completedDeliveryCount,
                appPushDeliveryCount,
                operationsFeedDeliveryCount
        );
    }
}
