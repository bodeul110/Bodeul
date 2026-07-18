package com.example.bodeul.data.mock;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionChatAttachment;

/**
 * 목업 모드에서는 선택한 SAF URI를 그대로 미리보기 가능한 첨부 메타데이터로 만든다.
 */
public final class MockCompanionChatAttachmentUploader implements CompanionChatAttachmentUploader {
    private final Context appContext;

    public MockCompanionChatAttachmentUploader(Context context) {
        this.appContext = context.getApplicationContext();
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
                appContext.getContentResolver(),
                fileUri
        );
        if (!TextUtils.isEmpty(validationError)) {
            callback.onError(validationError);
            return;
        }

        String fileName = CompanionChatAttachmentUploadPolicy.resolveFileName(
                appContext.getContentResolver(),
                fileUri
        );
        String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(
                appContext.getContentResolver(),
                fileUri
        );
        long uploadedAtMillis = System.currentTimeMillis();
        callback.onSuccess(new CompanionChatAttachment(
                "companion-chat-attachments/" + sessionId + "/" + uploadedAtMillis + "-" + fileName,
                fileName,
                contentType,
                uploadedAtMillis,
                CompanionChatAttachmentUploadPolicy.resolveFileSize(
                        appContext.getContentResolver(),
                        fileUri),
                fileUri.toString()
        ));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }
}
