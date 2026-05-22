package com.example.bodeul.ui.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 매니저 동행 진행 화면의 인증, 저장, 라우팅만 담당한다.
 */
public class ManagerGuideActivity extends AppCompatActivity {
    private static final int REQUEST_FINE_LOCATION = 1001;

    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerGuideCoordinator managerGuideCoordinator;
    private ManagerGuideDashboardBinder managerGuideDashboardBinder;
    private User currentUser;

    private View managerGuideStatePanel;
    private View managerGuideContentContainer;
    private TextInputEditText inputGuideLocationSummary;
    private TextInputEditText inputGuardianUpdate;
    private TextInputEditText inputGuidePhotoNote;
    private TextInputEditText inputMedicationNote;
    private TextInputEditText inputPharmacySummary;
    private TextInputEditText inputReportSummary;
    private TextInputEditText inputReportTreatment;
    private TextInputEditText inputNextVisit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_guide);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerGuideCoordinator = new ManagerGuideCoordinator(
                this,
                new ManagerGuidePresentationFormatter(this)
        );

        managerGuideStatePanel = findViewById(R.id.managerGuideStatePanel);
        managerGuideContentContainer = findViewById(R.id.managerGuideContentContainer);
        inputGuideLocationSummary = findViewById(R.id.inputGuideLocationSummary);
        inputGuardianUpdate = findViewById(R.id.inputGuardianUpdate);
        inputGuidePhotoNote = findViewById(R.id.inputGuidePhotoNote);
        inputMedicationNote = findViewById(R.id.inputMedicationNote);
        inputPharmacySummary = findViewById(R.id.inputPharmacySummary);
        inputReportSummary = findViewById(R.id.inputReportSummary);
        inputReportTreatment = findViewById(R.id.inputReportTreatment);
        inputNextVisit = findViewById(R.id.inputNextVisit);

        managerGuideDashboardBinder = new ManagerGuideDashboardBinder(
                LayoutInflater.from(this),
                new ManagerGuideStageItemBinder(this),
                findViewById(R.id.textGuideMode),
                findViewById(R.id.textGuideTitle),
                findViewById(R.id.textGuideSubtitle),
                findViewById(R.id.textGuideHeroBadge),
                findViewById(R.id.textGuideHeroTitle),
                findViewById(R.id.textGuideHeroBody),
                findViewById(R.id.textGuideHeroNote),
                findViewById(R.id.guideMapActionContainer),
                new ManagerGuideMapActionBinder(this::openMapFallback),
                (LinearLayout) findViewById(R.id.guideStageRailContainer),
                findViewById(R.id.textGuideFocusBadge),
                findViewById(R.id.textGuideFocusTitle),
                findViewById(R.id.textGuideFocusBody),
                findViewById(R.id.textGuideFocusPreviewLabel),
                findViewById(R.id.textGuideFocusPreviewBody),
                findViewById(R.id.viewGuideFocusPreview),
                inputGuideLocationSummary,
                inputGuardianUpdate,
                inputGuidePhotoNote,
                inputMedicationNote,
                inputPharmacySummary,
                inputReportSummary,
                inputReportTreatment,
                inputNextVisit,
                (MaterialButton) findViewById(R.id.buttonAdvanceGuide),
                (MaterialButton) findViewById(R.id.buttonSaveLocationSummary),
                (MaterialButton) findViewById(R.id.buttonShareCurrentLocation),
                (MaterialButton) findViewById(R.id.buttonSaveGuardianUpdate),
                (MaterialButton) findViewById(R.id.buttonSaveGuidePhotoNote),
                (MaterialButton) findViewById(R.id.buttonSaveMedicationNote),
                (MaterialButton) findViewById(R.id.buttonSavePharmacySummary),
                (MaterialButton) findViewById(R.id.buttonTogglePharmacyCompleted),
                (MaterialButton) findViewById(R.id.buttonSubmitReport)
        );

        findViewById(R.id.buttonBackGuide).setOnClickListener(view -> finish());
        findViewById(R.id.buttonAdvanceGuide).setOnClickListener(view -> advanceStep());
        findViewById(R.id.buttonSaveLocationSummary).setOnClickListener(view -> saveLocationSummary());
        findViewById(R.id.buttonSaveGuardianUpdate).setOnClickListener(view -> saveGuardianUpdate());
        findViewById(R.id.buttonSaveGuidePhotoNote).setOnClickListener(view -> saveFieldPhotoNote());
        findViewById(R.id.buttonSaveMedicationNote).setOnClickListener(view -> saveMedicationNote());
        findViewById(R.id.buttonSavePharmacySummary).setOnClickListener(view -> savePharmacySummary());
        findViewById(R.id.buttonTogglePharmacyCompleted).setOnClickListener(view -> togglePharmacyCompleted());
        findViewById(R.id.buttonSubmitReport).setOnClickListener(view -> submitReport());
        findViewById(R.id.buttonGuideOpenChat).setOnClickListener(view -> openCompanionChat());
        findViewById(R.id.buttonShareCurrentLocation).setOnClickListener(view -> shareCurrentLocation());

        bindEmptyState();
    }

    private void openMapFallback(ManagerGuideMapActionModel model) {
        if (!ManagerGuideMapFallbackLauncher.open(this, model)) {
            Toast.makeText(this, R.string.guide_map_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openCompanionChat() {
        startActivity(CompanionChatActivity.createIntent(this));
    }

    private void shareCurrentLocation() {
        if (currentUser == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
            return;
        }
        ManagerCurrentLocationSharer.share(this, new ManagerCurrentLocationSharer.Callback() {
            @Override
            public void onSuccess(double latitude, double longitude, String summary) {
                managerRepository.shareCurrentLocation(
                        currentUser.getId(),
                        latitude,
                        longitude,
                        summary,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard result) {
                                Toast.makeText(
                                        ManagerGuideActivity.this,
                                        R.string.guide_share_location_done,
                                        Toast.LENGTH_SHORT
                                ).show();
                                bindDashboard(result);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.MANAGER) {
                    showPermissionState();
                    return;
                }

                currentUser = result;
                hideBlockingState();
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                showAuthState();
            }
        });
    }

    private void loadDashboard() {
        if (currentUser == null) {
            showAuthState();
            return;
        }

        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                hideBlockingState();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                if (isNoActiveSession(message)) {
                    hideBlockingState();
                    bindEmptyState();
                    return;
                }
                bindEmptyState();
                showLoadErrorState(message);
            }
        });
    }

    private void bindDashboard(@Nullable ManagerDashboard dashboard) {
        managerGuideDashboardBinder.bindScreen(
                managerGuideCoordinator.createScreenModel(
                        dashboard,
                        managerRepository.isFirebaseBacked()
                )
        );
    }

    private void bindEmptyState() {
        bindDashboard(null);
    }

    private void advanceStep() {
        if (currentUser == null) {
            return;
        }

        managerRepository.advanceCurrentStep(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_step_advanced, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveLocationSummary() {
        if (currentUser == null) {
            return;
        }

        String summary = valueOf(inputGuideLocationSummary);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveLocationSummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_location_saved, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveGuardianUpdate() {
        if (currentUser == null) {
            return;
        }

        String message = valueOf(inputGuardianUpdate);
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveGuardianUpdate(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_guardian_save, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveFieldPhotoNote() {
        if (currentUser == null) {
            return;
        }

        String note = valueOf(inputGuidePhotoNote);
        if (TextUtils.isEmpty(note)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveFieldPhotoNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_photo_saved, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveMedicationNote() {
        if (currentUser == null) {
            return;
        }

        String note = valueOf(inputMedicationNote);
        if (TextUtils.isEmpty(note)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveMedicationNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_medication_save, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePharmacySummary() {
        if (currentUser == null) {
            return;
        }

        String summary = valueOf(inputPharmacySummary);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.savePharmacySummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_pharmacy_save_done, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePharmacyCompleted() {
        if (currentUser == null) {
            return;
        }

        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isPharmacyCompleted();
                managerRepository.updatePharmacyCompleted(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                Toast.makeText(
                                        ManagerGuideActivity.this,
                                        nextValue
                                                ? R.string.guide_pharmacy_complete_done
                                                : R.string.guide_pharmacy_incomplete_done,
                                        Toast.LENGTH_SHORT
                                ).show();
                                bindDashboard(updated);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitReport() {
        if (currentUser == null) {
            return;
        }

        String summary = valueOf(inputReportSummary);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.submitSessionReport(
                currentUser.getId(),
                summary,
                valueOf(inputReportTreatment),
                valueOf(inputMedicationNote),
                valueOf(inputNextVisit),
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        Toast.makeText(ManagerGuideActivity.this, R.string.guide_report_submit, Toast.LENGTH_SHORT).show();
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_FINE_LOCATION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            shareCurrentLocation();
            return;
        }
        Toast.makeText(this, R.string.guide_share_location_permission_denied, Toast.LENGTH_SHORT).show();
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.guide_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.guide_title)),
                body,
                getString(R.string.state_action_retry),
                view -> loadDashboard(),
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
                managerGuideStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerGuideContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(managerGuideStatePanel);
        managerGuideContentContainer.setVisibility(View.VISIBLE);
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

    private boolean isNoActiveSession(String message) {
        return ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
