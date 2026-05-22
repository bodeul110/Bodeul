package com.example.bodeul.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class CompanionChatActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private ManagerRepository managerRepository;
    private CompanionChatCoordinator coordinator;
    private CompanionChatBinder binder;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private TextInputEditText inputMessage;
    private User currentUser;
    private String requestId;
    @Nullable
    private AppointmentRequestDetail currentDetail;
    @Nullable
    private ManagerDashboard currentDashboard;

    public static Intent createIntent(Context context) {
        return new Intent(context, CompanionChatActivity.class);
    }

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, CompanionChatActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion_chat);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        coordinator = new CompanionChatCoordinator(this);

        statePanel = findViewById(R.id.companionChatStatePanel);
        contentContainer = findViewById(R.id.companionChatContentContainer);
        progressBar = findViewById(R.id.progressCompanionChat);
        inputMessage = findViewById(R.id.inputCompanionChatMessage);

        binder = new CompanionChatBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textCompanionChatMode),
                findViewById(R.id.textCompanionChatTitle),
                findViewById(R.id.textCompanionChatSubtitle),
                findViewById(R.id.textCompanionChatHeroBadge),
                findViewById(R.id.textCompanionChatHeroTitle),
                findViewById(R.id.textCompanionChatHeroBody),
                findViewById(R.id.textCompanionChatSectionTitle),
                findViewById(R.id.textCompanionChatEmptyBody),
                (LinearLayout) findViewById(R.id.layoutCompanionChatMessages),
                (TextInputLayout) findViewById(R.id.layoutCompanionChatInput),
                (MaterialButton) findViewById(R.id.buttonCompanionChatSend)
        );

        findViewById(R.id.buttonBackCompanionChat).setOnClickListener(view -> finish());
        findViewById(R.id.buttonCompanionChatSend).setOnClickListener(view -> sendMessage());
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reload();
    }

    private void reload() {
        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                currentUser = result;
                if (result.getRole() == UserRole.MANAGER) {
                    loadManagerDashboard(result);
                    return;
                }
                if (result.getRole() == UserRole.PATIENT || result.getRole() == UserRole.GUARDIAN) {
                    if (TextUtils.isEmpty(requestId)) {
                        setLoading(false);
                        showLoadErrorState(getString(R.string.booking_status_request_missing));
                        return;
                    }
                    loadBookingDetail(result);
                    return;
                }
                setLoading(false);
                showPermissionState();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadBookingDetail(User user) {
        bookingRepository.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                currentDetail = result;
                currentDashboard = null;
                binder.bindScreen(coordinator.createForBooking(
                        user,
                        result,
                        bookingRepository.isFirebaseBacked()
                ));
                setLoading(false);
                hideBlockingState();
                contentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                currentDetail = null;
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void loadManagerDashboard(User user) {
        managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                currentDashboard = result;
                currentDetail = null;
                binder.bindScreen(coordinator.createForManager(
                        user,
                        result,
                        managerRepository.isFirebaseBacked()
                ));
                setLoading(false);
                hideBlockingState();
                contentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                currentDashboard = null;
                setLoading(false);
                if (ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message)) {
                    showEmptyState();
                    return;
                }
                showLoadErrorState(message);
            }
        });
    }

    private void sendMessage() {
        if (currentUser == null) {
            return;
        }

        String message = inputMessage.getText() == null ? "" : inputMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, R.string.companion_chat_empty_message, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        if (currentUser.getRole() == UserRole.MANAGER) {
            managerRepository.sendCompanionChatMessage(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
                @Override
                public void onSuccess(ManagerDashboard result) {
                    inputMessage.setText("");
                    currentDashboard = result;
                    binder.bindScreen(coordinator.createForManager(
                            currentUser,
                            result,
                            managerRepository.isFirebaseBacked()
                    ));
                    setLoading(false);
                    hideBlockingState();
                    contentContainer.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(CompanionChatActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        bookingRepository.sendCompanionChatMessage(currentUser, requestId, message, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                inputMessage.setText("");
                currentDetail = result;
                binder.bindScreen(coordinator.createForBooking(
                        currentUser,
                        result,
                        bookingRepository.isFirebaseBacked()
                ));
                setLoading(false);
                hideBlockingState();
                contentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(CompanionChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.companion_chat_title)),
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

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.companion_chat_empty_title),
                getString(R.string.companion_chat_empty_session_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
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
                getString(R.string.state_load_error_title, getString(R.string.companion_chat_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reload(),
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
                statePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(statePanel);
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
