package com.example.bodeul;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.admin.AdminActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.example.bodeul.ui.report.GuardianReportActivity;

/**
 * 로그인된 사용자가 각 기능 화면으로 이동하는 기본 홈 화면이다.
 */
public class MainActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private TextView textSignedInAs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authRepository = ServiceLocator.provideAuthRepository(this);
        textSignedInAs = findViewById(R.id.textSignedInAs);

        findViewById(R.id.openBooking).setOnClickListener(view -> open(BookingActivity.class));
        findViewById(R.id.openManager).setOnClickListener(view -> open(ManagerActivity.class));
        findViewById(R.id.openGuardianReport).setOnClickListener(view -> open(GuardianReportActivity.class));
        findViewById(R.id.openAdmin).setOnClickListener(view -> open(AdminActivity.class));
        findViewById(R.id.buttonSignOut).setOnClickListener(view -> {
            authRepository.signOut();
            openRoleSelection();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 현재 로그인 정보를 다시 확인해 홈 화면 상단 문구를 최신 상태로 맞춘다.
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                textSignedInAs.setText(getString(
                        R.string.home_signed_in_as,
                        result.getName(),
                        toRoleLabel(result.getRole())
                ));
            }

            @Override
            public void onError(String message) {
                openRoleSelection();
            }
        });
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void openRoleSelection() {
        // 로그아웃이나 세션 만료 시 역할 선택부터 다시 시작하도록 스택을 정리한다.
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String toRoleLabel(UserRole role) {
        // 홈 화면 문구에 표시할 역할명을 문자열 리소스로 변환한다.
        switch (role) {
            case MANAGER:
                return getString(R.string.login_role_manager);
            case GUARDIAN:
                return getString(R.string.login_role_guardian);
            case ADMIN:
                return getString(R.string.feature_admin_title);
            case PATIENT:
            default:
                return getString(R.string.login_role_patient);
        }
    }
}
