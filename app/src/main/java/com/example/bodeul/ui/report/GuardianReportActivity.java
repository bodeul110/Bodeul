package com.example.bodeul.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 보호자 진행 화면의 인증, 로딩, 상세 이동만 담당한다.
 */
public class GuardianReportActivity extends AppCompatActivity implements GuardianReportEntryCardBinder.Listener {
    private AuthRepository authRepository;
    private GuardianReportRepository guardianReportRepository;
    private GuardianReportCoordinator guardianReportCoordinator;
    private GuardianReportDashboardBinder guardianReportDashboardBinder;

    private User currentUser;
    private boolean loading;

    private View guardianReportStatePanel;
    private View guardianReportContentContainer;
    private ProgressBar progressGuardianReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_report);

        authRepository = ServiceLocator.provideAuthRepository(this);
        guardianReportRepository = ServiceLocator.provideGuardianReportRepository(this);
        guardianReportCoordinator = new GuardianReportCoordinator(
                this,
                new GuardianReportPresentationFormatter(this)
        );

        guardianReportStatePanel = findViewById(R.id.guardianReportStatePanel);
        guardianReportContentContainer = findViewById(R.id.guardianReportContentContainer);
        progressGuardianReport = findViewById(R.id.progressGuardianReport);

        guardianReportDashboardBinder = new GuardianReportDashboardBinder(
                this,
                getLayoutInflater(),
                new GuardianReportEntryCardBinder(this, getLayoutInflater(), this),
                findViewById(R.id.textGuardianReportMode),
                findViewById(R.id.textGuardianReportGreeting),
                findViewById(R.id.textGuardianReportSummary),
                findViewById(R.id.textGuardianReportHighlightStatus),
                findViewById(R.id.textGuardianReportHighlightTitle),
                findViewById(R.id.textGuardianReportHighlightBody),
                (MaterialButton) findViewById(R.id.buttonGuardianReportHighlightAction),
                findViewById(R.id.guardianReportListContainer)
        );

        findViewById(R.id.buttonBackGuardianReport).setOnClickListener(view -> finish());
        findViewById(R.id.buttonGuardianReportRefresh).setOnClickListener(view -> refreshDashboard());
        bindEmptyState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                hideBlockingState();
                refreshDashboard();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadDashboard() {
        guardianReportRepository.getGuardianDashboard(currentUser, new RepositoryCallback<GuardianReportDashboard>() {
            @Override
            public void onSuccess(GuardianReportDashboard result) {
                setLoading(false);
                hideBlockingState();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptyState();
                showLoadErrorState(message);
            }
        });
    }

    private void refreshDashboard() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        setLoading(true);
        hideBlockingState();
        loadDashboard();
    }

    private void bindDashboard(@Nullable GuardianReportDashboard dashboard) {
        guardianReportDashboardBinder.bindScreen(
                dashboard == null
                        ? guardianReportCoordinator.createEmptyScreenModel(guardianReportRepository.isFirebaseBacked())
                        : guardianReportCoordinator.createScreenModel(
                                dashboard,
                                guardianReportRepository.isFirebaseBacked()
                        ),
                this
        );
    }

    private void bindEmptyState() {
        bindDashboard(null);
    }

    @Override
    public void onOpenRequestDetail(String requestId) {
        if (TextUtils.isEmpty(requestId)) {
            return;
        }
        startActivity(BookingStatusActivity.createIntent(this, requestId));
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressGuardianReport.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_guardian_report_title)),
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
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.feature_guardian_report_title)),
                body,
                getString(R.string.state_action_retry),
                view -> refreshDashboard(),
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
                guardianReportStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        guardianReportContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(guardianReportStatePanel);
        guardianReportContentContainer.setVisibility(View.VISIBLE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
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
}
