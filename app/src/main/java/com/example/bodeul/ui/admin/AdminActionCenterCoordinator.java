package com.example.bodeul.ui.admin;

import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationFilterKey;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionOverview;
import com.example.bodeul.domain.model.AdminActionNotificationPriority;
import com.example.bodeul.domain.model.AdminActionNotificationState;
import com.example.bodeul.domain.model.AdminAuditLogEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 후속 알림과 감사 로그를 관리자 화면용 타임라인 카드로 조합한다.
 */
public final class AdminActionCenterCoordinator {
    private final AdminActionCenterPresentationFormatter formatter;

    public AdminActionCenterCoordinator(AdminActionCenterPresentationFormatter formatter) {
        this.formatter = formatter;
    }

    public AdminActionCenterScreenModel createScreenModel(
            List<AdminActionNotification> notifications,
            List<AdminAuditLogEntry> auditLogs,
            AdminActionOverview actionOverview,
            AdminActionCenterFilter selectedFilter
    ) {
        AdminActionOverview resolvedOverview = actionOverview == null
                ? AdminActionContract.createOverview(
                notifications,
                auditLogs,
                java.util.Collections.emptyList()
        )
                : actionOverview;
        List<TimelineEntry> timelineEntries = new ArrayList<>();
        for (AdminActionNotification notification : notifications) {
            if (!matchesNotificationFilter(notification, selectedFilter)) {
                continue;
            }
            timelineEntries.add(new TimelineEntry(
                    notification.getCreatedAtMillis(),
                    notification.getPriority().getSortOrder(),
                    new AdminActionCenterEntryModel(
                            notification.getId(),
                            formatter.getNotificationBadgeText(),
                            notification.getLevel() == AdminActionNotificationLevel.WARNING
                                    ? AdminActionCenterTone.WARNING
                                    : AdminActionCenterTone.PRIMARY,
                            resolveNotificationStateText(notification),
                            resolveNotificationStateTone(notification),
                            formatter.getPriorityLabel(notification.getPriority()),
                            resolvePriorityTone(notification.getPriority()),
                            formatter.resolveNotificationTitle(notification),
                            formatter.resolveNotificationBody(notification),
                            formatter.buildMeta(
                                    notification.getSourceType(),
                                    notification.getActorName(),
                                    notification.getCreatedAtMillis()
                            ),
                            createNotificationActions(notification)
                    )
            ));
        }
        for (AdminAuditLogEntry auditLog : auditLogs) {
            if (selectedFilter != AdminActionCenterFilter.ALL
                    && selectedFilter != AdminActionCenterFilter.AUDIT) {
                continue;
            }
            timelineEntries.add(new TimelineEntry(
                    auditLog.getCreatedAtMillis(),
                    -1,
                    new AdminActionCenterEntryModel(
                            auditLog.getId(),
                            formatter.getAuditBadgeText(),
                            AdminActionCenterTone.SUCCESS,
                            formatter.getAuditStateText(),
                            AdminActionCenterTone.SUCCESS,
                            "",
                            AdminActionCenterTone.SUCCESS,
                            formatter.resolveAuditTitle(auditLog),
                            formatter.resolveAuditBody(auditLog),
                            formatter.buildMeta(
                                    auditLog.getSourceType(),
                                    auditLog.getActorName(),
                                    auditLog.getCreatedAtMillis()
                            ),
                            java.util.Collections.emptyList()
                    )
            ));
        }
        timelineEntries.sort((left, right) -> {
            if (left.priority != right.priority) {
                return Integer.compare(right.priority, left.priority);
            }
            return Long.compare(right.createdAtMillis, left.createdAtMillis);
        });

        List<AdminActionCenterEntryModel> entryModels = new ArrayList<>();
        for (TimelineEntry timelineEntry : timelineEntries) {
            entryModels.add(timelineEntry.entryModel);
        }
        return new AdminActionCenterScreenModel(
                formatter.buildSummary(
                        resolvedOverview.getNotificationCount(),
                        resolvedOverview.getUnreadNotificationCount(),
                        resolvedOverview.getUnresolvedNotificationCount(),
                        resolvedOverview.getAuditLogCount()
                ),
                selectedFilter == AdminActionCenterFilter.ALL
                        ? formatter.getEmptyStateText()
                        : formatter.buildFilteredEmptyText(selectedFilter),
                createFilterChips(resolvedOverview, selectedFilter),
                entryModels
        );
    }

    private List<AdminActionCenterFilterChipModel> createFilterChips(
            AdminActionOverview actionOverview,
            AdminActionCenterFilter selectedFilter
    ) {
        List<AdminActionCenterFilterChipModel> chips = new ArrayList<>();
        for (AdminActionCenterFilter filter : AdminActionCenterFilter.values()) {
            chips.add(new AdminActionCenterFilterChipModel(
                    filter,
                    formatter.buildFilterButtonText(
                            formatter.getFilterLabel(filter),
                            countByFilter(actionOverview, filter)
                    ),
                    filter == selectedFilter
            ));
        }
        return chips;
    }

    private int countByFilter(
            AdminActionOverview actionOverview,
            AdminActionCenterFilter filter
    ) {
        switch (filter) {
            case UNREAD:
                return actionOverview.getUnreadNotificationCount();
            case UNRESOLVED:
                return actionOverview.getUnresolvedNotificationCount();
            case RESOLVED:
                return actionOverview.getResolvedNotificationCount();
            case AUDIT:
                return actionOverview.getAuditLogCount();
            case ALL:
            default:
                return actionOverview.getNotificationCount() + actionOverview.getAuditLogCount();
        }
    }

    private boolean matchesNotificationFilter(
            AdminActionNotification notification,
            AdminActionCenterFilter filter
    ) {
        switch (filter) {
            case UNREAD:
                return notification.hasFilterKey(AdminActionNotificationFilterKey.UNREAD);
            case UNRESOLVED:
                return notification.hasFilterKey(AdminActionNotificationFilterKey.UNRESOLVED);
            case RESOLVED:
                return notification.hasFilterKey(AdminActionNotificationFilterKey.RESOLVED);
            case AUDIT:
                return false;
            case ALL:
            default:
                return true;
        }
    }

    private String resolveNotificationStateText(AdminActionNotification notification) {
        switch (notification.getState()) {
            case RESOLVED:
                return formatter.getResolvedStateText();
            case READ:
                return formatter.getReadStateText();
            case UNREAD:
            default:
                return formatter.getUnreadStateText();
        }
    }

    private AdminActionCenterTone resolveNotificationStateTone(AdminActionNotification notification) {
        switch (notification.getState()) {
            case RESOLVED:
                return AdminActionCenterTone.SUCCESS;
            case READ:
                return AdminActionCenterTone.PRIMARY;
            case UNREAD:
            default:
                return notification.getLevel() == AdminActionNotificationLevel.WARNING
                        ? AdminActionCenterTone.WARNING
                        : AdminActionCenterTone.PRIMARY;
        }
    }

    private List<AdminActionCenterActionModel> createNotificationActions(
            AdminActionNotification notification
    ) {
        List<AdminActionCenterActionModel> actions = new ArrayList<>();
        if (notification.getState() == AdminActionNotificationState.UNREAD) {
            actions.add(new AdminActionCenterActionModel(
                    AdminActionCenterActionType.MARK_READ,
                    formatter.getMarkReadActionText()
            ));
        }
        if (notification.getState() == AdminActionNotificationState.RESOLVED) {
            actions.add(new AdminActionCenterActionModel(
                    AdminActionCenterActionType.REOPEN,
                    formatter.getReopenActionText()
            ));
            return actions;
        }
        actions.add(new AdminActionCenterActionModel(
                AdminActionCenterActionType.MARK_RESOLVED,
                formatter.getResolveActionText()
        ));
        return actions;
    }

    private AdminActionCenterTone resolvePriorityTone(AdminActionNotificationPriority priority) {
        switch (priority) {
            case IMMEDIATE:
                return AdminActionCenterTone.WARNING;
            case ARCHIVED:
                return AdminActionCenterTone.SUCCESS;
            case ACTION_REQUIRED:
            case MONITORING:
            default:
                return AdminActionCenterTone.PRIMARY;
        }
    }

    private static final class TimelineEntry {
        private final long createdAtMillis;
        private final int priority;
        private final AdminActionCenterEntryModel entryModel;

        private TimelineEntry(
                long createdAtMillis,
                int priority,
                AdminActionCenterEntryModel entryModel
        ) {
            this.createdAtMillis = createdAtMillis;
            this.priority = priority;
            this.entryModel = entryModel;
        }
    }
}
