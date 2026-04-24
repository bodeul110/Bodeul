package com.example.bodeul.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;

/**
 * 앱 시작 시 로그인 상태를 확인하고 첫 진입 화면을 결정하는 스플래시 화면이다.
 */
public class SplashActivity extends AppCompatActivity {
    private EntryFlowCoordinator entryFlowCoordinator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        entryFlowCoordinator = new EntryFlowCoordinator(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Android 12 이상에서는 시스템 시작 화면이 먼저 보이므로 추가 지연 없이 바로 분기한다.
        routeAfterSplash();
    }

    private void routeAfterSplash() {
        entryFlowCoordinator.resolveLaunchIntent(this::openNext);
    }

    private void openNext(Intent intent) {
        // 스플래시는 일회성 화면이므로 뒤로 돌아오지 않게 스택을 비운다.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
