package com.example.bodeul.data;

import android.net.Uri;

import com.example.bodeul.domain.model.CompanionChatAttachment;

/**
 * 안심 채팅 첨부 파일을 Storage에 올리고 메시지 저장용 메타데이터를 만든다.
 */
public interface CompanionChatAttachmentUploader {
    void uploadAttachment(
            String sessionId,
            Uri fileUri,
            RepositoryCallback<CompanionChatAttachment> callback
    );

    boolean isFirebaseBacked();
}
