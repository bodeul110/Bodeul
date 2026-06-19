package com.example.bodeul.ui.support;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 환자와 보호자의 문의 작성과 최근 문의 내역 조회를 담당한다.
 */
public final class ClientSupportActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private ClientSupportRepository clientSupportRepository;
    private ClientSupportCoordinator clientSupportCoordinator;
    private ClientSupportBinder clientSupportBinder;

    private User currentUser;
    private AppointmentRequestDetail currentRequestDetail;
    private ClientSupportCategory selectedCategory = ClientSupportCategory.RESERVATION;
    private String requestId;

    private View clientSupportStatePanel;
    private View clientSupportContentContainer;
    private ProgressBar progressClientSupport;
    private MaterialButtonToggleGroup supportCategoryGroup;
    private TextInputLayout layoutSupportTitle;
    private TextInputLayout layoutSupportBody;
    private TextInputEditText inputSupportTitle;
    private TextInputEditText inputSupportBody;

    public static Intent createIntent(Context context) {
        return new Intent(context, ClientSupportActivity.class);
    }

    public static Intent createIntent(Context context, @Nullable String requestId) {
        Intent intent = new Intent(context, ClientSupportActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_support);

        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        clientSupportRepository = ServiceLocator.provideClientSupportRepository(this);
        clientSupportCoordinator = new ClientSupportCoordinator(this, new BookingPresentationFormatter(this));
        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);

        clientSupportStatePanel = findViewById(R.id.clientSupportStatePanel);
        clientSupportContentContainer = findViewById(R.id.clientSupportContentContainer);
        progressClientSupport = findViewById(R.id.progressClientSupport);
        supportCategoryGroup = findViewById(R.id.clientSupportCategoryGroup);
        layoutSupportTitle = findViewById(R.id.layoutClientSupportTitle);
        layoutSupportBody = findViewById(R.id.layoutClientSupportBody);
        inputSupportTitle = findViewById(R.id.inputClientSupportTitle);
        inputSupportBody = findViewById(R.id.inputClientSupportBody);

        clientSupportBinder = new ClientSupportBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textClientSupportMode),
                findViewById(R.id.textClientSupportHeroBadge),
                findViewById(R.id.textClientSupportHeroTitle),
                findViewById(R.id.textClientSupportHeroBody),
                findViewById(R.id.textClientSupportRequestSummary),
                findViewById(R.id.textClientSupportLatestSummary),
                findViewById(R.id.clientSupportRequestContainer),
                findViewById(R.id.textClientSupportEmpty)
        );

        findViewById(R.id.buttonBackClientSupport).setOnClickListener(view -> finish());
        findViewById(R.id.buttonClientSupportSubmit).setOnClickListener(view -> submitInquiry());
        supportCategoryGroup.check(R.id.buttonClientSupportCategoryReservation);
        supportCategoryGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                selectedCategory = toCategory(checkedId);
            }
        });
        clientSupportContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadSupportScreen();
    }

    private void loadSupportScreen() {
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
                loadRequestContextAndInquiries();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadRequestContextAndInquiries() {
        currentRequestDetail = null;
        if (TextUtils.isEmpty(requestId)) {
            bindInquiries();
            return;
        }
        bookingRepository.getAppointmentRequestDetail(currentUser, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                currentRequestDetail = result;
                bindInquiries();
            }

            @Override
            public void onError(String message) {
                currentRequestDetail = null;
                bindInquiries();
            }
        });
    }

    private void bindInquiries() {
        clientSupportRepository.getClientSupportRequests(currentUser, new RepositoryCallback<List<ClientSupportRequest>>() {
            @Override
            public void onSuccess(List<ClientSupportRequest> result) {
                hideBlockingState();
                clientSupportContentContainer.setVisibility(View.VISIBLE);
                clientSupportBinder.bindScreen(clientSupportCoordinator.createScreenModel(
                        currentUser,
                        currentRequestDetail,
                        result,
                        clientSupportRepository.isFirebaseBacked()
                ));
                setLoading(false);
                markUnreadResponsesRead(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(ClientSupportActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void markUnreadResponsesRead(List<ClientSupportRequest> requests) {
        if (!hasUnreadResponses(requests) || currentUser == null) {
            return;
        }
        clientSupportRepository.markClientSupportResponsesRead(
                currentUser,
                new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );
    }

    private boolean hasUnreadResponses(List<ClientSupportRequest> requests) {
        for (ClientSupportRequest request : requests) {
            if (request.hasUnreadResponse()) {
                return true;
            }
        }
        return false;
    }

    private void submitInquiry() {
        if (currentUser == null) {
            showAuthState();
            return;
        }

        String title = inputSupportTitle.getText() == null
                ? ""
                : inputSupportTitle.getText().toString().trim();
        String body = inputSupportBody.getText() == null
                ? ""
                : inputSupportBody.getText().toString().trim();

        boolean valid = true;
        if (TextUtils.isEmpty(title)) {
            layoutSupportTitle.setError(getString(R.string.error_required_field));
            valid = false;
        } else {
            layoutSupportTitle.setError(null);
        }
        if (TextUtils.isEmpty(body)) {
            layoutSupportBody.setError(getString(R.string.error_required_field));
            valid = false;
        } else {
            layoutSupportBody.setError(null);
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        clientSupportRepository.submitClientSupportRequest(
                currentUser,
                currentRequestDetail == null ? "" : currentRequestDetail.getAppointmentRequest().getId(),
                selectedCategory,
                title,
                body,
                new RepositoryCallback<List<ClientSupportRequest>>() {
                    @Override
                    public void onSuccess(List<ClientSupportRequest> result) {
                        inputSupportTitle.setText("");
                        inputSupportBody.setText("");
                        clientSupportBinder.bindScreen(clientSupportCoordinator.createScreenModel(
                                currentUser,
                                currentRequestDetail,
                                result,
                                clientSupportRepository.isFirebaseBacked()
                        ));
                        setLoading(false);
                        Toast.makeText(
                                ClientSupportActivity.this,
                                R.string.client_support_submit_done,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ClientSupportActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private ClientSupportCategory toCategory(int checkedId) {
        if (checkedId == R.id.buttonClientSupportCategoryProgress) {
            return ClientSupportCategory.PROGRESS;
        }
        if (checkedId == R.id.buttonClientSupportCategoryReport) {
            return ClientSupportCategory.REPORT;
        }
        if (checkedId == R.id.buttonClientSupportCategorySettlement) {
            return ClientSupportCategory.SETTLEMENT;
        }
        if (checkedId == R.id.buttonClientSupportCategoryOther) {
            return ClientSupportCategory.OTHER;
        }
        return ClientSupportCategory.RESERVATION;
    }

    private void setLoading(boolean loading) {
        progressClientSupport.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_client_support_title)),
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

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            String badge,
            String title,
            String body,
            String primaryActionLabel,
            View.OnClickListener primaryActionListener,
            @Nullable String secondaryActionLabel,
            @Nullable View.OnClickListener secondaryActionListener
    ) {
        clientSupportContentContainer.setVisibility(View.GONE);
        StatePanelHelper.show(
                clientSupportStatePanel,
                tone,
                badge,
                title,
                body,
                primaryActionLabel,
                primaryActionListener,
                secondaryActionLabel,
                secondaryActionListener
        );
        clientSupportStatePanel.setVisibility(View.VISIBLE);
    }

    private void hideBlockingState() {
        clientSupportStatePanel.setVisibility(View.GONE);
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
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
}
