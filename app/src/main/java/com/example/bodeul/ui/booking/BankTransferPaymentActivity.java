package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BankTransferPaymentRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.BankTransferPayment;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 환자 본인이 무통장입금 상태와 입금자명을 확인하는 화면이다.
 */
public final class BankTransferPaymentActivity extends AppCompatActivity {
    private static final String EXTRA_APPOINTMENT_REQUEST_ID = "appointmentRequestId";
    private static final String STATE_DEPOSITOR_DRAFT = "depositorDraft";
    private static final String STATE_HAS_DEPOSITOR_DRAFT = "hasDepositorDraft";

    private AuthRepository authRepository;
    private BankTransferPaymentRepository paymentRepository;
    private BankTransferPaymentCoordinator coordinator;
    private BankTransferPaymentBinder binder;

    private View statePanel;
    private View contentContainer;
    private ProgressBar initialProgress;
    private ProgressBar saveProgress;
    private TextInputLayout depositorInputLayout;
    private TextInputEditText depositorInput;
    private MaterialButton saveButton;

    private String appointmentRequestId;
    private int requestGeneration;
    private boolean bindingDepositorInput;
    private boolean hasDepositorDraft;
    private String depositorDraft = "";
    @Nullable
    private BankTransferPayment currentPayment;

    public static Intent createIntent(Context context, String appointmentRequestId) {
        Intent intent = new Intent(context, BankTransferPaymentActivity.class);
        intent.putExtra(EXTRA_APPOINTMENT_REQUEST_ID, appointmentRequestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_transfer_payment);

        appointmentRequestId = getIntent().getStringExtra(EXTRA_APPOINTMENT_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        paymentRepository = ServiceLocator.provideBankTransferPaymentRepository(this);
        coordinator = new BankTransferPaymentCoordinator(
                this,
                new BookingPresentationFormatter(this));

        statePanel = findViewById(R.id.bankTransferPaymentStatePanel);
        contentContainer = findViewById(R.id.bankTransferPaymentContentContainer);
        initialProgress = findViewById(R.id.progressBankTransferPaymentInitial);
        saveProgress = findViewById(R.id.progressBankTransferPaymentSave);
        depositorInputLayout = findViewById(R.id.layoutBankTransferDepositorInput);
        depositorInput = findViewById(R.id.inputBankTransferDepositor);
        saveButton = findViewById(R.id.buttonSaveBankTransferDepositor);
        binder = new BankTransferPaymentBinder(
                findViewById(R.id.textBankTransferPaymentStatus),
                findViewById(R.id.textBankTransferPaymentStatusBody),
                findViewById(R.id.textBankTransferPaymentExpectedAmount),
                findViewById(R.id.textBankTransferPaymentDueAt),
                findViewById(R.id.textBankTransferPaymentReceivedAmount),
                findViewById(R.id.textBankTransferPaymentConfirmedAt),
                findViewById(R.id.textBankTransferPaymentRefundRequestedAt),
                findViewById(R.id.textBankTransferPaymentRefundedAt),
                findViewById(R.id.textBankTransferPaymentCurrentDepositor),
                findViewById(R.id.textBankTransferPaymentInstruction),
                depositorInputLayout,
                saveButton
        );

        applySystemInsets(findViewById(R.id.scrollBankTransferPayment));
        depositorInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (bindingDepositorInput) {
                    return;
                }
                hasDepositorDraft = true;
                depositorDraft = value == null ? "" : value.toString();
            }
        });
        if (savedInstanceState != null) {
            hasDepositorDraft = savedInstanceState.getBoolean(
                    STATE_HAS_DEPOSITOR_DRAFT,
                    false);
            depositorDraft = savedInstanceState.getString(STATE_DEPOSITOR_DRAFT, "");
        }

        findViewById(R.id.buttonBackBankTransferPayment).setOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> saveDepositorName());
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startLoad();
    }

    private void startLoad() {
        int generation = ++requestGeneration;
        clearSensitiveContent();
        verifyPatientAndLoad(generation);
    }

    @Override
    protected void onStop() {
        requestGeneration++;
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_DEPOSITOR_DRAFT, hasDepositorDraft);
        outState.putString(STATE_DEPOSITOR_DRAFT, depositorDraft);
    }

    private void verifyPatientAndLoad(int generation) {
        if (TextUtils.isEmpty(appointmentRequestId)) {
            if (isCurrentRequest(generation)) {
                showLoadErrorState(getString(R.string.booking_status_request_missing));
            }
            return;
        }

        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isCurrentRequest(generation)) {
                    return;
                }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result == null || result.getRole() != UserRole.PATIENT) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                loadPayment(generation);
            }

            @Override
            public void onError(String message) {
                if (!isCurrentRequest(generation)) {
                    return;
                }
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadPayment(int generation) {
        paymentRepository.getPayment(
                appointmentRequestId,
                new RepositoryCallback<BankTransferPayment>() {
                    @Override
                    public void onSuccess(BankTransferPayment result) {
                        if (isCurrentRequest(generation)) {
                            render(result);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isCurrentRequest(generation)) {
                            return;
                        }
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void saveDepositorName() {
        if (currentPayment == null || !currentPayment.canEditDepositorName()) {
            return;
        }
        String normalizedName = BankTransferPaymentAccessPolicy.normalizeDepositorName(
                depositorInput.getText() == null ? "" : depositorInput.getText().toString());
        if (!BankTransferPaymentAccessPolicy.isValidDepositorName(normalizedName)) {
            depositorInputLayout.setError(
                    getString(R.string.bank_transfer_payment_depositor_invalid));
            return;
        }

        depositorInputLayout.setError(null);
        int generation = ++requestGeneration;
        setLoading(true);
        paymentRepository.saveDepositorName(
                appointmentRequestId,
                normalizedName,
                new RepositoryCallback<BankTransferPayment>() {
                    @Override
                    public void onSuccess(BankTransferPayment result) {
                        if (!isCurrentRequest(generation)) {
                            return;
                        }
                        hasDepositorDraft = false;
                        depositorDraft = "";
                        render(result);
                        Toast.makeText(
                                BankTransferPaymentActivity.this,
                                R.string.bank_transfer_payment_depositor_saved,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isCurrentRequest(generation)) {
                            return;
                        }
                        setLoading(false);
                        Toast.makeText(
                                BankTransferPaymentActivity.this,
                                TextUtils.isEmpty(message)
                                        ? getString(R.string.bank_transfer_payment_depositor_save_failed)
                                        : message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void render(BankTransferPayment payment) {
        currentPayment = payment;
        BankTransferPaymentScreenModel screenModel = coordinator.createScreenModel(payment);
        if (!screenModel.isDepositorEditable()) {
            hasDepositorDraft = false;
            depositorDraft = "";
        }
        binder.bind(screenModel);
        bindDepositorInput(hasDepositorDraft && screenModel.isDepositorEditable()
                ? depositorDraft
                : screenModel.getDepositorInputValue());
        contentContainer.setVisibility(View.VISIBLE);
        hideBlockingState();
        setLoading(false);
    }

    private void setLoading(boolean loading) {
        initialProgress.setVisibility(
                loading && currentPayment == null ? View.VISIBLE : View.GONE);
        saveProgress.setVisibility(
                loading && currentPayment != null ? View.VISIBLE : View.GONE);
        depositorInputLayout.setEnabled(!loading
                && currentPayment != null
                && currentPayment.canEditDepositorName());
        saveButton.setEnabled(!loading
                && currentPayment != null
                && currentPayment.canEditDepositorName());
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.bank_transfer_payment_permission_title),
                getString(R.string.bank_transfer_payment_permission_body),
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
                getString(
                        R.string.state_load_error_title,
                        getString(R.string.bank_transfer_payment_title)),
                body,
                getString(R.string.state_action_retry),
                view -> startLoad(),
                getString(R.string.guide_back),
                view -> finish()
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

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

    private void clearSensitiveContent() {
        currentPayment = null;
        contentContainer.setVisibility(View.GONE);
        initialProgress.setVisibility(View.GONE);
        saveProgress.setVisibility(View.GONE);
        depositorInputLayout.setError(null);
        bindDepositorInput("");
    }

    private void bindDepositorInput(String value) {
        bindingDepositorInput = true;
        depositorInput.setText(value == null ? "" : value);
        depositorInput.setSelection(depositorInput.length());
        bindingDepositorInput = false;
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && !isFinishing() && !isDestroyed();
    }

    private void applySystemInsets(ScrollView scrollView) {
        int left = scrollView.getPaddingLeft();
        int top = scrollView.getPaddingTop();
        int right = scrollView.getPaddingRight();
        int bottom = scrollView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    left + insets.left,
                    top + insets.top,
                    right + insets.right,
                    bottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(scrollView);
    }
}
