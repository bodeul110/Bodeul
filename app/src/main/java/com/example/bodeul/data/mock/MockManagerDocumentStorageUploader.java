package com.example.bodeul.data.mock;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;

/**
 * Firebase 없이도 업로드 흐름을 점검할 수 있게 원본 서류 메타데이터만 흉내 낸다.
 */
public final class MockManagerDocumentStorageUploader implements ManagerDocumentStorageUploader {
    private final Context appContext;

    public MockManagerDocumentStorageUploader(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void uploadDocument(
            String managerUserId,
            ManagerDocumentFileType fileType,
            Uri fileUri,
            RepositoryCallback<ManagerDocumentFileMetadata> callback
    ) {
        if (TextUtils.isEmpty(managerUserId) || fileType == null || fileUri == null) {
            callback.onError("업로드할 서류 정보가 올바르지 않습니다.");
            return;
        }

        String fileName = resolveFileName(fileUri);
        String contentType = normalizeText(appContext.getContentResolver().getType(fileUri));
        long uploadedAtMillis = System.currentTimeMillis();
        callback.onSuccess(new ManagerDocumentFileMetadata(
                fileType,
                "manager-documents/" + managerUserId + "/" + fileType.getStorageKey() + "/" + uploadedAtMillis + "-" + fileName,
                fileName,
                contentType,
                uploadedAtMillis
        ));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private String resolveFileName(Uri fileUri) {
        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(fileUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String displayName = normalizeText(cursor.getString(nameIndex));
                    if (!displayName.isEmpty()) {
                        return displayName;
                    }
                }
            }
        } catch (Exception ignored) {
            // 목업 모드에서는 파일명 추출 실패 시 마지막 segment로 이어간다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String lastSegment = fileUri.getLastPathSegment();
        if (TextUtils.isEmpty(lastSegment)) {
            return "manager-document";
        }
        return sanitizeFileName(lastSegment);
    }

    private String sanitizeFileName(String rawFileName) {
        String normalized = normalizeText(rawFileName);
        if (normalized.isEmpty()) {
            return "manager-document";
        }
        return normalized
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .replace(" ", "_");
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
