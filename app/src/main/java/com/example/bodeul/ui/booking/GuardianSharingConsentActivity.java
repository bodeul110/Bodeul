package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.GuardianSharingConsentRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.GuardianSharingConsent;
import com.example.bodeul.domain.model.GuardianSharingConsentScope;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class GuardianSharingConsentActivity extends AppCompatActivity {
    private static final String EXTRA_APPOINTMENT_REQUEST_ID = "appointmentRequestId";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy년 M월 d일 a h시 m분", Locale.KOREA)
            .withZone(ZoneId.of("Asia/Seoul"));

    private GuardianSharingConsentRepository consentRepository;
    private AuthRepository authRepository;
    private String appointmentRequestId;
    private TextView statusText;
    private TextView expiryText;
    private MaterialCheckBox appointmentCheck;
    private MaterialCheckBox locationCheck;
    private MaterialCheckBox chatCheck;
    private MaterialCheckBox attachmentCheck;
    private MaterialCheckBox reportCheck;
    private MaterialCheckBox adultPatientCheck;
    private MaterialButton saveButton;
    private MaterialButton revokeButton;
    private ProgressBar progress;
    private boolean activeConsent;
    private boolean locationSharingAvailable;

    public static Intent createIntent(Context context, String appointmentRequestId) {
        Intent intent = new Intent(context, GuardianSharingConsentActivity.class);
        intent.putExtra(EXTRA_APPOINTMENT_REQUEST_ID, appointmentRequestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_sharing_consent);

        appointmentRequestId = getIntent().getStringExtra(EXTRA_APPOINTMENT_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        consentRepository = ServiceLocator.provideGuardianSharingConsentRepository(this);
        statusText = findViewById(R.id.textGuardianSharingConsentStatus);
        expiryText = findViewById(R.id.textGuardianSharingConsentExpiry);
        appointmentCheck = findViewById(R.id.checkGuardianSharingAppointment);
        locationCheck = findViewById(R.id.checkGuardianSharingLocation);
        chatCheck = findViewById(R.id.checkGuardianSharingChat);
        attachmentCheck = findViewById(R.id.checkGuardianSharingAttachment);
        reportCheck = findViewById(R.id.checkGuardianSharingReport);
        adultPatientCheck = findViewById(R.id.checkGuardianSharingAdultPatient);
        saveButton = findViewById(R.id.buttonSaveGuardianSharingConsent);
        revokeButton = findViewById(R.id.buttonRevokeGuardianSharingConsent);
        progress = findViewById(R.id.progressGuardianSharingConsent);

        findViewById(R.id.buttonBackGuardianSharingConsent).setOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> saveConsent());
        revokeButton.setOnClickListener(view -> confirmRevoke());
        chatCheck.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                attachmentCheck.setChecked(false);
            }
            attachmentCheck.setEnabled(checked && !isLoading());
        });

        if (TextUtils.isEmpty(appointmentRequestId)) {
            Toast.makeText(this, R.string.booking_status_request_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        verifyPatientAndLoad();
    }

    private void verifyPatientAndLoad() {
        setLoading(true);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result == null || result.getRole() != UserRole.PATIENT) {
                    setLoading(false);
                    Toast.makeText(
                            GuardianSharingConsentActivity.this,
                            R.string.guardian_sharing_consent_patient_only,
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                    return;
                }
                loadConsent();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(
                        GuardianSharingConsentActivity.this,
                        R.string.state_auth_body,
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }
        });
    }

    private void loadConsent() {
        consentRepository.getConsent(
                appointmentRequestId,
                new RepositoryCallback<GuardianSharingConsent>() {
                    @Override
                    public void onSuccess(GuardianSharingConsent result) {
                        setLoading(false);
                        render(result);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        statusText.setText(R.string.guardian_sharing_consent_status_empty);
                        expiryText.setText(R.string.guardian_sharing_consent_expiry_rule);
                        revokeButton.setEnabled(false);
                    }
                }
        );
    }

    private void saveConsent() {
        EnumSet<GuardianSharingConsentScope> scopes = EnumSet.noneOf(
                GuardianSharingConsentScope.class);
        addIfChecked(scopes, appointmentCheck, GuardianSharingConsentScope.APPOINTMENT);
        addIfChecked(scopes, locationCheck, GuardianSharingConsentScope.LOCATION);
        addIfChecked(scopes, chatCheck, GuardianSharingConsentScope.CHAT);
        addIfChecked(scopes, attachmentCheck, GuardianSharingConsentScope.ATTACHMENT);
        addIfChecked(scopes, reportCheck, GuardianSharingConsentScope.REPORT);
        if (scopes.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.guardian_sharing_consent_scope_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (!adultPatientCheck.isChecked()) {
            Toast.makeText(
                    this,
                    R.string.guardian_sharing_consent_adult_confirmation_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (attachmentCheck.isChecked() && !chatCheck.isChecked()) {
            Toast.makeText(
                    this,
                    R.string.guardian_sharing_consent_attachment_requires_chat,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        setLoading(true);
        consentRepository.grantConsent(
                appointmentRequestId,
                Set.copyOf(scopes),
                true,
                callback(
                        R.string.guardian_sharing_consent_saved,
                        R.string.guardian_sharing_consent_save_failed)
        );
    }

    private void confirmRevoke() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.guardian_sharing_consent_revoke_title)
                .setMessage(R.string.guardian_sharing_consent_revoke_body)
                .setPositiveButton(
                        R.string.guardian_sharing_consent_revoke_confirm,
                        (dialog, which) -> revokeConsent())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void revokeConsent() {
        setLoading(true);
        consentRepository.revokeConsent(
                appointmentRequestId,
                callback(
                        R.string.guardian_sharing_consent_revoked,
                        R.string.guardian_sharing_consent_revoke_failed)
        );
    }

    private RepositoryCallback<GuardianSharingConsent> callback(
            int successMessage,
            int fallbackErrorMessage
    ) {
        return new RepositoryCallback<GuardianSharingConsent>() {
            @Override
            public void onSuccess(GuardianSharingConsent result) {
                setLoading(false);
                render(result);
                Toast.makeText(
                        GuardianSharingConsentActivity.this,
                        successMessage,
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(
                        GuardianSharingConsentActivity.this,
                        TextUtils.isEmpty(message) ? getString(fallbackErrorMessage) : message,
                        Toast.LENGTH_LONG
                ).show();
            }
        };
    }

    private void render(GuardianSharingConsent consent) {
        activeConsent = consent.isActive();
        locationSharingAvailable = consent.isLocationSharingAvailable();
        Set<GuardianSharingConsentScope> scopes = consent.getScopes();
        appointmentCheck.setChecked(scopes.contains(GuardianSharingConsentScope.APPOINTMENT));
        chatCheck.setChecked(scopes.contains(GuardianSharingConsentScope.CHAT));
        attachmentCheck.setChecked(
                scopes.contains(GuardianSharingConsentScope.CHAT)
                        && scopes.contains(GuardianSharingConsentScope.ATTACHMENT));
        reportCheck.setChecked(scopes.contains(GuardianSharingConsentScope.REPORT));
        locationCheck.setEnabled(locationSharingAvailable);
        locationCheck.setChecked(
                consent.isLocationSharingAvailable()
                        && scopes.contains(GuardianSharingConsentScope.LOCATION));
        statusText.setText(consent.isActive()
                ? R.string.guardian_sharing_consent_status_active
                : R.string.guardian_sharing_consent_status_inactive);
        expiryText.setText(consent.isExpiryFinalized()
                ? getString(
                        R.string.guardian_sharing_consent_expiry_value,
                        formatDateTime(consent.getExpiresAt()))
                : getString(R.string.guardian_sharing_consent_expiry_provisional));
        revokeButton.setEnabled(consent.isActive());
        adultPatientCheck.setChecked(false);
    }

    private void addIfChecked(
            EnumSet<GuardianSharingConsentScope> scopes,
            MaterialCheckBox checkBox,
            GuardianSharingConsentScope scope
    ) {
        if (checkBox.isChecked() && checkBox.isEnabled()) {
            scopes.add(scope);
        }
    }

    private String formatDateTime(String value) {
        try {
            return DATE_TIME_FORMATTER.format(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            return TextUtils.isEmpty(value)
                    ? getString(R.string.guardian_sharing_consent_expiry_unknown)
                    : value;
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
        if (loading) {
            revokeButton.setEnabled(false);
        }
        appointmentCheck.setEnabled(!loading);
        chatCheck.setEnabled(!loading);
        attachmentCheck.setEnabled(!loading && chatCheck.isChecked());
        reportCheck.setEnabled(!loading);
        adultPatientCheck.setEnabled(!loading);
        locationCheck.setEnabled(!loading && locationSharingAvailable);
        revokeButton.setEnabled(!loading && activeConsent);
    }

    private boolean isLoading() {
        return progress.getVisibility() == View.VISIBLE;
    }
}
