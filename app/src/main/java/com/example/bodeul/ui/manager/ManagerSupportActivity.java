package com.example.bodeul.ui.manager;

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
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 문의 작성과 최근 문의 내역 조회를 담당한다.
 */
public class ManagerSupportActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerSupportCoordinator managerSupportCoordinator;
    private ManagerSupportBinder managerSupportBinder;

    private User currentUser;
    private SupportInquiryCategory selectedCategory = SupportInquiryCategory.MATCHING;

    private View managerSupportStatePanel;
    private View managerSupportContentContainer;
    private ProgressBar progressManagerSupport;
    private MaterialButtonToggleGroup supportCategoryGroup;
    private TextInputLayout layoutSupportTitle;
    private TextInputLayout layoutSupportBody;
    private TextInputEditText inputSupportTitle;
    private TextInputEditText inputSupportBody;

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerSupportActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_support);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerSupportCoordinator = new ManagerSupportCoordinator(
                this,
                new ManagerHomePresentationFormatter(this)
        );

        managerSupportStatePanel = findViewById(R.id.managerSupportStatePanel);
        managerSupportContentContainer = findViewById(R.id.managerSupportContentContainer);
        progressManagerSupport = findViewById(R.id.progressManagerSupport);
        supportCategoryGroup = findViewById(R.id.managerSupportCategoryGroup);
        layoutSupportTitle = findViewById(R.id.layoutManagerSupportTitle);
        layoutSupportBody = findViewById(R.id.layoutManagerSupportBody);
        inputSupportTitle = findViewById(R.id.inputManagerSupportTitle);
        inputSupportBody = findViewById(R.id.inputManagerSupportBody);

        managerSupportBinder = new ManagerSupportBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textManagerSupportMode),
                findViewById(R.id.textManagerSupportHeroBadge),
                findViewById(R.id.textManagerSupportHeroTitle),
                findViewById(R.id.textManagerSupportHeroBody),
                findViewById(R.id.textManagerSupportLatestSummary),
                findViewById(R.id.managerSupportInquiryContainer),
                findViewById(R.id.textManagerSupportEmpty)
        );

        findViewById(R.id.buttonBackManagerSupport).setOnClickListener(view -> finish());
        findViewById(R.id.buttonManagerSupportSubmit).setOnClickListener(view -> submitInquiry());
        supportCategoryGroup.check(R.id.buttonManagerSupportCategoryMatching);
        supportCategoryGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                selectedCategory = toCategory(checkedId);
            }
        });
        managerSupportContentContainer.setVisibility(View.GONE);
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
                if (result.getRole() != UserRole.MANAGER) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                bindInquiries();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void bindInquiries() {
        if (currentUser == null) {
            return;
        }
        managerRepository.getSupportInquiries(
                currentUser.getId(),
                new RepositoryCallback<List<SupportInquiry>>() {
                    @Override
                    public void onSuccess(List<SupportInquiry> result) {
                        hideBlockingState();
                        managerSupportContentContainer.setVisibility(View.VISIBLE);
                        managerSupportBinder.bindScreen(managerSupportCoordinator.createScreenModel(
                                result,
                                managerRepository.isFirebaseBacked()
                        ));
                        setLoading(false);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerSupportActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
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
        managerRepository.submitSupportInquiry(
                currentUser.getId(),
                selectedCategory,
                title,
                body,
                new RepositoryCallback<List<SupportInquiry>>() {
                    @Override
                    public void onSuccess(List<SupportInquiry> result) {
                        inputSupportTitle.setText("");
                        inputSupportBody.setText("");
                        managerSupportBinder.bindScreen(managerSupportCoordinator.createScreenModel(
                                result,
                                managerRepository.isFirebaseBacked()
                        ));
                        setLoading(false);
                        Toast.makeText(
                                ManagerSupportActivity.this,
                                R.string.manager_support_submit_done,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerSupportActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private SupportInquiryCategory toCategory(int checkedId) {
        if (checkedId == R.id.buttonManagerSupportCategoryDocument) {
            return SupportInquiryCategory.DOCUMENT;
        }
        if (checkedId == R.id.buttonManagerSupportCategorySettlement) {
            return SupportInquiryCategory.SETTLEMENT;
        }
        if (checkedId == R.id.buttonManagerSupportCategoryOther) {
            return SupportInquiryCategory.OTHER;
        }
        return SupportInquiryCategory.MATCHING;
    }

    private void setLoading(boolean loading) {
        progressManagerSupport.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_manager_support_title)),
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
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                managerSupportStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerSupportContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(managerSupportStatePanel);
        managerSupportContentContainer.setVisibility(View.VISIBLE);
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
