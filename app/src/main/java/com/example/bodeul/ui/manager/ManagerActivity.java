package com.example.bodeul.ui.manager;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 매니저 홈은 대기형 홈과 진행형 홈을 분기하고, 실제 뷰 바인딩은 전용 객체에 위임한다.
 */
public class ManagerActivity extends AppCompatActivity implements ManagerHomeDashboardBinder.Listener {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerHomeCoordinator managerHomeCoordinator;
    private ManagerHomeDashboardBinder managerHomeDashboardBinder;
    private ManagerQuickNoteDialogController quickNoteDialogController;
    private User currentUser;
    private ManagerHomeProfile managerHomeProfile = new ManagerHomeProfile("", "");
    @Nullable
    private ManagerDashboard currentDashboard;

    private View managerStatePanel;
    private View managerContentContainer;
    private MaterialButton buttonManagerHeroPrimary;
    private TextView textManagerSectionAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_home);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerHomeCoordinator = new ManagerHomeCoordinator(
                this,
                new ManagerHomePresentationFormatter(this)
        );
        quickNoteDialogController = new ManagerQuickNoteDialogController(this, getLayoutInflater());

        managerStatePanel = findViewById(R.id.managerStatePanel);
        managerContentContainer = findViewById(R.id.managerContentContainer);
        buttonManagerHeroPrimary = findViewById(R.id.buttonManagerHeroPrimary);
        textManagerSectionAction = findViewById(R.id.textManagerSectionAction);

        managerHomeDashboardBinder = new ManagerHomeDashboardBinder(
                this,
                LayoutInflater.from(this),
                this,
                findViewById(R.id.textManagerMode),
                findViewById(R.id.textManagerGreeting),
                findViewById(R.id.textManagerSubtitle),
                findViewById(R.id.textManagerHeroBadge),
                findViewById(R.id.textManagerHeroStatus),
                findViewById(R.id.textManagerHeroTitle),
                findViewById(R.id.textManagerHeroBody),
                buttonManagerHeroPrimary,
                findViewById(R.id.managerActionContainer),
                findViewById(R.id.textManagerSectionTitle),
                textManagerSectionAction,
                findViewById(R.id.managerPromoScroll),
                findViewById(R.id.managerPromoContainer),
                findViewById(R.id.cardManagerLiveFeed),
                findViewById(R.id.viewManagerLiveFeedBanner),
                findViewById(R.id.textManagerLiveBadge),
                findViewById(R.id.textManagerLiveTime),
                findViewById(R.id.textManagerLiveTitle),
                findViewById(R.id.textManagerLiveSubtitle),
                findViewById(R.id.textManagerLiveNote),
                findViewById(R.id.textManagerLiveFooter)
        );

        buttonManagerHeroPrimary.setOnClickListener(view -> handleHeroPrimaryAction());
        textManagerSectionAction.setOnClickListener(view -> openServiceIntroDialog());
        findViewById(R.id.buttonManagerLogout).setOnClickListener(view -> signOut());
        findViewById(R.id.navManagerHome).setOnClickListener(view -> renderHome());
        findViewById(R.id.navManagerHistory).setOnClickListener(view -> openHistoryScreen());
        findViewById(R.id.navManagerGuide).setOnClickListener(view -> openGuide());
        findViewById(R.id.navManagerProfile).setOnClickListener(view -> openProfileScreen());
        bindModeOnly();
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
                currentDashboard = null;
                managerHomeProfile = new ManagerHomeProfile("", "");
                hideBlockingState();
                renderHome();
                loadManagerHomeProfile();
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                showAuthState();
            }
        });
    }

    private void bindModeOnly() {
        TextView textManagerMode = findViewById(R.id.textManagerMode);
        EnvironmentModeBadgeHelper.bind(
                textManagerMode,
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(
                        this,
                        managerRepository.isFirebaseBacked()
                )
        );
    }

    private void loadManagerHomeProfile() {
        if (currentUser == null) {
            return;
        }
        managerRepository.getManagerHomeProfile(currentUser.getId(), new RepositoryCallback<ManagerHomeProfile>() {
            @Override
            public void onSuccess(ManagerHomeProfile result) {
                managerHomeProfile = result;
                renderHome();
            }

            @Override
            public void onError(String message) {
                managerHomeProfile = new ManagerHomeProfile("", "");
                renderHome();
                if (!TextUtils.isEmpty(message)) {
                    Toast.makeText(ManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadDashboard() {
        if (currentUser == null) {
            return;
        }

        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                currentDashboard = result;
                hideBlockingState();
                renderHome();
            }

            @Override
            public void onError(String message) {
                if (isNoActiveSession(message)) {
                    currentDashboard = null;
                    hideBlockingState();
                    renderHome();
                    return;
                }
                showLoadErrorState(message);
            }
        });
    }

    private void renderHome() {
        if (currentUser == null) {
            bindModeOnly();
            return;
        }

        ManagerHomeScreenModel screenModel = managerHomeCoordinator.createScreenModel(
                currentUser,
                managerHomeProfile,
                currentDashboard,
                managerRepository.isFirebaseBacked()
        );
        managerHomeDashboardBinder.bindScreen(screenModel);
    }

    private void handleHeroPrimaryAction() {
        if (currentUser == null) {
            showAuthState();
            return;
        }

        if (currentDashboard == null) {
            loadDashboard();
            Toast.makeText(this, R.string.manager_home_matching_refresh_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        openGuide();
    }

    private void openGuide() {
        if (currentUser == null) {
            showAuthState();
            return;
        }

        if (currentDashboard == null) {
            Toast.makeText(this, R.string.manager_home_empty_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(new Intent(this, ManagerGuideActivity.class));
    }

    private void openHistoryScreen() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        startActivity(ManagerHistoryActivity.createIntent(this));
    }

    private void openSupportScreen() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        startActivity(ManagerSupportActivity.createIntent(this));
    }

    private void openProfileScreen() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        startActivity(ManagerProfileActivity.createIntent(this));
    }

    @Override
    public void onQuickActionSelected(ManagerHomeActionType actionType) {
        switch (actionType) {
            case DOCUMENT:
                openDocumentRegistration();
                return;
            case SCHEDULE:
                openQuickActionDialog(ManagerQuickNoteType.SCHEDULE);
                return;
            case HISTORY:
                openHistoryScreen();
                return;
            case SUPPORT:
            default:
                openSupportScreen();
        }
    }

    private void openDocumentRegistration() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        startActivity(ManagerDocumentRegistrationActivity.createIntent(this));
    }

    private void openQuickActionDialog(ManagerQuickNoteType actionType) {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        quickNoteDialogController.show(
                actionType,
                actionType == ManagerQuickNoteType.DOCUMENT
                        ? managerHomeProfile.getDocumentSummary()
                        : managerHomeProfile.getAvailabilitySummary(),
                (noteType, value, dialog) -> saveQuickAction(noteType, value, dialog)
        );
    }

    private void saveQuickAction(ManagerQuickNoteType actionType, String value, AlertDialog dialog) {
        RepositoryCallback<ManagerHomeProfile> callback = new RepositoryCallback<ManagerHomeProfile>() {
            @Override
            public void onSuccess(ManagerHomeProfile result) {
                managerHomeProfile = result;
                renderHome();
                Toast.makeText(
                        ManagerActivity.this,
                        actionType == ManagerQuickNoteType.DOCUMENT
                                ? R.string.manager_action_docs_saved
                                : R.string.manager_action_schedule_saved,
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        };

        if (actionType == ManagerQuickNoteType.DOCUMENT) {
            managerRepository.saveManagerDocumentSummary(currentUser.getId(), value, callback);
            return;
        }
        managerRepository.saveManagerAvailabilitySummary(currentUser.getId(), value, callback);
    }

    private void openServiceIntroDialog() {
        if (currentDashboard != null) {
            openGuide();
            return;
        }

        String message = getString(R.string.manager_home_service_card_body)
                + "\n\n"
                + getString(R.string.manager_home_service_card_secondary_body);
        new AlertDialog.Builder(this)
                .setTitle(R.string.manager_home_service_title)
                .setMessage(message)
                .setPositiveButton(R.string.permission_guide_confirm, null)
                .show();
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
                    loadManagerHomeProfile();
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
