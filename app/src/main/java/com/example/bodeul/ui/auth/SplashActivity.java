package com.example.bodeul.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.bodeul.R;

/** Figma 브랜드 시작 화면을 보여준 뒤 기존 인증 진입 흐름으로 연결한다. */
@SuppressLint("CustomSplashScreen")
public final class SplashActivity extends AppCompatActivity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable launchResolutionTimeout = this::openSignedOutFallback;
    private final Runnable navigateToResolvedScreen = this::navigateToResolvedScreen;

    private EntryFlowCoordinator entryFlowCoordinator;
    private Intent resolvedIntent;
    private long startedAtMillis;
    private boolean navigationScheduled;
    private boolean hasNavigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAtMillis = SystemClock.elapsedRealtime();
        configureSystemBars();
        setContentView(R.layout.activity_splash);

        entryFlowCoordinator = new EntryFlowCoordinator(this);
        mainHandler.postDelayed(
                launchResolutionTimeout,
                SplashTimingPolicy.MAX_LAUNCH_RESOLUTION_WAIT_MILLIS
        );
        entryFlowCoordinator.resolveLaunchIntent(intent -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            mainHandler.post(() -> scheduleNavigation(intent));
        });
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
    }

    private void scheduleNavigation(@NonNull Intent intent) {
        if (hasNavigated || navigationScheduled || isFinishing() || isDestroyed()) {
            return;
        }
        resolvedIntent = intent;
        navigationScheduled = true;
        mainHandler.removeCallbacks(launchResolutionTimeout);
        long remainingMillis = SplashTimingPolicy.remainingDisplayMillis(
                startedAtMillis,
                SystemClock.elapsedRealtime()
        );
        mainHandler.postDelayed(navigateToResolvedScreen, remainingMillis);
    }

    private void openSignedOutFallback() {
        scheduleNavigation(entryFlowCoordinator.createSignedOutIntent());
    }

    private void navigateToResolvedScreen() {
        if (hasNavigated || resolvedIntent == null || isFinishing() || isDestroyed()) {
            return;
        }
        hasNavigated = true;
        resolvedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(resolvedIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
