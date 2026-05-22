package com.example.bodeul.ui.booking;

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
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 환자와 보호자가 현재 동행 위치 공유와 현장 메모를 한 화면에서 확인한다.
 */
public class BookingLiveLocationActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private BookingLiveLocationCoordinator coordinator;
    private BookingLiveLocationBinder binder;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private String requestId;
    private AppointmentRequestDetail currentDetail;

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, BookingLiveLocationActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_live_location);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        coordinator = new BookingLiveLocationCoordinator(this, new BookingPresentationFormatter(this));

        statePanel = findViewById(R.id.bookingLiveLocationStatePanel);
        contentContainer = findViewById(R.id.bookingLiveLocationContentContainer);
        progressBar = findViewById(R.id.progressBookingLiveLocation);

        binder = new BookingLiveLocationBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textBookingLiveLocationMode),
                findViewById(R.id.textBookingLiveLocationTitle),
                findViewById(R.id.textBookingLiveLocationSubtitle),
                findViewById(R.id.textBookingLiveLocationHeroBadge),
                findViewById(R.id.textBookingLiveLocationHeroTitle),
                findViewById(R.id.textBookingLiveLocationHeroBody),
                findViewById(R.id.textBookingLiveLocationStatusSectionTitle),
                findViewById(R.id.textBookingLiveLocationMemoSectionTitle),
                findViewById(R.id.textBookingLiveLocationMapSectionTitle),
                findViewById(R.id.textBookingLiveLocationMapSectionHelper),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationStatusLines),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationMemoLines),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationMapActions),
                (MaterialButton) findViewById(R.id.buttonBookingLiveLocationPrimary),
                new BookingLiveLocationMapActionBinder(this::openMapFallback)
        );

        findViewById(R.id.buttonBackBookingLiveLocation).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingLiveLocationPrimary).setOnClickListener(view -> openBookingStatus());
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reload();
    }

    private void reload() {
        if (TextUtils.isEmpty(requestId)) {
            showLoadErrorState(getString(R.string.booking_status_request_missing));
            return;
        }

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

                bookingRepository.getAppointmentRequestDetail(
                        result,
                        requestId,
                        new RepositoryCallback<AppointmentRequestDetail>() {
                            @Override
                            public void onSuccess(AppointmentRequestDetail detail) {
                                currentDetail = detail;
                                binder.bindScreen(coordinator.createScreenModel(
                                        result,
                                        detail,
                                        bookingRepository.isFirebaseBacked()
                                ));
                                contentContainer.setVisibility(View.VISIBLE);
                                hideBlockingState();
                                setLoading(false);
                            }

                            @Override
                            public void onError(String message) {
                                currentDetail = null;
                                setLoading(false);
                                showLoadErrorState(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void openMapFallback(BookingLiveLocationMapActionModel model) {
        if (!BookingLiveLocationMapFallbackLauncher.open(this, model)) {
            Toast.makeText(this, R.string.guide_map_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookingStatus() {
        if (currentDetail == null) {
            finish();
            return;
        }
        startActivity(BookingStatusActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()
        ));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            contentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.booking_live_location_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.booking_live_location_title)),
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
