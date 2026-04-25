package com.example.bodeul.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.card.MaterialCardView;

/**
 * 사용자의 서비스 유형을 먼저 고르게 해 이후 로그인 흐름을 단순화하는 화면이다.
 */
public class RoleSelectionActivity extends AppCompatActivity {
    private static final int ADMIN_ENTRY_REQUIRED_TAPS = 5;
    private static final long ADMIN_ENTRY_RESET_WINDOW_MS = 1_500L;

    private ImageView logoView;
    private MaterialCardView managerCard;
    private MaterialCardView patientCard;
    private RoleOptionCardBinder managerCardBinder;
    private RoleOptionCardBinder patientCardBinder;
    private UserRole selectedRoleHint = UserRole.MANAGER;
    private long lastAdminEntryTapAtMillis;
    private int adminEntryTapCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        logoView = findViewById(R.id.imageRoleLogo);
        managerCard = findViewById(R.id.cardManagerType);
        patientCard = findViewById(R.id.cardPatientType);
        managerCardBinder = new RoleOptionCardBinder(
                this,
                managerCard,
                findViewById(R.id.textManagerCheck),
                findViewById(R.id.textManagerAction)
        );
        patientCardBinder = new RoleOptionCardBinder(
                this,
                patientCard,
                findViewById(R.id.textPatientCheck),
                findViewById(R.id.textPatientAction)
        );

        logoView.setOnClickListener(view -> handleAdminEntryTap());
        managerCard.setOnClickListener(view -> selectRole(UserRole.MANAGER));
        patientCard.setOnClickListener(view -> selectRole(UserRole.PATIENT));
        findViewById(R.id.buttonContinueRole).setOnClickListener(view -> {
            // 선택한 역할 힌트를 로그인 화면으로 넘겨 초기 상태를 맞춘다.
            Intent intent = LoginActivity.createIntent(this, selectedRoleHint);
            startActivity(intent);
        });

        selectRole(UserRole.MANAGER);
    }

    private void selectRole(UserRole roleHint) {
        selectedRoleHint = roleHint;

        // 선택된 카드만 강조해 이후 로그인 대상 역할을 분명하게 보여준다.
        managerCardBinder.render(roleHint == UserRole.MANAGER);
        patientCardBinder.render(roleHint == UserRole.PATIENT);
    }

    private void handleAdminEntryTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAdminEntryTapAtMillis > ADMIN_ENTRY_RESET_WINDOW_MS) {
            adminEntryTapCount = 0;
        }

        adminEntryTapCount++;
        lastAdminEntryTapAtMillis = now;
        if (adminEntryTapCount < ADMIN_ENTRY_REQUIRED_TAPS) {
            return;
        }

        adminEntryTapCount = 0;
        Toast.makeText(this, R.string.toast_admin_entry_opened, Toast.LENGTH_SHORT).show();
        startActivity(LoginActivity.createIntent(this, UserRole.ADMIN));
    }
}
