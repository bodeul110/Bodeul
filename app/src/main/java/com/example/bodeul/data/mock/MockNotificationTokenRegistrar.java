package com.example.bodeul.data.mock;

import com.example.bodeul.data.NotificationTokenRegistrar;

/**
 * 목 모드에서는 알림 토큰을 실제로 저장하지 않는다.
 */
public final class MockNotificationTokenRegistrar implements NotificationTokenRegistrar {
    @Override
    public void syncCurrentUserToken() {
        // 목 모드에서는 동기화 대상이 없다.
    }

    @Override
    public void syncCurrentUserToken(String token) {
        // 목 모드에서는 동기화 대상이 없다.
    }

    @Override
    public void clearCurrentUserToken() {
        // 목 모드에서는 정리할 토큰이 없다.
    }
}
