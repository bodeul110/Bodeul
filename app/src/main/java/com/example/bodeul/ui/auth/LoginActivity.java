package com.example.bodeul.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 역할별 로그인과 회원가입을 처리하는 공통 인증 화면이다.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String EXTRA_ROLE_HINT = "role_hint";
    private static final long VERIFICATION_EMAIL_RESEND_COOLDOWN_MS = 60_000L;

    private AuthRepository authRepository;
    private UserRole roleHint;
    private boolean registerMode;
    private boolean loading;
    private long verificationResendAvailableAtMillis;
    @Nullable
    private CountDownTimer verificationResendCooldownTimer;

    private TextView textLoginTitle;
    private TextView textLoginSubtitle;
    private TextView textDemoBanner;
    private TextView textSwitchMode;
    private TextView textForgotPassword;
    private TextView textResendVerification;
    private TextInputLayout layoutName;
    private TextInputLayout layoutPhone;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPassword;
    private TextInputEditText inputName;
    private TextInputEditText inputPhone;
    private TextInputEditText inputEmail;
    private TextInputEditText inputPassword;
    private Chip chipRoleManager;
    private Chip chipRolePatient;
    private Chip chipRoleGuardian;
    private MaterialButton buttonSubmit;
    private MaterialButton buttonSocialKakao;
    private MaterialButton buttonSocialGoogle;
    private MaterialButton buttonSocialNaver;
    private ProgressBar progressBar;

    public static Intent createIntent(Context context, UserRole roleHint) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(EXTRA_ROLE_HINT, roleHint.name());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = ServiceLocator.provideAuthRepository(this);
        roleHint = UserRole.valueOf(getIntent().getStringExtra(EXTRA_ROLE_HINT) == null
                ? UserRole.MANAGER.name()
                : getIntent().getStringExtra(EXTRA_ROLE_HINT));

        textLoginTitle = findViewById(R.id.textLoginTitle);
        textLoginSubtitle = findViewById(R.id.textLoginSubtitle);
        textDemoBanner = findViewById(R.id.textDemoBanner);
        textSwitchMode = findViewById(R.id.textSwitchMode);
        textForgotPassword = findViewById(R.id.textForgotPassword);
        textResendVerification = findViewById(R.id.textResendVerification);
        layoutName = findViewById(R.id.layoutName);
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        inputName = findViewById(R.id.inputName);
        inputPhone = findViewById(R.id.inputPhone);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        chipRoleManager = findViewById(R.id.chipRoleManager);
        chipRolePatient = findViewById(R.id.chipRolePatient);
        chipRoleGuardian = findViewById(R.id.chipRoleGuardian);
        buttonSubmit = findViewById(R.id.buttonSubmitAuth);
        buttonSocialKakao = findViewById(R.id.buttonSocialKakao);
        buttonSocialGoogle = findViewById(R.id.buttonSocialGoogle);
        buttonSocialNaver = findViewById(R.id.buttonSocialNaver);
        progressBar = findViewById(R.id.progressAuth);

        configureRoleChips();
        bindMode();
        bindFirebaseBanner();
        applyDemoCredentials();

        buttonSubmit.setOnClickListener(view -> submitAuth());
        textSwitchMode.setOnClickListener(view -> {
            registerMode = !registerMode;
            bindMode();
        });
        textForgotPassword.setOnClickListener(view -> requestPasswordReset());
        textResendVerification.setOnClickListener(view -> requestVerificationEmailResend());
        buttonSocialKakao.setOnClickListener(view -> submitKakaoAuth());
        buttonSocialGoogle.setOnClickListener(view -> submitGoogleAuth());
        buttonSocialNaver.setOnClickListener(view -> submitNaverAuth());

        View.OnClickListener roleListener = view -> applyDemoCredentials();
        chipRoleManager.setOnClickListener(roleListener);
        chipRolePatient.setOnClickListener(roleListener);
        chipRoleGuardian.setOnClickListener(roleListener);
    }

    private void configureRoleChips() {
        // 매니저 로그인은 역할이 고정이고, 일반 사용자 로그인은 환자/보호자 선택을 노출한다.
        if (roleHint == UserRole.MANAGER) {
            chipRoleManager.setVisibility(View.VISIBLE);
            chipRoleManager.setChecked(true);
            chipRolePatient.setVisibility(View.GONE);
            chipRoleGuardian.setVisibility(View.GONE);
            return;
        }

        chipRoleManager.setVisibility(View.GONE);
        chipRolePatient.setVisibility(View.VISIBLE);
        chipRoleGuardian.setVisibility(View.VISIBLE);
        chipRolePatient.setChecked(true);
    }

    private void bindMode() {
        // 로그인과 회원가입 모드에 따라 필요한 입력 필드만 노출한다.
        textLoginTitle.setText(roleHint == UserRole.MANAGER
                ? R.string.login_title_manager
                : R.string.login_title_general);
        textLoginSubtitle.setText(registerMode
                ? R.string.login_subtitle_register
                : R.string.login_subtitle);

        buttonSubmit.setText(registerMode ? R.string.register_button : R.string.login_button);
        textSwitchMode.setText(registerMode
                ? R.string.switch_to_login
                : R.string.switch_to_register);
        layoutName.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        layoutPhone.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        textForgotPassword.setVisibility(registerMode ? View.GONE : View.VISIBLE);
        updateVerificationResendState();
    }

    private void bindFirebaseBanner() {
        if (authRepository.isFirebaseBacked()) {
            textDemoBanner.setVisibility(View.GONE);
            return;
        }

        // Firebase 설정 전에는 바로 체험할 수 있도록 데모 계정을 화면에 안내한다.
        textDemoBanner.setVisibility(View.VISIBLE);
        textDemoBanner.setText(roleHint == UserRole.MANAGER
                ? R.string.demo_banner_manager
                : R.string.demo_banner_general);
    }

    private void applyDemoCredentials() {
        if (authRepository.isFirebaseBacked()) {
            return;
        }

        // 데모 모드에서는 역할에 맞는 기본 계정을 자동으로 채워준다.
        if (roleHint == UserRole.MANAGER || chipRoleManager.isChecked()) {
            inputEmail.setText(R.string.demo_account_manager_email);
            inputPassword.setText(R.string.demo_account_password);
            return;
        }

        if (chipRoleGuardian.isChecked()) {
            inputEmail.setText(R.string.demo_account_guardian_email);
            inputPassword.setText(R.string.demo_account_password);
            return;
        }

        inputEmail.setText(R.string.demo_account_patient_email);
        inputPassword.setText(R.string.demo_account_password);
    }

    private void submitAuth() {
        clearErrors();

        // 공통 필수값을 먼저 검사해 불필요한 저장소 호출을 막는다.
        String email = valueOf(inputEmail);
        String password = valueOf(inputPassword);
        if (!validateEmail(email) || !validatePassword(password, registerMode)) {
            return;
        }

        UserRole selectedRole = getSelectedRole();
        if (selectedRole == null) {
            Toast.makeText(this, R.string.toast_role_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        if (registerMode) {
            String name = valueOf(inputName);
            String phone = valueOf(inputPhone);
            if (!validateName(name) || !validatePhone(phone)) {
                setLoading(false);
                return;
            }

            // 회원가입 시 선택한 역할을 함께 저장해 이후 화면 분기를 단순화한다.
            authRepository.register(name, email, phone, password, selectedRole, registerCallback);
            return;
        }

        authRepository.signIn(email, password, selectedRole, signInCallback);
    }

    private void submitGoogleAuth() {
        clearErrors();

        // 구글 로그인도 현재 선택한 역할을 기준으로 Firestore 프로필을 맞춘다.
        UserRole selectedRole = getSelectedRole();
        if (selectedRole == null) {
            Toast.makeText(this, R.string.toast_role_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.signInWithGoogle(this, selectedRole, signInCallback);
    }

    private void submitKakaoAuth() {
        clearErrors();

        // 카카오 로그인도 현재 선택한 역할을 기준으로 Firestore 프로필을 맞춘다.
        UserRole selectedRole = getSelectedRole();
        if (selectedRole == null) {
            Toast.makeText(this, R.string.toast_role_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.signInWithKakao(this, selectedRole, signInCallback);
    }

    private void submitNaverAuth() {
        clearErrors();

        // 네이버 로그인도 현재 선택한 역할을 기준으로 Firestore 프로필을 맞춘다.
        UserRole selectedRole = getSelectedRole();
        if (selectedRole == null) {
            Toast.makeText(this, R.string.toast_role_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.signInWithNaver(this, selectedRole, signInCallback);
    }

    private void requestPasswordReset() {
        clearErrors();

        // 비밀번호 재설정은 이메일만 있으면 실행할 수 있다.
        String email = valueOf(inputEmail);
        if (!validateEmail(email)) {
            return;
        }

        setLoading(true);
        authRepository.resetPassword(email, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, R.string.toast_password_reset_sent, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestVerificationEmailResend() {
        clearErrors();

        // 재발송은 로그인 전이므로 이메일과 비밀번호를 다시 확인한 뒤 실행한다.
        String email = valueOf(inputEmail);
        String password = valueOf(inputPassword);
        if (!validateEmail(email) || !validatePassword(password, false)) {
            return;
        }

        setLoading(true);
        authRepository.resendVerificationEmail(email, password, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                startVerificationResendCooldown();
                Toast.makeText(
                        LoginActivity.this,
                        R.string.toast_verification_email_resent,
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private final RepositoryCallback<User> signInCallback = new RepositoryCallback<User>() {
        @Override
        public void onSuccess(User result) {
            setLoading(false);
            // 로그인된 역할에 따라 매니저 전용 화면과 일반 홈 화면을 분기한다.
            Intent intent = result.getRole() == UserRole.MANAGER
                    ? new Intent(LoginActivity.this, ManagerActivity.class)
                    : new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        @Override
        public void onError(String message) {
            setLoading(false);
            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
        }
    };

    private final RepositoryCallback<User> registerCallback = new RepositoryCallback<User>() {
        @Override
        public void onSuccess(User result) {
            setLoading(false);

            // 회원가입 직후에는 다시 로그인 흐름으로 보내 메일 인증 또는 첫 로그인을 유도한다.
            authRepository.signOut();
            registerMode = false;
            inputName.setText(null);
            inputPhone.setText(null);
            inputPassword.setText(null);
            bindMode();
            if (authRepository.isFirebaseBacked()) {
                startVerificationResendCooldown();
            }

            Toast.makeText(
                    LoginActivity.this,
                    authRepository.isFirebaseBacked()
                            ? R.string.toast_register_success_verify
                            : R.string.toast_register_success_demo,
                    Toast.LENGTH_LONG
            ).show();
        }

        @Override
        public void onError(String message) {
            setLoading(false);
            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
        }
    };

    @Nullable
    private UserRole getSelectedRole() {
        // 현재 노출된 칩 중 선택된 항목을 읽어 실제 로그인 대상 역할로 변환한다.
        if (chipRoleManager.getVisibility() == View.VISIBLE && chipRoleManager.isChecked()) {
            return UserRole.MANAGER;
        }
        if (chipRoleGuardian.getVisibility() == View.VISIBLE && chipRoleGuardian.isChecked()) {
            return UserRole.GUARDIAN;
        }
        if (chipRolePatient.getVisibility() == View.VISIBLE && chipRolePatient.isChecked()) {
            return UserRole.PATIENT;
        }
        return null;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSubmit.setEnabled(!loading);
        buttonSocialKakao.setEnabled(!loading);
        buttonSocialGoogle.setEnabled(!loading);
        buttonSocialNaver.setEnabled(!loading);
        textSwitchMode.setEnabled(!loading);
        textForgotPassword.setEnabled(!loading);
        chipRoleManager.setEnabled(!loading);
        chipRolePatient.setEnabled(!loading);
        chipRoleGuardian.setEnabled(!loading);
        updateVerificationResendState();
    }

    private void clearErrors() {
        layoutName.setError(null);
        layoutPhone.setError(null);
        layoutEmail.setError(null);
        layoutPassword.setError(null);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            layoutEmail.setError(getString(R.string.error_email_required));
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError(getString(R.string.error_email_invalid));
            return false;
        }
        return true;
    }

    private boolean validatePassword(String password, boolean enforceRule) {
        if (TextUtils.isEmpty(password)) {
            layoutPassword.setError(getString(R.string.error_password_required));
            return false;
        }
        if (enforceRule && password.length() < 6) {
            layoutPassword.setError(getString(R.string.error_password_length));
            return false;
        }
        return true;
    }

    private boolean validateName(String name) {
        if (TextUtils.isEmpty(name)) {
            layoutName.setError(getString(R.string.error_name_required));
            return false;
        }
        return true;
    }

    private boolean validatePhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            layoutPhone.setError(getString(R.string.error_phone_required));
            return false;
        }

        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            layoutPhone.setError(getString(R.string.error_phone_invalid));
            return false;
        }
        return true;
    }

    private void startVerificationResendCooldown() {
        verificationResendAvailableAtMillis =
                System.currentTimeMillis() + VERIFICATION_EMAIL_RESEND_COOLDOWN_MS;
        if (verificationResendCooldownTimer != null) {
            verificationResendCooldownTimer.cancel();
        }

        verificationResendCooldownTimer = new CountDownTimer(
                VERIFICATION_EMAIL_RESEND_COOLDOWN_MS,
                1_000L
        ) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateVerificationResendState();
            }

            @Override
            public void onFinish() {
                verificationResendAvailableAtMillis = 0L;
                verificationResendCooldownTimer = null;
                updateVerificationResendState();
            }
        };
        verificationResendCooldownTimer.start();
        updateVerificationResendState();
    }

    private void updateVerificationResendState() {
        if (textResendVerification == null) {
            return;
        }

        boolean visible = !registerMode && authRepository.isFirebaseBacked();
        textResendVerification.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }

        long remainingSeconds = getVerificationResendRemainingSeconds();
        boolean enabled = !loading && remainingSeconds == 0L;
        textResendVerification.setEnabled(enabled);
        textResendVerification.setAlpha(enabled ? 1f : 0.5f);
        textResendVerification.setText(remainingSeconds > 0L
                ? getString(R.string.resend_verification_email_countdown, remainingSeconds)
                : getString(R.string.resend_verification_email));
    }

    private long getVerificationResendRemainingSeconds() {
        long remainingMillis = verificationResendAvailableAtMillis - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return 0L;
        }
        return (remainingMillis + 999L) / 1_000L;
    }

    @Override
    protected void onDestroy() {
        if (verificationResendCooldownTimer != null) {
            verificationResendCooldownTimer.cancel();
            verificationResendCooldownTimer = null;
        }
        super.onDestroy();
    }
}
