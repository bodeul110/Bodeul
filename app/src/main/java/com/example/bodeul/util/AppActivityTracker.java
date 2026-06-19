package com.example.bodeul.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 현재 화면에 보이는 액티비티를 추적해 포그라운드 알림 중복을 줄인다.
 */
public final class AppActivityTracker implements Application.ActivityLifecycleCallbacks {
    private static final AppActivityTracker INSTANCE = new AppActivityTracker();

    @Nullable
    private static volatile Class<?> currentResumedActivityClass;
    private static volatile boolean installed;

    private AppActivityTracker() {}

    public static void install(@NonNull Application application) {
        if (installed) {
            return;
        }
        application.registerActivityLifecycleCallbacks(INSTANCE);
        installed = true;
    }

    public static boolean isCurrentActivity(@NonNull Class<?> activityClass) {
        return activityClass.equals(currentResumedActivityClass);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentResumedActivityClass = activity.getClass();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity.getClass().equals(currentResumedActivityClass)) {
            currentResumedActivityClass = null;
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}
}
