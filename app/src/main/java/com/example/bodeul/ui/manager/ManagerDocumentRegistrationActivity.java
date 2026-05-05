package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 매니저 홈의 서류 등록 전용 화면에서 인증 서류 업로드와 검토 요청 흐름을 제어한다.
 */
public class ManagerDocumentRegistrationActivity extends AppCompatActivity
        implements ManagerDocumentRegistrationBinder.Listener {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private ManagerDocumentRegistrationCoordinator coordinator;
    private ManagerDocumentRegistrationBinder binder;
    private ActivityResultLauncher<String[]> documentPickerLauncher;

    @Nullable
    private User currentUser;
    @Nullable
    private ManagerDocumentOverview currentOverview;
    @Nullable
    private ManagerDocumentFileType pendingDocumentFileType;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerDocumentRegistrationActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_document_registration);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerDocumentStorageUploader = ServiceLocator.provideManagerDocumentStorageUploader(this);
        coordinator = new ManagerDocumentRegistrationCoordinator(
                this,
                new ManagerHomePresentationFormatter(this)
        );
        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleDocumentPicked
        );

        statePanel = findViewById(R.id.managerDocumentStatePanel);
        contentContainer = findViewById(R.id.managerDocumentContentContainer);
        progressBar = findViewById(R.id.progressManagerDocumentRegistration);

        MaterialButton buttonRequest = findViewById(R.id.buttonManagerDocumentRequest);
        binder = new ManagerDocumentRegistrationBinder(
                getLayoutInflater(),
                this,
                findViewById(R.id.textManagerDocumentMode),
                findViewById(R.id.managerDocumentHiddenStatusContainer),
                findViewById(R.id.textManagerDocumentStatusBadge),
                findViewById(R.id.textManagerDocumentStatusTitle),
                findViewById(R.id.textManagerDocumentStatusBody),
                findViewById(R.id.textManagerDocumentPrimaryTitle),
                findViewById(R.id.textManagerDocumentPrimaryHelper),
                findViewById(R.id.textManagerDocumentPrimaryFileName),
                findViewById(R.id.textManagerDocumentPrimaryFileMeta),
                findViewById(R.id.buttonManagerDocumentPrimaryUpload),
                (LinearLayout) findViewById(R.id.managerDocumentRegistrationContainer),
                findViewById(R.id.cardManagerDocumentReview),
                findViewById(R.id.textManagerDocumentReviewTitle),
                findViewById(R.id.textManagerDocumentReviewBody),
                buttonRequest
        );

        findViewById(R.id.buttonBackManagerDocument).setOnClickListener(view -> finish());
        buttonRequest.setOnClickListener(view -> submitRegistrationRequest());
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadScreen();
    }

    @Override
    public void onDocumentUploadRequested(@Nullable ManagerDocumentFileType fileType) {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        
        if (fileType == null) {
            showLicenseTypeSelector();
            return;
        }
        
        pendingDocumentFileType = fileType;
        documentPickerLauncher.launch(new String[]{"application/pdf", "image/*"});
    }

    private void showLicenseTypeSelector() {
        String[] options = new String[]{
                getString(R.string.manager_document_registration_document_nursing_license),
                getString(R.string.manager_document_registration_document_elderly_care_license)
        };
        ManagerDocumentFileType[] types = new ManagerDocumentFileType[]{
                ManagerDocumentFileType.HEALTH_CERTIFICATE,
                ManagerDocumentFileType.LICENSE
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manager_document_registration_document_nursing_or_elderly_care_license)
                .setItems(options, (dialog, which) -> {
                    pendingDocumentFileType = types[which];
                    documentPickerLauncher.launch(new String[]{"application/pdf", "image/*"});
                })
                .show();
    }

    private void handleDocumentPicked(@Nullable Uri fileUri) {
        ManagerDocumentFileType selectedFileType = pendingDocumentFileType;
        pendingDocumentFileType = null;
        if (fileUri == null || selectedFileType == null) {
            return;
        }
        if (currentUser == null) {
            showAuthState();
            return;
        }

        setLoading(true);
        managerDocumentStorageUploader.uploadDocument(
                currentUser.getId(),
                selectedFileType,
                fileUri,
                new RepositoryCallback<ManagerDocumentFileMetadata>() {
                    @Override
                    public void onSuccess(ManagerDocumentFileMetadata result) {
                        saveDraftDocumentFile(selectedFileType, result);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerDocumentRegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void saveDraftDocumentFile(
            ManagerDocumentFileType fileType,
            ManagerDocumentFileMetadata documentFileMetadata
    ) {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        managerRepository.saveManagerDocumentDraftFileMetadata(
                currentUser.getId(),
                documentFileMetadata,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        Toast.makeText(
                                ManagerDocumentRegistrationActivity.this,
                                getString(
                                        R.string.manager_document_registration_upload_saved,
                                        getDocumentTypeLabel(fileType)
                                ),
                                Toast.LENGTH_SHORT
                        ).show();
                        loadOverview();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerDocumentRegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void submitRegistrationRequest() {
        if (currentUser == null || currentOverview == null) {
            showAuthState();
            return;
        }

        ManagerHomeProfile profile = currentOverview.getProfile();
        if (!coordinator.canRequestReview(profile)) {
            Toast.makeText(
                    this,
                    R.string.manager_document_registration_request_missing_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String summary = coordinator.buildRequestSummary(profile);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(
                    this,
                    R.string.manager_document_registration_request_missing_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        setLoading(true);
        managerRepository.saveManagerDocumentSummary(
                currentUser.getId(),
                summary,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        Toast.makeText(
                                ManagerDocumentRegistrationActivity.this,
                                R.string.manager_document_registration_request_success,
                                Toast.LENGTH_SHORT
                        ).show();
                        loadOverview();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerDocumentRegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void loadScreen() {
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
                loadOverview();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadOverview() {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        managerRepository.getManagerDocumentOverview(
                currentUser.getId(),
                new RepositoryCallback<ManagerDocumentOverview>() {
                    @Override
                    public void onSuccess(ManagerDocumentOverview result) {
                        currentOverview = result;
                        setLoading(false);
                        hideBlockingState();
                        contentContainer.setVisibility(View.VISIBLE);
                        binder.bindScreen(
                                coordinator.createScreenModel(
                                        result,
                                        managerRepository.isFirebaseBacked()
                                )
                        );
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(
                        R.string.state_permission_title,
                        getString(R.string.manager_document_registration_heading)
                ),
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
                getString(
                        R.string.state_load_error_title,
                        getString(R.string.manager_document_registration_heading)
                ),
                body,
                getString(R.string.state_action_retry),
                view -> loadScreen(),
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
        contentContainer.setVisibility(View.VISIBLE);
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

    private String getDocumentTypeLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return getString(R.string.manager_document_registration_document_id_card);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return getString(R.string.manager_document_registration_document_nursing_license);
        }
        return getString(R.string.manager_document_registration_document_criminal_record);
    }
}
