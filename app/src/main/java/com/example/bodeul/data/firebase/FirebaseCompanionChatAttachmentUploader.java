package com.example.bodeul.data.firebase;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;

import java.util.Locale;

/**
 * Firebase Storage에 안심 채팅 첨부 파일을 올리고 메시지 메타데이터를 만든다.
 */
public final class FirebaseCompanionChatAttachmentUploader implements CompanionChatAttachmentUploader {
    private final Context appContext;
    private final FirebaseStorage firebaseStorage;

    public FirebaseCompanionChatAttachmentUploader(Context context) {
        this.appContext = context.getApplicationContext();
        this.firebaseStorage = FirebaseStorage.getInstance();
    }

    @Override
    public void uploadAttachment(
            String sessionId,
            Uri fileUri,
            RepositoryCallback<CompanionChatAttachment> callback
    ) {
        if (TextUtils.isEmpty(sessionId) || fileUri == null) {
            callback.onError("첨부 파일 세션 정보를 확인하지 못했습니다.");
            return;
        }

        ContentResolver resolver = appContext.getContentResolver();
        String validationError = CompanionChatAttachmentUploadPolicy.validate(resolver, fileUri);
        if (!TextUtils.isEmpty(validationError)) {
            callback.onError(validationError);
            return;
        }

        String fileName = CompanionChatAttachmentUploadPolicy.resolveFileName(resolver, fileUri);
        String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(resolver, fileUri);
        String fullPath = String.format(
                Locale.KOREA,
                "companion-chat-attachments/%s/%d-%s",
                sessionId,
                System.currentTimeMillis(),
                fileName
        );
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(contentType)
                .build();

        firebaseStorage.getReference()
                .child(fullPath)
                .putFile(fileUri, metadata)
                .addOnSuccessListener(taskSnapshot -> {
                    StorageMetadata uploadedMetadata = taskSnapshot.getMetadata();
                    long uploadedAtMillis = uploadedMetadata == null
                            ? System.currentTimeMillis()
                            : Math.max(uploadedMetadata.getUpdatedTimeMillis(), System.currentTimeMillis());
                    callback.onSuccess(new CompanionChatAttachment(
                            fullPath,
                            fileName,
                            uploadedMetadata == null
                                    ? contentType
                                    : normalizeText(uploadedMetadata.getContentType()),
                            uploadedAtMillis
                    ));
                })
                .addOnFailureListener(exception ->
                        callback.onError("안심 채팅 첨부 파일을 올리지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
