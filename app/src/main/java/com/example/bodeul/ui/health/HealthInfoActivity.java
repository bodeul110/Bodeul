package com.example.bodeul.ui.health;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.firebase.ClientSupportPushContract;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.ClientBookingHistoryActivity;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class HealthInfoActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private ClientSupportRepository clientSupportRepository;
    private HealthInfoCoordinator healthInfoCoordinator;
    private HealthInfoBinder healthInfoBinder;

    private View healthInfoStatePanel;
    private View healthInfoContentContainer;
    private ProgressBar progressHealthInfo;
    private String explicitRequestId;
    private User currentUser;
    private AppointmentRequestDetail currentDetail;
    private List<AppointmentRequest> currentRequests;
    private List<ClientSupportRequest> currentSupportRequests;
    private HealthInfoScreenModel currentScreenModel;
    private HealthInfoTab selectedTab = HealthInfoTab.SERVICE;
    private boolean supportRefreshReceiverRegistered;

    private final BroadcastReceiver supportRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    public static Intent createIntent(Context context) {
        return new Intent(context, HealthInfoActivity.class);
    }

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, HealthInfoActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_info);

        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        clientSupportRepository = ServiceLocator.provideClientSupportRepository(this);
        healthInfoCoordinator = new HealthInfoCoordinator(this, new BookingPresentationFormatter(this));
        explicitRequestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);

        healthInfoStatePanel = findViewById(R.id.healthInfoStatePanel);
        healthInfoContentContainer = findViewById(R.id.healthInfoContentContainer);
        progressHealthInfo = findViewById(R.id.progressHealthInfo);

        healthInfoBinder = new HealthInfoBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textHealthInfoMode),
                findViewById(R.id.textHealthInfoTitle),
                findViewById(R.id.textHealthInfoSubtitle),
                findViewById(R.id.textHealthInfoHeroBadge),
                findViewById(R.id.textHealthInfoHeroTitle),
                findViewById(R.id.textHealthInfoHeroBody),
                (MaterialButton) findViewById(R.id.buttonHealthInfoTabService),
                (MaterialButton) findViewById(R.id.buttonHealthInfoTabProfile),
                (MaterialButton) findViewById(R.id.buttonHealthInfoTabSupport),
                findViewById(R.id.cardHealthInfoService),
                findViewById(R.id.cardHealthInfoAccount),
                findViewById(R.id.cardHealthInfoProfile),
                findViewById(R.id.cardHealthInfoRequest),
                findViewById(R.id.cardHealthInfoHistory),
                findViewById(R.id.cardHealthInfoSupport),
                findViewById(R.id.textHealthInfoServiceSectionTitle),
                findViewById(R.id.textHealthInfoServiceSectionHelper),
                (LinearLayout) findViewById(R.id.layoutHealthInfoServiceLines),
                findViewById(R.id.textHealthInfoAccountSectionTitle),
                findViewById(R.id.textHealthInfoAccountSectionHelper),
                findViewById(R.id.textHealthInfoProfileSectionTitle),
                findViewById(R.id.textHealthInfoProfileSectionHelper),
                findViewById(R.id.textHealthInfoRequestSectionTitle),
                findViewById(R.id.textHealthInfoRequestSectionHelper),
                findViewById(R.id.textHealthInfoHistorySectionTitle),
                findViewById(R.id.textHealthInfoHistorySectionHelper),
                findViewById(R.id.textHealthInfoSupportSectionTitle),
                findViewById(R.id.textHealthInfoSupportSectionHelper),
                (LinearLayout) findViewById(R.id.layoutHealthInfoAccountLines),
                (LinearLayout) findViewById(R.id.layoutHealthInfoProfileLines),
                (LinearLayout) findViewById(R.id.layoutHealthInfoRequestLines),
                (LinearLayout) findViewById(R.id.layoutHealthInfoHistoryLines),
                (LinearLayout) findViewById(R.id.layoutHealthInfoSupportLines),
                (MaterialButton) findViewById(R.id.buttonHealthInfoHistory),
                (MaterialButton) findViewById(R.id.buttonHealthInfoBooking),
                (MaterialButton) findViewById(R.id.buttonHealthInfoBookingStatus),
                (MaterialButton) findViewById(R.id.buttonHealthInfoGuardianReport),
                (MaterialButton) findViewById(R.id.buttonHealthInfoSupport),
                (MaterialButton) findViewById(R.id.buttonHealthInfoPrimary)
        );

        findViewById(R.id.buttonBackHealthInfo).setOnClickListener(view -> finish());
        findViewById(R.id.buttonHealthInfoTabService).setOnClickListener(view -> selectTab(HealthInfoTab.SERVICE));
        findViewById(R.id.buttonHealthInfoTabProfile).setOnClickListener(view -> selectTab(HealthInfoTab.PROFILE));
        findViewById(R.id.buttonHealthInfoTabSupport).setOnClickListener(view -> selectTab(HealthInfoTab.SUPPORT));
        findViewById(R.id.buttonHealthInfoPrimary).setOnClickListener(view -> openBookingStatus());
        findViewById(R.id.buttonHealthInfoHistory).setOnClickListener(view -> openBookingHistory());
        findViewById(R.id.buttonHealthInfoBooking).setOnClickListener(view -> openBooking());
        findViewById(R.id.buttonHealthInfoBookingStatus).setOnClickListener(view -> openBookingStatus());
        findViewById(R.id.buttonHealthInfoGuardianReport).setOnClickListener(view -> openGuardianReport());
        findViewById(R.id.buttonHealthInfoSupport).setOnClickListener(view -> openSupport());
        healthInfoContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerSupportRefreshReceiver();
        reload();
    }

    @Override
    protected void onStop() {
        unregisterSupportRefreshReceiver();
        super.onStop();
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
                if (result.getRole() != UserRole.PATIENT && result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                currentUser = result;
                loadDetailTarget(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadDetailTarget(User user) {
        bookingRepository.getMyAppointmentRequests(user, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                currentRequests = result;
                AppointmentRequest request = resolvePrimaryRequest(result);
                if (explicitRequestId != null && !explicitRequestId.trim().isEmpty()) {
                    loadDetail(user, explicitRequestId);
                    return;
                }
                if (request == null) {
                    setLoading(false);
                    showEmptyState();
                    return;
                }
                loadDetail(user, request.getId());
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void loadDetail(User user, String requestId) {
        bookingRepository.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                currentDetail = result;
                loadSupportRequests(user, result);
            }

            @Override
            public void onError(String message) {
                currentDetail = null;
                currentScreenModel = null;
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void loadSupportRequests(User user, AppointmentRequestDetail detail) {
        clientSupportRepository.getClientSupportRequests(user, new RepositoryCallback<List<ClientSupportRequest>>() {
            @Override
            public void onSuccess(List<ClientSupportRequest> result) {
                currentSupportRequests = result;
                bindScreen(user, detail, result);
            }

            @Override
            public void onError(String message) {
                currentSupportRequests = java.util.Collections.emptyList();
                bindScreen(user, detail, currentSupportRequests);
            }
        });
    }

    private void bindScreen(
            User user,
            AppointmentRequestDetail detail,
            List<ClientSupportRequest> supportRequests
    ) {
        setLoading(false);
        hideBlockingState();
        currentScreenModel = healthInfoCoordinator.createScreenModel(
                user,
                detail,
                currentRequests,
                supportRequests,
                bookingRepository.isFirebaseBacked()
        );
        healthInfoBinder.bindScreen(currentScreenModel, selectedTab);
        healthInfoContentContainer.setVisibility(View.VISIBLE);
    }

    private void selectTab(HealthInfoTab tab) {
        selectedTab = tab;
        if (currentScreenModel != null) {
            healthInfoBinder.bindScreen(currentScreenModel, selectedTab);
        }
    }

    @Nullable
    private AppointmentRequest resolvePrimaryRequest(@Nullable List<AppointmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        for (AppointmentRequest request : requests) {
            if (request.getStatus() != AppointmentStatus.COMPLETED
                    && request.getStatus() != AppointmentStatus.CANCELED) {
                return request;
            }
        }
        return requests.get(0);
    }

    private void openBookingStatus() {
        if (currentScreenModel == null) {
            if (currentDetail == null) {
                openBooking();
                return;
            }
            startActivity(BookingStatusActivity.createIntent(this, currentDetail.getAppointmentRequest().getId()));
            return;
        }
        switch (currentScreenModel.getPrimaryActionType()) {
            case OPEN_GUARDIAN_REPORT:
                openGuardianReport();
                return;
            case OPEN_BOOKING:
                openBooking();
                return;
            case OPEN_BOOKING_STATUS:
            default:
                if (currentDetail == null) {
                    openBooking();
                    return;
                }
                startActivity(BookingStatusActivity.createIntent(this, currentDetail.getAppointmentRequest().getId()));
        }
    }

    private void openBooking() {
        startActivity(new Intent(this, BookingActivity.class));
    }

    private void openBookingHistory() {
        startActivity(new Intent(this, ClientBookingHistoryActivity.class));
    }

    private void openGuardianReport() {
        if (currentUser == null || currentUser.getRole() != UserRole.GUARDIAN) {
            return;
        }
        startActivity(new Intent(this, GuardianReportActivity.class));
    }

    private void openSupport() {
        startActivity(ClientSupportActivity.createIntent(
                this,
                currentDetail == null ? null : currentDetail.getAppointmentRequest().getId()
        ));
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

    private void registerSupportRefreshReceiver() {
        if (supportRefreshReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(ClientSupportPushContract.ACTION_CLIENT_SUPPORT_UPDATED);
        ContextCompat.registerReceiver(
                this,
                supportRefreshReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        supportRefreshReceiverRegistered = true;
    }

    private void unregisterSupportRefreshReceiver() {
        if (!supportRefreshReceiverRegistered) {
            return;
        }
        unregisterReceiver(supportRefreshReceiver);
        supportRefreshReceiverRegistered = false;
    }

    private void setLoading(boolean loading) {
        progressHealthInfo.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            healthInfoContentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.health_info_title)),
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

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.health_info_empty_title),
                getString(R.string.health_info_empty_body),
                getString(R.string.health_info_empty_action),
                view -> openBooking(),
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
                getString(R.string.state_load_error_title, getString(R.string.health_info_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reload(),
                getString(R.string.health_info_empty_action),
                view -> openBooking()
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
                healthInfoStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        healthInfoContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(healthInfoStatePanel);
    }
}
