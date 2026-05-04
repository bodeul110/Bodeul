package com.example.bodeul.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;

/**
 * 서비스 진입 전에 현재 버전의 보안/저장 원칙을 설명하는 안내 화면이다.
 */
public class PermissionGuideActivity extends AppCompatActivity {
    private final ActivityResultLauncher<String[]> permissionRequestLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> handlePermissionRequestFinished()
            );

    private PermissionGuidePreferences permissionGuidePreferences;
    private PermissionGuideCatalog permissionGuideCatalog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);

        permissionGuidePreferences = new PermissionGuidePreferences(this);
        permissionGuideCatalog = new PermissionGuideCatalog();

        ViewGroup permissionContainer = findViewById(R.id.layoutPermissionItems);
        PermissionGuideItemBinder itemBinder = new PermissionGuideItemBinder(getLayoutInflater());
        itemBinder.bindItems(permissionContainer, permissionGuideCatalog.getItems());

        findViewById(R.id.buttonPermissionClose).setOnClickListener(view -> skipGuide());
        findViewById(R.id.buttonPermissionConfirm).setOnClickListener(view -> requestPermissions());
    }

    private void skipGuide() {
        permissionGuidePreferences.markCompleted();
        openRoleSelection();
    }

    private void requestPermissions() {
        String[] ungrantedPermissions = permissionGuideCatalog.collectUngrantedPermissions(this);
        if (ungrantedPermissions.length == 0) {
            permissionGuidePreferences.markCompleted();
            openRoleSelection();
            return;
        }

        permissionRequestLauncher.launch(ungrantedPermissions);
    }

    private void handlePermissionRequestFinished() {
        permissionGuidePreferences.markCompleted();
        if (permissionGuideCatalog.hasMissingRequiredPermission(this)) {
            Toast.makeText(
                    this,
                    R.string.permission_required_missing_notice,
                    Toast.LENGTH_LONG
            ).show();
        }
        openRoleSelection();
    }

    private void openRoleSelection() {
        startActivity(new Intent(this, RoleSelectionActivity.class));
        finish();
    }
}
