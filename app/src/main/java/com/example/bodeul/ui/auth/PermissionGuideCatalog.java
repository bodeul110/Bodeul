package com.example.bodeul.ui.auth;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 권한 안내 화면에서 보여줄 카드 목록과 실제 요청 권한 집합을 관리한다.
 */
public final class PermissionGuideCatalog {
    private final List<PermissionGuideItem> items;

    public PermissionGuideCatalog() {
        items = createItems();
    }

    @NonNull
    public List<PermissionGuideItem> getItems() {
        return items;
    }

    @NonNull
    public String[] collectUngrantedPermissions(Context context) {
        Set<String> permissions = new LinkedHashSet<>();
        for (PermissionGuideItem item : items) {
            for (String permission : item.getManifestPermissions()) {
                if (ContextCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(permission);
                }
            }
        }
        return permissions.toArray(new String[0]);
    }

    public boolean hasMissingRequiredPermission(Context context) {
        for (PermissionGuideItem item : items) {
            if (!item.isRequired()) {
                continue;
            }
            for (String permission : item.getManifestPermissions()) {
                if (ContextCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    private List<PermissionGuideItem> createItems() {
        List<PermissionGuideItem> result = new ArrayList<>();
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_mylocation,
                R.color.bodeul_soft_blue,
                R.string.permission_location_title,
                R.string.permission_location_description,
                true,
                Manifest.permission.ACCESS_FINE_LOCATION
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_camera,
                R.color.bodeul_soft_yellow,
                R.string.permission_camera_title,
                R.string.permission_camera_description,
                true,
                Manifest.permission.CAMERA
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_save,
                R.color.bodeul_soft_green,
                R.string.permission_storage_title,
                R.string.permission_storage_description,
                true,
                buildStoragePermission()
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.stat_sys_data_bluetooth,
                R.color.bodeul_soft_purple,
                R.string.permission_bluetooth_title,
                R.string.permission_bluetooth_description,
                false,
                buildBluetoothPermissions()
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_call,
                R.color.bodeul_soft_blue,
                R.string.permission_phone_title,
                R.string.permission_phone_description,
                false,
                Manifest.permission.CALL_PHONE
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_myplaces,
                R.color.bodeul_soft_yellow,
                R.string.permission_contacts_title,
                R.string.permission_contacts_description,
                false,
                Manifest.permission.READ_CONTACTS
        ));
        return result;
    }

    @NonNull
    private String buildStoragePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @NonNull
    private String[] buildBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return new String[0];
        }
        return new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
        };
    }
}
