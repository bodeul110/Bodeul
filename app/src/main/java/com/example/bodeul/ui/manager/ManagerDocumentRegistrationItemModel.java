package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.ManagerDocumentFileType;

/**
 * 서류 등록 화면의 각 항목(카드)을 표현하는 데이터 모델이다.
 */
public final class ManagerDocumentRegistrationItemModel {
    @Nullable
    private final ManagerDocumentFileType fileType;
    private final String titleText;
    private final String helperText;
    private final String badgeText;
    private final int badgeBackgroundColorResId;
    private final int badgeTextColorResId;
    private final String fileNameText;
    private final String fileMetaText;
    private final String actionText;

    public ManagerDocumentRegistrationItemModel(
            @Nullable ManagerDocumentFileType fileType,
            String titleText,
            String helperText,
            String badgeText,
            int badgeBackgroundColorResId,
            int badgeTextColorResId,
            String fileNameText,
            String fileMetaText,
            String actionText
    ) {
        this.fileType = fileType;
        this.titleText = titleText;
        this.helperText = helperText;
        this.badgeText = badgeText;
        this.badgeBackgroundColorResId = badgeBackgroundColorResId;
        this.badgeTextColorResId = badgeTextColorResId;
        this.fileNameText = fileNameText;
        this.fileMetaText = fileMetaText;
        this.actionText = actionText;
    }

    @Nullable
    public ManagerDocumentFileType getFileType() {
        return fileType;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getHelperText() {
        return helperText;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public int getBadgeBackgroundColorResId() {
        return badgeBackgroundColorResId;
    }

    public int getBadgeTextColorResId() {
        return badgeTextColorResId;
    }

    public String getFileNameText() {
        return fileNameText;
    }

    public String getFileMetaText() {
        return fileMetaText;
    }

    public String getActionText() {
        return actionText;
    }
}
