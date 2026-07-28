package com.example.bodeul.data.coreapi;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionChatAttachment;

/**
 * Core API multipart 전송 전에 SAF 파일을 검증하고 로컬 첨부 메타데이터를 준비한다.
 */
public final class CoreApiCompanionChatAttachmentUploader
        implements CompanionChatAttachmentUploader {
    private final ContentResolver contentResolver;

    public CoreApiCompanionChatAttachmentUploader(Context context) {
        contentResolver = context.getApplicationContext().getContentResolver();
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
        String validationError = CompanionChatAttachmentUploadPolicy.validate(
                contentResolver,
                fileUri);
        if (!TextUtils.isEmpty(validationError)) {
            callback.onError(validationError);
            return;
        }
        String fileName = CompanionChatAttachmentUploadPolicy.resolveFileName(
                contentResolver,
                fileUri);
        String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(
                contentResolver,
                fileUri);
        long sizeBytes = CompanionChatAttachmentUploadPolicy.resolveFileSize(
                contentResolver,
                fileUri);
        callback.onSuccess(new CompanionChatAttachment(
                fileUri.toString(),
                fileName,
                contentType,
                System.currentTimeMillis(),
                sizeBytes,
                fileUri.toString()));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }
}
