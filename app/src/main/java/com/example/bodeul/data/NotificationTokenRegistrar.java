package com.example.bodeul.data;

/**
 * 현재 로그인 사용자 기준으로 앱 알림 토큰을 서버 문서와 동기화한다.
 */
public interface NotificationTokenRegistrar {
    void syncCurrentUserToken();

    void syncCurrentUserToken(String token);

    void clearCurrentUserToken();
}
