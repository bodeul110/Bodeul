package com.example.bodeul.ui.auth;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.BundleCompat;

import com.example.bodeul.R;
import com.example.bodeul.util.NotificationPermissionSupport;

/**
 * 서비스 진입 전에 현재 버전의 보안/저장 원칙을 설명하는 안내 화면이다.
 */
public class PermissionGuideActivity extends AppCompatActivity {
    private static final String EXTRA_NEXT_INTENT = "next_intent";

    private final ActivityResultLauncher<String[]> permissionRequestLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> handlePermissionRequestFinished()
            );
    private final ActivityResultLauncher<Intent> notificationSettingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> handleNotificationSettingsReturned()
            );

    private PermissionGuidePreferences permissionGuidePreferences;
    private PermissionGuideCatalog permissionGuideCatalog;
    private View notificationSettingsButton;

    public static Intent createIntent(Context context, Intent nextIntent) {
        Intent intent = new Intent(context, PermissionGuideActivity.class);
        intent.putExtra(EXTRA_NEXT_INTENT, nextIntent);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);

        permissionGuidePreferences = new PermissionGuidePreferences(this);
        permissionGuideCatalog = new PermissionGuideCatalog();

        ViewGroup permissionContainer = findViewById(R.id.layoutPermissionItems);
        PermissionGuideItemBinder itemBinder = new PermissionGuideItemBinder(getLayoutInflater());
        itemBinder.bindItems(permissionContainer, permissionGuideCatalog.getItems());

        notificationSettingsButton = findViewById(R.id.buttonPermissionOpenNotificationSettings);
        findViewById(R.id.buttonPermissionClose).setOnClickListener(view -> skipGuide());
        findViewById(R.id.buttonPermissionConfirm).setOnClickListener(view -> requestPermissions());
        notificationSettingsButton.setOnClickListener(view -> openNotificationSettings());
        updateNotificationSettingsButton();
    }

    private void skipGuide() {
        permissionGuidePreferences.markCompleted();
        openNextScreen();
    }

    private void requestPermissions() {
        String[] ungrantedPermissions = permissionGuideCatalog.collectUngrantedPermissions(this);
        if (ungrantedPermissions.length == 0) {
            if (!NotificationPermissionSupport.canPostNotifications(this)) {
                permissionGuidePreferences.markCompleted();
                updateNotificationSettingsButton();
                Toast.makeText(
                        this,
                        R.string.permission_notification_recovery_notice,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            markGuideCompletedIfNotificationResolved();
            openNextScreen();
            return;
        }

        permissionRequestLauncher.launch(ungrantedPermissions);
    }

    private void handlePermissionRequestFinished() {
        markGuideCompletedIfNotificationResolved();
        if (permissionGuideCatalog.hasMissingRequiredPermission(this)) {
            Toast.makeText(
                    this,
                    R.string.permission_required_missing_notice,
                    Toast.LENGTH_LONG
            ).show();
        } else if (permissionGuideCatalog.hasPendingRuntimePermissionRequest(this)) {
            Toast.makeText(
                    this,
                    R.string.permission_notification_recovery_notice,
                    Toast.LENGTH_LONG
            ).show();
        }
        openNextScreen();
    }

    private void openNotificationSettings() {
        permissionGuidePreferences.markCompleted();
        try {
            notificationSettingsLauncher.launch(
                    NotificationPermissionSupport.createNotificationSettingsIntent(this)
            );
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    R.string.permission_notification_settings_unavailable,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleNotificationSettingsReturned() {
        markGuideCompletedIfNotificationResolved();
        if (NotificationPermissionSupport.canPostNotifications(this)) {
            Toast.makeText(
                    this,
                    R.string.permission_notification_enabled_notice,
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    R.string.permission_notification_recovery_notice,
                    Toast.LENGTH_LONG
            ).show();
        }
        openNextScreen();
    }

    private void markGuideCompletedIfNotificationResolved() {
        permissionGuidePreferences.markCompleted();
        if (NotificationPermissionSupport.canPostNotifications(this)) {
            permissionGuidePreferences.markNotificationPromptCompleted();
        }
    }

    private void updateNotificationSettingsButton() {
        boolean shouldShowSettingsButton = permissionGuidePreferences.hasCompletedGuide()
                && !NotificationPermissionSupport.canPostNotifications(this);
        notificationSettingsButton.setVisibility(shouldShowSettingsButton ? View.VISIBLE : View.GONE);
    }

    private void openNextScreen() {
        Intent nextIntent = readNextIntent();
        if (nextIntent == null) {
            nextIntent = new Intent(this, RoleSelectionActivity.class);
        }
        startActivity(nextIntent);
        finish();
    }

    private Intent readNextIntent() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        }
        return BundleCompat.getParcelable(extras, EXTRA_NEXT_INTENT, Intent.class);
    }
}
