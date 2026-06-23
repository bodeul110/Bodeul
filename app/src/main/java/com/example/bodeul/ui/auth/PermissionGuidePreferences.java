package com.example.bodeul.ui.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 권한 안내 온보딩 노출 여부를 저장하는 간단한 환경 설정 객체다.
 */
public final class PermissionGuidePreferences {
    private static final String PREF_NAME = "permission_guide";
    private static final String KEY_COMPLETED = "completed";
    private static final String KEY_NOTIFICATION_PROMPT_COMPLETED = "notification_prompt_completed";

    private final SharedPreferences preferences;

    public PermissionGuidePreferences(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public boolean hasCompletedGuide() {
        return preferences.getBoolean(KEY_COMPLETED, false);
    }

    public void markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply();
    }

    public boolean hasCompletedNotificationPrompt() {
        return preferences.getBoolean(KEY_NOTIFICATION_PROMPT_COMPLETED, false);
    }

    public void markNotificationPromptCompleted() {
        preferences.edit().putBoolean(KEY_NOTIFICATION_PROMPT_COMPLETED, true).apply();
    }

    public void markNotificationPromptPending() {
        preferences.edit().putBoolean(KEY_NOTIFICATION_PROMPT_COMPLETED, false).apply();
    }
}
