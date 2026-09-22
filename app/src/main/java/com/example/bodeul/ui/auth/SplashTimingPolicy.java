package com.example.bodeul.ui.auth;

/** Figma 시작 화면의 최소 노출 시간과 인증 조회 대기 시간을 관리한다. */
final class SplashTimingPolicy {
    static final long MIN_DISPLAY_MILLIS = 900L;
    static final long MAX_LAUNCH_RESOLUTION_WAIT_MILLIS = 3_000L;

    private SplashTimingPolicy() {
    }

    static long remainingDisplayMillis(long startedAtMillis, long nowMillis) {
        long elapsedMillis = Math.max(0L, nowMillis - startedAtMillis);
        return Math.max(0L, MIN_DISPLAY_MILLIS - elapsedMillis);
    }
}
