package com.example.bodeul.ui.auth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.util.NotificationPermissionSupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 권한 안내 화면에서 보여줄 카드 목록과 실제 요청 권한 집합을 관리한다.
 */
public final class PermissionGuideCatalog {
    @SuppressLint("InlinedApi")
    private static final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";

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
        LinkedHashSet<String> ungrantedPermissions = new LinkedHashSet<>();
        for (PermissionGuideItem item : items) {
            for (String manifestPermission : item.getManifestPermissions()) {
                if (!isSupportedRuntimePermission(manifestPermission)) {
                    continue;
                }
                if (ContextCompat.checkSelfPermission(context, manifestPermission)
                        != PackageManager.PERMISSION_GRANTED) {
                    ungrantedPermissions.add(manifestPermission);
                }
            }
        }
        return ungrantedPermissions.toArray(new String[0]);
    }

    public boolean hasMissingRequiredPermission(Context context) {
        for (PermissionGuideItem item : items) {
            if (!item.isRequired()) {
                continue;
            }
            for (String manifestPermission : item.getManifestPermissions()) {
                if (!isSupportedRuntimePermission(manifestPermission)) {
                    continue;
                }
                if (ContextCompat.checkSelfPermission(context, manifestPermission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasPendingRuntimePermissionRequest(Context context) {
        return collectUngrantedPermissions(context).length > 0;
    }

    @NonNull
    private List<PermissionGuideItem> createItems() {
        List<PermissionGuideItem> result = new ArrayList<>();
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_info_details,
                R.color.bodeul_soft_blue,
                R.string.permission_item_badge_data,
                R.string.permission_data_title,
                R.string.permission_data_description,
                false
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_popup_reminder,
                R.color.bodeul_soft_green,
                R.string.permission_item_badge_notification,
                R.string.permission_notification_title,
                R.string.permission_notification_description,
                false,
                POST_NOTIFICATIONS_PERMISSION
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_save,
                R.color.bodeul_soft_yellow,
                R.string.permission_item_badge_document,
                R.string.permission_document_title,
                R.string.permission_document_description,
                false
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_lock_lock,
                R.color.bodeul_soft_green,
                R.string.permission_item_badge_future,
                R.string.permission_future_title,
                R.string.permission_future_description,
                false
        ));
        return result;
    }

    private boolean isSupportedRuntimePermission(String manifestPermission) {
        if (POST_NOTIFICATIONS_PERMISSION.equals(manifestPermission)) {
            return NotificationPermissionSupport.isRuntimePermissionRequired();
        }
        return true;
    }
}
