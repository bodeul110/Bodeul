package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

/**
 * 매니저 내 페이지 화면의 인증, 로딩, 저장 흐름을 담당한다.
 */
public class ManagerProfileActivity extends AppCompatActivity implements ManagerQuickNoteDialogController.Listener {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private ManagerProfileCoordinator managerProfileCoordinator;
    private ManagerProfileBinder managerProfileBinder;
    private ManagerQuickNoteDialogController quickNoteDialogController;
    private ActivityResultLauncher<String[]> documentPickerLauncher;

    private User currentUser;
    @Nullable
    private ManagerHomeProfile currentProfile;
    @Nullable
    private ManagerDocumentFileType pendingDocumentFileType;

    private View managerProfileStatePanel;
    private View managerProfileContentContainer;
    private ProgressBar progressManagerProfile;

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerProfileActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_profile);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerDocumentStorageUploader = ServiceLocator.provideManagerDocumentStorageUploader(this);
        ManagerHomePresentationFormatter formatter = new ManagerHomePresentationFormatter(this);
        managerProfileCoordinator = new ManagerProfileCoordinator(this, formatter);
        quickNoteDialogController = new ManagerQuickNoteDialogController(this, getLayoutInflater());
        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleDocumentPicked
        );

        managerProfileStatePanel = findViewById(R.id.managerProfileStatePanel);
        managerProfileContentContainer = findViewById(R.id.managerProfileContentContainer);
        progressManagerProfile = findViewById(R.id.progressManagerProfile);

        managerProfileBinder = new ManagerProfileBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textManagerProfileMode),
                findViewById(R.id.textManagerProfileHeroBadge),
                findViewById(R.id.textManagerProfileHeroTitle),
                findViewById(R.id.textManagerProfileHeroBody),
                findViewById(R.id.textManagerProfileUploadHighlightTitle),
                findViewById(R.id.textManagerProfileUploadHighlightBody),
                findViewById(R.id.managerProfileAccountContainer),
                findViewById(R.id.managerProfileDocumentFileContainer),
                findViewById(R.id.managerProfileDocumentContainer),
                findViewById(R.id.textManagerProfileReviewNote),
                findViewById(R.id.textManagerProfileTimeline),
                findViewById(R.id.managerProfileHistoryContainer),
                findViewById(R.id.textManagerProfileHistoryEmpty)
        );

        findViewById(R.id.buttonBackManagerProfile).setOnClickListener(view -> finish());
        findViewById(R.id.buttonManagerProfileDocumentEdit).setOnClickListener(view ->
                quickNoteDialogController.show(
                        ManagerQuickNoteType.DOCUMENT,
                        currentProfile == null ? "" : currentProfile.getDocumentSummary(),
                        this
                )
        );
        findViewById(R.id.buttonManagerProfileDocumentUpload).setOnClickListener(view ->
                openDocumentTypeSelector()
        );
        findViewById(R.id.buttonManagerProfileScheduleEdit).setOnClickListener(view ->
                quickNoteDialogController.show(
                        ManagerQuickNoteType.SCHEDULE,
                        currentProfile == null ? "" : currentProfile.getAvailabilitySummary(),
                        this
                )
        );
        managerProfileContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadProfile();
    }

    @Override
    public void onSave(ManagerQuickNoteType noteType, String value, AlertDialog dialog) {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        setLoading(true);
        RepositoryCallback<ManagerHomeProfile> callback = new RepositoryCallback<ManagerHomeProfile>() {
            @Override
            public void onSuccess(ManagerHomeProfile result) {
                currentProfile = result;
                dialog.dismiss();
                Toast.makeText(
                        ManagerProfileActivity.this,
                        noteType == ManagerQuickNoteType.DOCUMENT
                                ? R.string.manager_action_docs_saved
                                : R.string.manager_action_schedule_saved,
                        Toast.LENGTH_SHORT
                ).show();
                loadOverview();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(ManagerProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        };

        if (noteType == ManagerQuickNoteType.DOCUMENT) {
            managerRepository.saveManagerDocumentSummary(currentUser.getId(), value, callback);
            return;
        }
        managerRepository.saveManagerAvailabilitySummary(currentUser.getId(), value, callback);
    }

    private void openDocumentTypeSelector() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        if (currentProfile == null
                || TextUtils.isEmpty(currentProfile.getDocumentSummary())) {
            Toast.makeText(
                    this,
                    R.string.manager_profile_document_upload_requires_summary,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        CharSequence[] items = new CharSequence[]{
                getString(R.string.manager_profile_document_type_id_card),
                getString(R.string.manager_document_registration_document_nursing_or_elderly_care_license),
                getString(R.string.manager_profile_document_type_criminal_record)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.manager_profile_document_upload_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 1) {
                        openLicenseTypeSelector();
                        return;
                    }
                    pendingDocumentFileType = which == 0
                            ? ManagerDocumentFileType.ID_CARD
                            : ManagerDocumentFileType.CRIMINAL_RECORD;
                    openDocumentPicker();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openLicenseTypeSelector() {
        String[] options = new String[]{
                getString(R.string.manager_document_registration_document_nursing_license),
                getString(R.string.manager_document_registration_document_elderly_care_license)
        };
        ManagerDocumentFileType[] fileTypes = new ManagerDocumentFileType[]{
                ManagerDocumentFileType.HEALTH_CERTIFICATE,
                ManagerDocumentFileType.LICENSE
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.manager_document_registration_document_nursing_or_elderly_care_license)
                .setItems(options, (dialog, which) -> {
                    pendingDocumentFileType = fileTypes[which];
                    openDocumentPicker();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openDocumentPicker() {
        documentPickerLauncher.launch(new String[]{"application/pdf", "image/*"});
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
                    public void onSuccess(ManagerDocumentFileMetadata uploadedMetadata) {
                        saveDocumentFileMetadata(selectedFileType, uploadedMetadata);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void saveDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            ManagerDocumentFileMetadata documentFileMetadata
    ) {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        managerRepository.saveManagerDocumentFileMetadata(
                currentUser.getId(),
                documentFileMetadata,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        currentProfile = result;
                        Toast.makeText(
                                ManagerProfileActivity.this,
                                getString(
                                        R.string.manager_profile_document_upload_saved,
                                        getDocumentTypeLabel(fileType)
                                ),
                                Toast.LENGTH_SHORT
                        ).show();
                        loadOverview();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(ManagerProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void loadProfile() {
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
        managerRepository.getManagerDocumentOverview(currentUser.getId(), new RepositoryCallback<ManagerDocumentOverview>() {
            @Override
            public void onSuccess(ManagerDocumentOverview result) {
                currentProfile = result.getProfile();
                setLoading(false);
                hideBlockingState();
                managerProfileContentContainer.setVisibility(View.VISIBLE);
                managerProfileBinder.bindScreen(managerProfileCoordinator.createScreenModel(
                        currentUser,
                        result,
                        managerRepository.isFirebaseBacked()
                ));
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressManagerProfile.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_manager_profile_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.feature_manager_profile_title)),
                body,
                getString(R.string.state_action_retry),
                view -> loadProfile(),
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
                managerProfileStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerProfileContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(managerProfileStatePanel);
        managerProfileContentContainer.setVisibility(View.VISIBLE);
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
            return getString(R.string.manager_profile_document_type_id_card);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return getString(R.string.manager_document_registration_document_nursing_license);
        }
        return getString(R.string.manager_profile_document_type_criminal_record);
    }
}
