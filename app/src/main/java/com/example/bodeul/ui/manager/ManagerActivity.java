package com.example.bodeul.ui.manager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * 매니저가 현재 동행 현황과 주요 빠른 작업을 확인하는 홈 화면이다.
 */
public class ManagerActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private User currentUser;
    private boolean hasActiveSession;

    private TextView textManagerMode;
    private TextView textManagerGreeting;
    private TextView textManagerCardTitle;
    private TextView textManagerCardBody;
    private TextView textAssignmentDetail;
    private TextView textAssignmentNote;
    private View managerStatePanel;
    private View managerContentContainer;
    private MaterialButton buttonOpenGuideFromHero;
    private MaterialCardView cardActionGuide;

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
        managerStatePanel = findViewById(R.id.managerStatePanel);
        managerContentContainer = findViewById(R.id.managerContentContainer);
        buttonOpenGuideFromHero = findViewById(R.id.buttonOpenGuideFromHero);
        cardActionGuide = findViewById(R.id.cardActionGuide);

        buttonOpenGuideFromHero.setOnClickListener(view -> openGuide());
        cardActionGuide.setOnClickListener(view -> openGuide());
        findViewById(R.id.cardActionDocs).setOnClickListener(view ->
                Toast.makeText(this, R.string.toast_placeholder, Toast.LENGTH_SHORT).show());
        findViewById(R.id.cardActionSchedule).setOnClickListener(view ->
                Toast.makeText(this, R.string.toast_placeholder, Toast.LENGTH_SHORT).show());
        findViewById(R.id.cardActionLogout).setOnClickListener(view -> signOut());

        textManagerMode.setText(managerRepository.isFirebaseBacked()
                ? R.string.manager_home_mode_firebase
                : R.string.manager_home_mode_demo);
        bindEmptyDashboard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.MANAGER) {
                    showPermissionState();
                    return;
                }

                currentUser = result;
                hideBlockingState();
                textManagerGreeting.setText(getString(R.string.manager_home_greeting, result.getName()));
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                showAuthState();
            }
        });
    }

    private void loadDashboard() {
        // 대시보드 데이터를 읽어 상단 카드와 배정 정보 영역을 동시에 갱신한다.
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                hideBlockingState();
                hasActiveSession = true;
                setGuideAccessEnabled(true);
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
                if (isNoActiveSession(message)) {
                    hideBlockingState();
                    bindEmptyDashboard();
                    return;
                }
                bindEmptyDashboard();
                showLoadErrorState(message);
            }
        });
    }

    private void openGuide() {
        if (currentUser == null) {
            showAuthState();
            return;
        }

        if (!hasActiveSession) {
            Toast.makeText(this, R.string.manager_home_empty_toast, Toast.LENGTH_SHORT).show();
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

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void bindEmptyDashboard() {
        // 세션이 없는 경우에도 홈 화면은 정상 상태로 보이도록 기본 안내 문구를 채운다.
        hasActiveSession = false;
        setGuideAccessEnabled(false);
        textManagerCardTitle.setText(R.string.manager_home_empty_card_title);
        textManagerCardBody.setText(R.string.manager_home_empty_card_body);
        textAssignmentDetail.setText(R.string.manager_assignment_empty_detail);
        textAssignmentNote.setText(R.string.manager_assignment_empty_note);
    }

    private void setGuideAccessEnabled(boolean enabled) {
        buttonOpenGuideFromHero.setEnabled(enabled);
        buttonOpenGuideFromHero.setAlpha(enabled ? 1f : 0.5f);
        cardActionGuide.setEnabled(enabled);
        cardActionGuide.setAlpha(enabled ? 1f : 0.5f);
    }

    private boolean isNoActiveSession(String message) {
        return ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_manager_home_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (message != null && !message.trim().isEmpty()) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.feature_manager_home_title)),
                body,
                getString(R.string.state_action_retry),
                view -> {
                    if (currentUser == null) {
                        showAuthState();
                        return;
                    }
                    hideBlockingState();
                    loadDashboard();
                },
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                managerStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(managerStatePanel);
        managerContentContainer.setVisibility(View.VISIBLE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
