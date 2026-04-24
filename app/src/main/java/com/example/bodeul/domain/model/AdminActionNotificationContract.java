package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 후속 알림의 상태, 우선순위, 필터 태그 규칙을 한 곳에서 계산한다.
 */
public final class AdminActionNotificationContract {
    private AdminActionNotificationContract() {
    }

    public static AdminActionNotificationState resolveState(boolean read, boolean resolved) {
        if (resolved) {
            return AdminActionNotificationState.RESOLVED;
        }
        if (read) {
            return AdminActionNotificationState.READ;
        }
        return AdminActionNotificationState.UNREAD;
    }

    public static AdminActionNotificationPriority resolvePriority(
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            AdminActionNotificationState state
    ) {
        if (state == AdminActionNotificationState.RESOLVED) {
            return AdminActionNotificationPriority.ARCHIVED;
        }
        if (state == AdminActionNotificationState.UNREAD
                && (sourceType == AdminActionSourceType.EMERGENCY
                || level == AdminActionNotificationLevel.WARNING)) {
            return AdminActionNotificationPriority.IMMEDIATE;
        }
        if (state == AdminActionNotificationState.UNREAD) {
            return AdminActionNotificationPriority.ACTION_REQUIRED;
        }
        return AdminActionNotificationPriority.MONITORING;
    }

    public static List<AdminActionNotificationFilterKey> resolveFilterKeys(
            AdminActionNotificationState state
    ) {
        List<AdminActionNotificationFilterKey> filterKeys = new ArrayList<>();
        switch (state) {
            case RESOLVED:
                filterKeys.add(AdminActionNotificationFilterKey.RESOLVED);
                break;
            case READ:
                filterKeys.add(AdminActionNotificationFilterKey.UNRESOLVED);
                break;
            case UNREAD:
            default:
                filterKeys.add(AdminActionNotificationFilterKey.UNREAD);
                filterKeys.add(AdminActionNotificationFilterKey.UNRESOLVED);
                break;
        }
        return Collections.unmodifiableList(filterKeys);
    }
}
