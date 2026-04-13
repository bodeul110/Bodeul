package com.example.bodeul.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.card.MaterialCardView;

/**
 * 사용자의 서비스 유형을 먼저 고르게 해 이후 로그인 흐름을 단순화하는 화면이다.
 */
public class RoleSelectionActivity extends AppCompatActivity {
    private MaterialCardView managerCard;
    private MaterialCardView patientCard;
    private TextView managerSelected;
    private TextView patientSelected;
    private UserRole selectedRoleHint = UserRole.MANAGER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        managerCard = findViewById(R.id.cardManagerType);
        patientCard = findViewById(R.id.cardPatientType);
        managerSelected = findViewById(R.id.textManagerSelected);
        patientSelected = findViewById(R.id.textPatientSelected);

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
        boolean managerSelectedState = roleHint == UserRole.MANAGER;
        managerCard.setStrokeWidth(managerSelectedState ? 2 : 1);
        patientCard.setStrokeWidth(managerSelectedState ? 1 : 2);
        managerCard.setStrokeColor(getColor(managerSelectedState
                ? R.color.bodeul_primary
                : R.color.bodeul_outline));
        patientCard.setStrokeColor(getColor(managerSelectedState
                ? R.color.bodeul_outline
                : R.color.bodeul_primary));

        managerSelected.setText(managerSelectedState
                ? R.string.role_selected
                : R.string.role_unselected);
        patientSelected.setText(managerSelectedState
                ? R.string.role_unselected
                : R.string.role_selected);

        managerSelected.setTextColor(getColor(managerSelectedState
                ? R.color.bodeul_primary
                : R.color.bodeul_text_secondary));
        patientSelected.setTextColor(getColor(managerSelectedState
                ? R.color.bodeul_text_secondary
                : R.color.bodeul_primary));
    }
}
