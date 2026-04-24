package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 후속 알림 전달 기록의 상태, SLA, 재시도 규칙을 한 곳에서 계산한다.
 */
public final class AdminActionDeliveryContract {
    private static final long EMERGENCY_CONFIRMATION_WINDOW_MILLIS = 15L * 60L * 1000L;
    private static final long DEFAULT_CONFIRMATION_WINDOW_MILLIS = 60L * 60L * 1000L;
    private static final long RETRY_INTERVAL_MILLIS = 10L * 60L * 1000L;

    private AdminActionDeliveryContract() {
    }

    public static int resolveDefaultMaxAttempts(AdminActionDeliveryChannel channel) {
        return channel == AdminActionDeliveryChannel.APP_PUSH ? 3 : 1;
    }

    public static long resolveConfirmedAt(
            AdminActionDeliveryStatus status,
            long processedAtMillis
    ) {
        if (status == AdminActionDeliveryStatus.CONFIRMED) {
            return Math.max(processedAtMillis, 0L);
        }
        return 0L;
    }

    public static long resolveNextRetryAt(
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            int attemptCount,
            int maxAttemptCount,
            long processedAtMillis
    ) {
        if (channel != AdminActionDeliveryChannel.APP_PUSH
                || status != AdminActionDeliveryStatus.FAILED
                || attemptCount >= maxAttemptCount
                || processedAtMillis <= 0L) {
            return 0L;
        }
        return processedAtMillis + (RETRY_INTERVAL_MILLIS * Math.max(attemptCount, 1));
    }

    public static long resolveSlaDueAt(
            AdminActionSourceType sourceType,
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            long createdAtMillis,
            long processedAtMillis
    ) {
        if (channel != AdminActionDeliveryChannel.APP_PUSH) {
            return 0L;
        }
        long baseTime = Math.max(processedAtMillis, createdAtMillis);
        if (status == AdminActionDeliveryStatus.CONFIRMED
                || status == AdminActionDeliveryStatus.SKIPPED) {
            return baseTime;
        }
        long confirmationWindow = sourceType == AdminActionSourceType.EMERGENCY
                ? EMERGENCY_CONFIRMATION_WINDOW_MILLIS
                : DEFAULT_CONFIRMATION_WINDOW_MILLIS;
        return baseTime + confirmationWindow;
    }

    public static AdminActionDeliveryState resolveState(
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            long confirmedAtMillis,
            long nextRetryAtMillis,
            long slaDueAtMillis
    ) {
        if (status == AdminActionDeliveryStatus.SKIPPED) {
            return AdminActionDeliveryState.SKIPPED;
        }
        if (status == AdminActionDeliveryStatus.FAILED) {
            return AdminActionDeliveryState.FOLLOW_UP_REQUIRED;
        }
        if (status == AdminActionDeliveryStatus.CONFIRMED || confirmedAtMillis > 0L) {
            return AdminActionDeliveryState.DELIVERED;
        }
        if (channel == AdminActionDeliveryChannel.APP_PUSH) {
            if (nextRetryAtMillis > 0L) {
                return AdminActionDeliveryState.FOLLOW_UP_REQUIRED;
            }
            if (slaDueAtMillis > 0L && System.currentTimeMillis() > slaDueAtMillis) {
                return AdminActionDeliveryState.FOLLOW_UP_REQUIRED;
            }
            return AdminActionDeliveryState.PENDING_CONFIRMATION;
        }
        return AdminActionDeliveryState.DELIVERED;
    }

    public static AdminActionDeliverySlaStatus resolveSlaStatus(
            AdminActionDeliveryState state
    ) {
        switch (state) {
            case FOLLOW_UP_REQUIRED:
                return AdminActionDeliverySlaStatus.ATTENTION_REQUIRED;
            case PENDING_CONFIRMATION:
                return AdminActionDeliverySlaStatus.ON_TRACK;
            case DELIVERED:
            case SKIPPED:
            default:
                return AdminActionDeliverySlaStatus.COMPLETED;
        }
    }

    public static AdminActionDeliveryPriority resolvePriority(
            AdminActionSourceType sourceType,
            AdminActionDeliveryState state
    ) {
        switch (state) {
            case FOLLOW_UP_REQUIRED:
                return sourceType == AdminActionSourceType.EMERGENCY
                        ? AdminActionDeliveryPriority.IMMEDIATE
                        : AdminActionDeliveryPriority.ACTION_REQUIRED;
            case PENDING_CONFIRMATION:
                return sourceType == AdminActionSourceType.EMERGENCY
                        ? AdminActionDeliveryPriority.IMMEDIATE
                        : AdminActionDeliveryPriority.MONITORING;
            case SKIPPED:
                return AdminActionDeliveryPriority.ARCHIVED;
            case DELIVERED:
            default:
                return AdminActionDeliveryPriority.MONITORING;
        }
    }

    public static List<AdminActionDeliveryFilterKey> resolveFilterKeys(
            AdminActionDeliveryState state
    ) {
        List<AdminActionDeliveryFilterKey> filterKeys = new ArrayList<>();
        switch (state) {
            case PENDING_CONFIRMATION:
                filterKeys.add(AdminActionDeliveryFilterKey.PENDING_CONFIRMATION);
                break;
            case FOLLOW_UP_REQUIRED:
                filterKeys.add(AdminActionDeliveryFilterKey.FOLLOW_UP_REQUIRED);
                break;
            case DELIVERED:
            case SKIPPED:
            default:
                filterKeys.add(AdminActionDeliveryFilterKey.COMPLETED);
                break;
        }
        return Collections.unmodifiableList(filterKeys);
    }
}
