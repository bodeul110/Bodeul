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
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerDocumentUploadPolicy;
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
import com.example.bodeul.util.DocumentPreviewLauncher;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 매니저 홈의 서류 등록 전용 화면에서 인증 서류 업로드와 검토 요청 흐름을 제어한다.
 */
public class ManagerDocumentRegistrationActivity extends AppCompatActivity
        implements ManagerDocumentRegistrationBinder.Listener {
    private static final String STATE_COMPLETION_PENDING = "state_completion_pending";
    private static final String[] QUALIFICATION_IMAGE_MIME_TYPES = new String[]{
            "image/jpeg",
            "image/png",
            "image/webp"
    };
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private ManagerDocumentPreviewResolver managerDocumentPreviewResolver;
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
    private View buttonBack;
    private View buttonClose;
    private View buttonSkip;
    private MaterialButton buttonRequest;
    private boolean submissionInFlight;
    private boolean completionPending;

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerDocumentRegistrationActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_document_registration);

        completionPending = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_COMPLETION_PENDING, false);
        ManagerScreenInsets.apply(
                findViewById(R.id.scrollManagerDocumentRegistration),
                findViewById(R.id.layoutManagerDocumentTopBar),
                findViewById(R.id.layoutManagerDocumentBottomAction)
        );
        getSupportFragmentManager().setFragmentResultListener(
                ManagerQualificationCompletionDialog.RESULT_KEY,
                this,
                (requestKey, result) -> completionPending = false
        );

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerDocumentStorageUploader = ServiceLocator.provideManagerDocumentStorageUploader(this);
        managerDocumentPreviewResolver = ServiceLocator.provideManagerDocumentPreviewResolver(this);
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

        buttonBack = findViewById(R.id.buttonBackManagerDocument);
        buttonClose = findViewById(R.id.buttonCloseManagerDocument);
        buttonSkip = findViewById(R.id.buttonManagerDocumentSkip);
        buttonRequest = findViewById(R.id.buttonManagerDocumentRequest);
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
                findViewById(R.id.buttonManagerDocumentPrimaryPreview),
                findViewById(R.id.buttonManagerDocumentPrimaryUpload),
                (LinearLayout) findViewById(R.id.managerDocumentRegistrationContainer),
                findViewById(R.id.cardManagerDocumentReview),
                findViewById(R.id.textManagerDocumentReviewTitle),
                findViewById(R.id.textManagerDocumentReviewBody),
                buttonRequest
        );

        buttonBack.setOnClickListener(view -> requestExit());
        buttonClose.setOnClickListener(view -> requestExit());
        buttonSkip.setOnClickListener(view -> requestExit());
        buttonRequest.setOnClickListener(view -> submitRegistrationRequest());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestExit();
            }
        });
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCompletionDialogIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_COMPLETION_PENDING, completionPending);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadScreen();
    }

    @Override
    public void onDocumentUploadRequested(@Nullable ManagerDocumentFileType fileType) {
        if (submissionInFlight) {
            return;
        }
        if (currentUser == null) {
            showAuthState();
            return;
        }
        
        if (!ManagerDocumentUploadPolicy.isCanonicalQualificationType(fileType)) {
            showLicenseTypeSelector();
            return;
        }
        
        pendingDocumentFileType = fileType;
        documentPickerLauncher.launch(QUALIFICATION_IMAGE_MIME_TYPES);
    }

    @Override
    public void onDocumentPreviewRequested(@Nullable ManagerDocumentFileType fileType) {
        if (submissionInFlight) {
            return;
        }
        ManagerDocumentFileMetadata metadata = findDocumentMetadata(fileType);
        if (metadata == null) {
            Toast.makeText(
                    this,
                    R.string.manager_document_preview_missing,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        openDocumentPreview(metadata);
    }

    private void showLicenseTypeSelector() {
        String[] options = new String[]{
                getString(R.string.manager_document_registration_document_nursing_license),
                getString(R.string.manager_document_registration_document_elderly_care_license)
        };
        ManagerDocumentFileType[] types = new ManagerDocumentFileType[]{
                ManagerDocumentFileType.NURSING_LICENSE,
                ManagerDocumentFileType.LICENSE
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manager_document_registration_document_nursing_or_elderly_care_license)
                .setItems(options, (dialog, which) -> {
                    pendingDocumentFileType = types[which];
                    documentPickerLauncher.launch(QUALIFICATION_IMAGE_MIME_TYPES);
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

        String fileTypeError = ManagerDocumentUploadPolicy.validateFileType(selectedFileType);
        if (!TextUtils.isEmpty(fileTypeError)) {
            Toast.makeText(this, fileTypeError, Toast.LENGTH_SHORT).show();
            return;
        }

        persistDocumentReadPermission(fileUri);
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

    private void openDocumentPreview(ManagerDocumentFileMetadata metadata) {
        setLoading(true);
        managerDocumentPreviewResolver.resolvePreviewUri(
                metadata,
                new RepositoryCallback<Uri>() {
                    @Override
                    public void onSuccess(Uri result) {
                        setLoading(false);
                        if (!DocumentPreviewLauncher.open(
                                ManagerDocumentRegistrationActivity.this,
                                result,
                                metadata.getContentType()
                        )) {
                            Toast.makeText(
                                    ManagerDocumentRegistrationActivity.this,
                                    R.string.manager_document_preview_open_failed,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(
                                ManagerDocumentRegistrationActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    @Nullable
    private ManagerDocumentFileMetadata findDocumentMetadata(@Nullable ManagerDocumentFileType fileType) {
        if (currentOverview == null || fileType == null) {
            return null;
        }
        return currentOverview.getProfile().getDocumentFile(fileType);
    }

    private void persistDocumentReadPermission(@Nullable Uri fileUri) {
        if (fileUri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 일부 공급자는 지속 권한을 주지 않아도 현재 세션 업로드는 가능하다.
        }
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
        if (submissionInFlight) {
            return;
        }
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

        setSubmissionInFlight(true);
        managerRepository.saveManagerDocumentSummary(
                currentUser.getId(),
                summary,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        if (!isActivityUsable()) {
                            return;
                        }
                        setSubmissionInFlight(false);
                        completionPending = true;
                        showCompletionDialogIfNeeded();
                        setLoading(true);
                        loadOverview();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isActivityUsable()) {
                            return;
                        }
                        setSubmissionInFlight(false);
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

    private void setSubmissionInFlight(boolean inFlight) {
        submissionInFlight = inFlight;
        setLoading(inFlight);
        buttonBack.setEnabled(!inFlight);
        buttonClose.setEnabled(!inFlight);
        buttonSkip.setEnabled(!inFlight);
        buttonBack.setAlpha(inFlight ? 0.45f : 1f);
        buttonClose.setAlpha(inFlight ? 0.45f : 1f);
        buttonSkip.setAlpha(inFlight ? 0.45f : 1f);
        binder.setInteractionsEnabled(!inFlight);
    }

    private void requestExit() {
        if (submissionInFlight) {
            Toast.makeText(
                    this,
                    R.string.manager_document_registration_submission_in_progress,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        finish();
    }

    private void showCompletionDialogIfNeeded() {
        if (!completionPending
                || !isActivityUsable()
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                || getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(
                ManagerQualificationCompletionDialog.TAG
        ) != null) {
            return;
        }
        new ManagerQualificationCompletionDialog().show(
                getSupportFragmentManager(),
                ManagerQualificationCompletionDialog.TAG
        );
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
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
                view -> openGeneralHome(),
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
                view -> openManagerHome()
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

    private void openGeneralHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openManagerHome() {
        Intent intent = new Intent(this, ManagerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

    private String getDocumentTypeLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return getString(R.string.manager_document_registration_document_id_card);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.NURSING_LICENSE
                || fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return getString(R.string.manager_document_registration_document_nursing_license);
        }
        return getString(R.string.manager_document_registration_document_criminal_record);
    }
}
