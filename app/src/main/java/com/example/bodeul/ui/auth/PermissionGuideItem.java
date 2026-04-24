package com.example.bodeul.ui.auth;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 권한 안내 카드 한 장에 필요한 고정 정보를 담는 모델이다.
 */
public final class PermissionGuideItem {
    @DrawableRes
    private final int iconResId;
    @ColorRes
    private final int iconBackgroundColorResId;
    @StringRes
    private final int titleResId;
    @StringRes
    private final int descriptionResId;
    private final boolean required;
    private final List<String> manifestPermissions;

    public PermissionGuideItem(
            @DrawableRes int iconResId,
            @ColorRes int iconBackgroundColorResId,
            @StringRes int titleResId,
            @StringRes int descriptionResId,
            boolean required,
            String... manifestPermissions
    ) {
        this.iconResId = iconResId;
        this.iconBackgroundColorResId = iconBackgroundColorResId;
        this.titleResId = titleResId;
        this.descriptionResId = descriptionResId;
        this.required = required;
        this.manifestPermissions = Collections.unmodifiableList(
                Arrays.asList(manifestPermissions.clone())
        );
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getIconBackgroundColorResId() {
        return iconBackgroundColorResId;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getDescriptionResId() {
        return descriptionResId;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getManifestPermissions() {
        return manifestPermissions;
    }
}
