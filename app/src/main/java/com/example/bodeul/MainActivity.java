package com.example.bodeul;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.home.ClientHomeCoordinator;
import com.example.bodeul.ui.home.ClientHomeDashboard;
import com.example.bodeul.ui.home.ClientHomeDashboardBinder;
import com.example.bodeul.ui.home.ClientHomeNoticeProvider;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;

/**
 * 환자와 보호자가 서비스 신청, 진행 현황, 안내 정보를 한곳에서 보는 메인 홈 화면이다.
 */
public class MainActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ClientHomeCoordinator clientHomeCoordinator;

    private View homeStatePanel;
    private View homeContentContainer;
    private ProgressBar progressHome;
    private TextView textHomeMode;
    private ClientHomeDashboard currentDashboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authRepository = ServiceLocator.provideAuthRepository(this);
        clientHomeCoordinator = new ClientHomeCoordinator(
                ServiceLocator.provideBookingRepository(this),
                ServiceLocator.provideGuardianReportRepository(this),
                new ClientHomeNoticeProvider(),
                new AppointmentProgressComposer(this)
        );

        homeStatePanel = findViewById(R.id.homeStatePanel);
        homeContentContainer = findViewById(R.id.homeContentContainer);
        progressHome = findViewById(R.id.progressHome);
        textHomeMode = findViewById(R.id.textHomeMode);

        ClientHomeDashboardBinder dashboardBinder = new ClientHomeDashboardBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textHomeGreeting),
                findViewById(R.id.textHomeSubtitle),
                findViewById(R.id.textHomeHeroBadge),
                findViewById(R.id.textHomeHeroTitle),
                findViewById(R.id.textHomeHeroBody),
                findViewById(R.id.buttonHomeHeroPrimary),
                findViewById(R.id.textHomeProgressBadge),
                findViewById(R.id.textHomeProgressTitle),
                findViewById(R.id.textHomeProgressBody),
                findViewById(R.id.layoutHomeProgressStageContainer),
                findViewById(R.id.buttonHomeProgressDetail),
                findViewById(R.id.textActionSecondaryTitle),
                findViewById(R.id.textActionSecondaryBody),
                findViewById(R.id.textRecentBadge),
                findViewById(R.id.textRecentTitle),
                findViewById(R.id.textRecentBody),
                findViewById(R.id.buttonOpenRecent),
                findViewById(R.id.layoutHomeNoticeContainer)
        );
        findViewById(R.id.buttonHomeHeroPrimary).setOnClickListener(view -> openPrimaryAction());
        findViewById(R.id.buttonHomeProgressDetail).setOnClickListener(view -> openPrimaryAction());
        findViewById(R.id.cardActionBooking).setOnClickListener(view -> openBooking());
        findViewById(R.id.cardActionSecondary).setOnClickListener(view -> openSecondaryAction());
        findViewById(R.id.buttonOpenRecent).setOnClickListener(view -> openPrimaryAction());
        findViewById(R.id.buttonHomeSignOut).setOnClickListener(view -> signOut());

        EnvironmentModeBadgeHelper.bind(
                textHomeMode,
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(
                        this,
                        clientHomeCoordinator.isFirebaseBacked()
                )
        );
        homeContentContainer.setTag(dashboardBinder);
        homeContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reloadHome();
    }

    private void reloadHome() {
        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() == UserRole.MANAGER || result.getRole() == UserRole.ADMIN) {
                    redirectToRoleHome(result);
                    return;
                }
                if (result.getRole() != UserRole.PATIENT && result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                loadDashboard(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadDashboard(User currentUser) {
        clientHomeCoordinator.loadDashboard(currentUser, new RepositoryCallback<ClientHomeDashboard>() {
            @Override
            public void onSuccess(ClientHomeDashboard result) {
                currentDashboard = result;
                setLoading(false);
                hideBlockingState();
                getDashboardBinder().bindDashboard(result);
                homeContentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                currentDashboard = null;
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private ClientHomeDashboardBinder getDashboardBinder() {
        return (ClientHomeDashboardBinder) homeContentContainer.getTag();
    }

    private void openPrimaryAction() {
        if (currentDashboard == null || !currentDashboard.hasRequests()) {
            openBooking();
            return;
        }
        openPrimaryRequestDetail();
    }

    private void openSecondaryAction() {
        if (currentDashboard != null && currentDashboard.isGuardianUser()) {
            openGuardianReport();
            return;
        }
        openBooking();
    }

    private void openBooking() {
        startActivity(new Intent(this, BookingActivity.class));
    }

    private void openPrimaryRequestDetail() {
        if (currentDashboard == null || currentDashboard.getPrimaryRequest() == null) {
            openBooking();
            return;
        }
        startActivity(BookingStatusActivity.createIntent(
                this,
                currentDashboard.getPrimaryRequest().getId()
        ));
    }

    private void openGuardianReport() {
        startActivity(new Intent(this, GuardianReportActivity.class));
    }

    private void signOut() {
        authRepository.signOut();
        openRoleSelection();
    }

    private void redirectToRoleHome(User user) {
        Intent intent = AuthFlowRouter.createPostAuthIntent(this, user);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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

    private void setLoading(boolean loading) {
        progressHome.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            homeContentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.home_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
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
                getString(R.string.state_load_error_title, getString(R.string.home_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reloadHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            CharSequence primaryText,
            View.OnClickListener primaryListener,
            CharSequence secondaryText,
            View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                homeStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        homeContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(homeStatePanel);
    }
}
