package com.example.bodeul.data.firebase;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Locale;

/**
 * Firebase Storage에 매니저 서류 원본을 업로드하고, Firestore에 저장할 메타데이터를 만든다.
 */
public final class FirebaseManagerDocumentStorageUploader implements ManagerDocumentStorageUploader {
    private final Context appContext;
    private final FirebaseStorage firebaseStorage;

    public FirebaseManagerDocumentStorageUploader(Context context) {
        this.appContext = context.getApplicationContext();
        this.firebaseStorage = FirebaseStorage.getInstance();
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

        ContentResolver resolver = appContext.getContentResolver();
        String fileName = resolveFileName(resolver, fileUri);
        String sanitizedFileName = sanitizeFileName(fileName);
        String contentType = normalizeText(resolver.getType(fileUri));
        String fullPath = String.format(
                Locale.KOREA,
                "manager-documents/%s/%s/%d-%s",
                managerUserId,
                fileType.getStorageKey(),
                System.currentTimeMillis(),
                sanitizedFileName
        );
        StorageReference storageReference = firebaseStorage.getReference().child(fullPath);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(contentType)
                .build();

        storageReference.putFile(fileUri, metadata)
                .addOnSuccessListener(taskSnapshot -> {
                    StorageMetadata uploadedMetadata = taskSnapshot.getMetadata();
                    long uploadedAtMillis = uploadedMetadata == null
                            ? System.currentTimeMillis()
                            : Math.max(uploadedMetadata.getUpdatedTimeMillis(), System.currentTimeMillis());
                    callback.onSuccess(new ManagerDocumentFileMetadata(
                            fileType,
                            fullPath,
                            sanitizedFileName,
                            uploadedMetadata == null
                                    ? contentType
                                    : normalizeText(uploadedMetadata.getContentType()),
                            uploadedAtMillis
                    ));
                })
                .addOnFailureListener(exception ->
                        callback.onError("원본 서류 파일을 업로드하지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private String resolveFileName(ContentResolver resolver, Uri fileUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(fileUri, null, null, null, null);
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
            // SAF 메타데이터 조회가 실패해도 마지막 path segment로 이어간다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String lastSegment = fileUri.getLastPathSegment();
        if (TextUtils.isEmpty(lastSegment)) {
            return "manager-document";
        }
        return lastSegment;
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
