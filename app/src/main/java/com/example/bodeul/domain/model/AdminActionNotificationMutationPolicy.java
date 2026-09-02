package com.example.bodeul.domain.model;

/**
 * 관리자 후속 알림에서 현재 MVP가 새 상태 변경을 허용하는 운영 축을 정의한다.
 */
public final class AdminActionNotificationMutationPolicy {
    private AdminActionNotificationMutationPolicy() {
    }

    public static boolean canMutate(AdminActionSourceType sourceType) {
        return sourceType == AdminActionSourceType.SETTLEMENT
                || sourceType == AdminActionSourceType.SUPPORT;
    }
}
