package com.example.bodeul.ui.auth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.bodeul.R;

import java.util.ArrayList;
import java.util.List;

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
        return new String[0];
    }

    public boolean hasMissingRequiredPermission(Context context) {
        return false;
    }

    @NonNull
    private List<PermissionGuideItem> createItems() {
        List<PermissionGuideItem> result = new ArrayList<>();
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_info_details,
                R.color.bodeul_soft_blue,
                R.string.permission_data_title,
                R.string.permission_data_description,
                false
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_menu_save,
                R.color.bodeul_soft_yellow,
                R.string.permission_document_title,
                R.string.permission_document_description,
                false
        ));
        result.add(new PermissionGuideItem(
                android.R.drawable.ic_lock_lock,
                R.color.bodeul_soft_green,
                R.string.permission_future_title,
                R.string.permission_future_description,
                false
        ));
        return result;
    }
}
