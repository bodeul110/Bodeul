package com.example.bodeul.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;

/**
 * 앱 시작 시 로그인 상태를 확인하고 첫 진입 화면을 결정하는 스플래시 화면이다.
 */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Android 12 이상에서는 시스템 시작 화면이 먼저 보이므로 추가 지연 없이 바로 분기한다.
        routeAfterSplash();
    }

    private void routeAfterSplash() {
        AuthRepository authRepository = ServiceLocator.provideAuthRepository(this);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                // 로그인된 사용자는 프로필 보완 여부까지 확인한 뒤 다음 화면으로 보낸다.
                openNext(AuthFlowRouter.createPostAuthIntent(SplashActivity.this, result));
            }

            @Override
            public void onError(String message) {
                // 로그인 정보가 없으면 역할 선택부터 시작한다.
                openNext(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            }
        });
    }

    private void openNext(Intent intent) {
        // 스플래시는 일회성 화면이므로 뒤로 돌아오지 않게 스택을 비운다.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
