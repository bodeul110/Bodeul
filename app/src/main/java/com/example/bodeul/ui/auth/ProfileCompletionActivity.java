package com.example.bodeul.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 소셜 로그인 직후 누락된 프로필 정보를 보완 입력받는 화면이다.
 */
public class ProfileCompletionActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private User currentUser;
    private boolean loading;

    private TextView textProfileEmail;
    private TextView textProfileRole;
    private TextInputLayout layoutName;
    private TextInputLayout layoutPhone;
    private TextInputEditText inputName;
    private TextInputEditText inputPhone;
    private MaterialButton buttonSaveProfile;
    private ProgressBar progressProfile;

    public static Intent createIntent(Context context) {
        return new Intent(context, ProfileCompletionActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_completion);

        authRepository = ServiceLocator.provideAuthRepository(this);
        textProfileEmail = findViewById(R.id.textProfileEmail);
        textProfileRole = findViewById(R.id.textProfileRole);
        layoutName = findViewById(R.id.layoutProfileName);
        layoutPhone = findViewById(R.id.layoutProfilePhone);
        inputName = findViewById(R.id.inputProfileName);
        inputPhone = findViewById(R.id.inputProfilePhone);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        progressProfile = findViewById(R.id.progressProfile);

        buttonSaveProfile.setOnClickListener(view -> saveProfile());
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadCurrentUser();
    }

    private void loadCurrentUser() {
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                currentUser = result;
                if (!AuthFlowRouter.requiresProfileCompletion(result)) {
                    openNext(result);
                    return;
                }
                bindUser(result);
            }

            @Override
            public void onError(String message) {
                openRoleSelection();
            }
        });
    }

    private void bindUser(User user) {
        // 저장 전에도 현재 계정과 역할을 분명하게 보여줘 오입력을 줄인다.
        textProfileEmail.setText(getString(R.string.profile_completion_email_value, user.getEmail()));
        textProfileRole.setText(getString(
                R.string.profile_completion_role_value,
                toRoleLabel(user.getRole())
        ));
        inputName.setText(TextUtils.isEmpty(user.getName()) ? null : user.getName());
        inputPhone.setText(TextUtils.isEmpty(user.getPhone()) ? null : user.getPhone());
    }

    private void saveProfile() {
        if (currentUser == null || loading) {
            return;
        }

        clearErrors();
        String name = valueOf(inputName);
        String phone = valueOf(inputPhone);
        if (!validateName(name) || !validatePhone(phone)) {
            return;
        }

        setLoading(true);
        authRepository.updateCurrentUserProfile(name, phone, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                setLoading(false);
                Toast.makeText(
                        ProfileCompletionActivity.this,
                        R.string.toast_profile_updated,
                        Toast.LENGTH_SHORT
                ).show();
                openNext(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(ProfileCompletionActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openNext(User user) {
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

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressProfile.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSaveProfile.setEnabled(!loading);
        inputName.setEnabled(!loading);
        inputPhone.setEnabled(!loading);
    }

    private void clearErrors() {
        layoutName.setError(null);
        layoutPhone.setError(null);
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

    private String toRoleLabel(UserRole role) {
        switch (role) {
            case MANAGER:
                return getString(R.string.login_role_manager);
            case GUARDIAN:
                return getString(R.string.login_role_guardian);
            case ADMIN:
                return getString(R.string.feature_admin_title);
            case PATIENT:
            default:
                return getString(R.string.login_role_patient);
        }
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
