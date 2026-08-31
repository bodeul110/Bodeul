package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.ManagerDocumentFileType;

/**
 * Firestore 서류 메타데이터와 경로 별칭이 같은 매니저 원본을 가리키는지 확인한다.
 */
public final class ManagerDocumentReferencePolicy {
    private ManagerDocumentReferencePolicy() {
    }

    public static boolean isConsistent(
            @Nullable String managerUserId,
            @Nullable ManagerDocumentFileType fileType,
            @Nullable String metadataPath,
            @Nullable String pathMapValue,
            boolean aliasPresent,
            @Nullable String aliasValue
    ) {
        if (!isSafePath(managerUserId, fileType, metadataPath)
                || !value(metadataPath).equals(value(pathMapValue))) {
            return false;
        }

        if (fileType == ManagerDocumentFileType.NURSING_LICENSE) {
            return !aliasPresent;
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return !aliasPresent || value(metadataPath).equals(value(aliasValue));
        }
        if (fileType == ManagerDocumentFileType.ID_CARD
                || fileType == ManagerDocumentFileType.LICENSE
                || fileType == ManagerDocumentFileType.CRIMINAL_RECORD) {
            return aliasPresent && value(metadataPath).equals(value(aliasValue));
        }
        return false;
    }

    private static boolean isSafePath(
            @Nullable String managerUserId,
            @Nullable ManagerDocumentFileType fileType,
            @Nullable String fullPath
    ) {
        String userId = value(managerUserId);
        String path = value(fullPath);
        if (fileType == null || !userId.matches("^[A-Za-z0-9_-]{1,128}$")) {
            return false;
        }

        String prefix = "manager-documents/"
                + userId
                + "/"
                + fileType.getStorageKey()
                + "/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String fileName = path.substring(prefix.length());
        return !fileName.isEmpty() && !fileName.contains("/");
    }

    private static String value(@Nullable String value) {
        return value == null ? "" : value;
    }
}
