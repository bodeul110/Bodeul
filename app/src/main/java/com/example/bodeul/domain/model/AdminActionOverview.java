package com.example.bodeul.domain.model;

/**
 * 관리자 액션센터와 전달 기록 섹션이 함께 참조하는 공용 요약 응답 모델이다.
 */
public final class AdminActionOverview {
    private final int notificationCount;
    private final int unreadNotificationCount;
    private final int unresolvedNotificationCount;
    private final int resolvedNotificationCount;
    private final int auditLogCount;
    private final int deliveryCount;
    private final int pendingDeliveryCount;
    private final int followUpDeliveryCount;
    private final int completedDeliveryCount;
    private final int appPushDeliveryCount;
    private final int operationsFeedDeliveryCount;

    public AdminActionOverview(
            int notificationCount,
            int unreadNotificationCount,
            int unresolvedNotificationCount,
            int resolvedNotificationCount,
            int auditLogCount,
            int deliveryCount,
            int pendingDeliveryCount,
            int followUpDeliveryCount,
            int completedDeliveryCount,
            int appPushDeliveryCount,
            int operationsFeedDeliveryCount
    ) {
        this.notificationCount = Math.max(notificationCount, 0);
        this.unreadNotificationCount = Math.max(unreadNotificationCount, 0);
        this.unresolvedNotificationCount = Math.max(unresolvedNotificationCount, 0);
        this.resolvedNotificationCount = Math.max(resolvedNotificationCount, 0);
        this.auditLogCount = Math.max(auditLogCount, 0);
        this.deliveryCount = Math.max(deliveryCount, 0);
        this.pendingDeliveryCount = Math.max(pendingDeliveryCount, 0);
        this.followUpDeliveryCount = Math.max(followUpDeliveryCount, 0);
        this.completedDeliveryCount = Math.max(completedDeliveryCount, 0);
        this.appPushDeliveryCount = Math.max(appPushDeliveryCount, 0);
        this.operationsFeedDeliveryCount = Math.max(operationsFeedDeliveryCount, 0);
    }

    public int getNotificationCount() {
        return notificationCount;
    }

    public int getUnreadNotificationCount() {
        return unreadNotificationCount;
    }

    public int getUnresolvedNotificationCount() {
        return unresolvedNotificationCount;
    }

    public int getResolvedNotificationCount() {
        return resolvedNotificationCount;
    }

    public int getAuditLogCount() {
        return auditLogCount;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public int getPendingDeliveryCount() {
        return pendingDeliveryCount;
    }

    public int getFollowUpDeliveryCount() {
        return followUpDeliveryCount;
    }

    public int getCompletedDeliveryCount() {
        return completedDeliveryCount;
    }

    public int getAppPushDeliveryCount() {
        return appPushDeliveryCount;
    }

    public int getOperationsFeedDeliveryCount() {
        return operationsFeedDeliveryCount;
    }
}
