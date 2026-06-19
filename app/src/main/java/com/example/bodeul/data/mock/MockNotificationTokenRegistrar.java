package com.example.bodeul.data.mock;

import com.example.bodeul.data.NotificationTokenRegistrar;

/**
 * 목 모드에서는 푸시 토큰을 서버에 저장하지 않는다.
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
}
