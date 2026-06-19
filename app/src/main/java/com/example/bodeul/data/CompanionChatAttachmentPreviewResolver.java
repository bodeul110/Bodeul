package com.example.bodeul.data;

import android.net.Uri;

import com.example.bodeul.domain.model.CompanionChatAttachment;

/**
 * 안심 채팅 첨부 메타데이터를 실제 미리보기 가능한 URI로 변환한다.
 */
public interface CompanionChatAttachmentPreviewResolver {
    void resolvePreviewUri(
            CompanionChatAttachment attachment,
            RepositoryCallback<Uri> callback
    );
}
