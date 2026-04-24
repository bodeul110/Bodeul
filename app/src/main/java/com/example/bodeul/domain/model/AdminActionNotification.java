package com.example.bodeul.domain.model;

/**
 * 관리자 화면에서 바로 확인할 후속 알림 한 건을 나타낸다.
 */
public final class AdminActionNotification {
    private final String id;
    private final AdminActionSourceType sourceType;
    private final AdminActionNotificationLevel level;
    private final AdminActionNotificationState state;
    private final AdminActionNotificationPriority priority;
    private final java.util.List<AdminActionNotificationFilterKey> filterKeys;
    private final String requestId;
    private final String inquiryId;
    private final String title;
    private final String body;
    private final String actorName;
    private final long createdAtMillis;
    private final boolean read;
    private final long readAtMillis;
    private final boolean resolved;
    private final long resolvedAtMillis;
    private final String resolvedByName;

    public AdminActionNotification(
            String id,
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String actorName,
            long createdAtMillis
    ) {
        this(
                id,
                sourceType,
                level,
                requestId,
                inquiryId,
                title,
                body,
                actorName,
                createdAtMillis,
                false,
                0L,
                false,
                0L,
                "",
                null,
                null,
                null
            );
    }

    public AdminActionNotification(
            String id,
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String actorName,
            long createdAtMillis,
            boolean read,
            long readAtMillis,
            boolean resolved,
            long resolvedAtMillis,
            String resolvedByName
    ) {
        this(
                id,
                sourceType,
                level,
                requestId,
                inquiryId,
                title,
                body,
                actorName,
                createdAtMillis,
                read,
                readAtMillis,
                resolved,
                resolvedAtMillis,
                resolvedByName,
                null,
                null,
                null
        );
    }

    public AdminActionNotification(
            String id,
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String actorName,
            long createdAtMillis,
            boolean read,
            long readAtMillis,
            boolean resolved,
            long resolvedAtMillis,
            String resolvedByName,
            AdminActionNotificationState state,
            AdminActionNotificationPriority priority,
            java.util.List<AdminActionNotificationFilterKey> filterKeys
    ) {
        this.id = id == null ? "" : id;
        this.sourceType = sourceType == null ? AdminActionSourceType.SUPPORT : sourceType;
        this.level = level == null ? AdminActionNotificationLevel.INFO : level;
        AdminActionNotificationState resolvedState = state == null
                ? AdminActionNotificationContract.resolveState(read, resolved)
                : state;
        this.state = resolvedState;
        this.priority = priority == null
                ? AdminActionNotificationContract.resolvePriority(
                this.sourceType,
                this.level,
                resolvedState
        )
                : priority;
        java.util.List<AdminActionNotificationFilterKey> resolvedFilterKeys =
                filterKeys == null || filterKeys.isEmpty()
                        ? AdminActionNotificationContract.resolveFilterKeys(resolvedState)
                        : filterKeys;
        this.filterKeys = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(resolvedFilterKeys)
        );
        this.requestId = requestId == null ? "" : requestId;
        this.inquiryId = inquiryId == null ? "" : inquiryId;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.actorName = actorName == null ? "" : actorName;
        this.createdAtMillis = Math.max(createdAtMillis, 0L);
        this.read = read;
        this.readAtMillis = Math.max(readAtMillis, 0L);
        this.resolved = resolved;
        this.resolvedAtMillis = Math.max(resolvedAtMillis, 0L);
        this.resolvedByName = resolvedByName == null ? "" : resolvedByName;
    }

    public String getId() {
        return id;
    }

    public AdminActionSourceType getSourceType() {
        return sourceType;
    }

    public AdminActionNotificationLevel getLevel() {
        return level;
    }

    public AdminActionNotificationState getState() {
        return state;
    }

    public AdminActionNotificationPriority getPriority() {
        return priority;
    }

    public java.util.List<AdminActionNotificationFilterKey> getFilterKeys() {
        return filterKeys;
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

    public String getActorName() {
        return actorName;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public boolean isRead() {
        return read;
    }

    public long getReadAtMillis() {
        return readAtMillis;
    }

    public boolean isResolved() {
        return resolved;
    }

    public long getResolvedAtMillis() {
        return resolvedAtMillis;
    }

    public String getResolvedByName() {
        return resolvedByName;
    }

    public boolean hasFilterKey(AdminActionNotificationFilterKey filterKey) {
        return filterKey != null && filterKeys.contains(filterKey);
    }
}
