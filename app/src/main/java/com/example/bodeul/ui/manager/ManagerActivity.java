package com.example.bodeul.ui.manager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.RoleSelectionActivity;

/**
 * 매니저가 현재 동행 현황과 주요 빠른 작업을 확인하는 홈 화면이다.
 */
public class ManagerActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private User currentUser;

    private TextView textManagerMode;
    private TextView textManagerGreeting;
    private TextView textManagerCardTitle;
    private TextView textManagerCardBody;
    private TextView textAssignmentDetail;
    private TextView textAssignmentNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_home);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);

        textManagerMode = findViewById(R.id.textManagerMode);
        textManagerGreeting = findViewById(R.id.textManagerGreeting);
        textManagerCardTitle = findViewById(R.id.textManagerCardTitle);
        textManagerCardBody = findViewById(R.id.textManagerCardBody);
        textAssignmentDetail = findViewById(R.id.textAssignmentDetail);
        textAssignmentNote = findViewById(R.id.textAssignmentNote);

        findViewById(R.id.buttonOpenGuideFromHero).setOnClickListener(view -> openGuide());
        findViewById(R.id.cardActionGuide).setOnClickListener(view -> openGuide());
        findViewById(R.id.cardActionDocs).setOnClickListener(view ->
                Toast.makeText(this, R.string.toast_placeholder, Toast.LENGTH_SHORT).show());
        findViewById(R.id.cardActionSchedule).setOnClickListener(view ->
                Toast.makeText(this, R.string.toast_placeholder, Toast.LENGTH_SHORT).show());
        findViewById(R.id.cardActionLogout).setOnClickListener(view -> signOut());

        textManagerMode.setText(managerRepository.isFirebaseBacked()
                ? R.string.manager_home_mode_firebase
                : R.string.manager_home_mode_demo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result.getRole() != UserRole.MANAGER) {
                    Toast.makeText(ManagerActivity.this, R.string.toast_manager_only, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(ManagerActivity.this, MainActivity.class));
                    finish();
                    return;
                }

                currentUser = result;
                textManagerGreeting.setText(getString(R.string.manager_home_greeting, result.getName()));
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                openRoleSelection();
            }
        });
    }

    private void loadDashboard() {
        // 대시보드 데이터를 읽어 상단 카드와 배정 정보 영역을 동시에 갱신한다.
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                int totalSteps = result.getHospitalGuide().getSteps().size();
                textManagerCardTitle.setText(getString(
                        R.string.manager_home_card_title,
                        result.getSession().getCurrentStepOrder(),
                        totalSteps
                ));
                textManagerCardBody.setText(getString(
                        R.string.manager_home_card_body,
                        result.getPatient().getName(),
                        result.getAppointmentRequest().getHospitalName()
                ));
                textAssignmentDetail.setText(getString(
                        R.string.manager_assignment_detail,
                        result.getPatient().getName(),
                        result.getAppointmentRequest().getDepartmentName(),
                        result.getAppointmentRequest().getAppointmentAt()
                ));
                textAssignmentNote.setText(getString(
                        R.string.manager_assignment_note,
                        result.getAppointmentRequest().getSpecialNotes()
                ));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openGuide() {
        if (currentUser == null) {
            Toast.makeText(this, R.string.toast_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // 가이드 화면은 현재 로그인된 매니저 세션을 기준으로 동작한다.
        startActivity(new Intent(this, ManagerGuideActivity.class));
    }

    private void signOut() {
        authRepository.signOut();
        openRoleSelection();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
