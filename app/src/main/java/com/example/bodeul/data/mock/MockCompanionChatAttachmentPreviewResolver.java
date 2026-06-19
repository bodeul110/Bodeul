package com.example.bodeul.data.mock;

import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.CompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionChatAttachment;

/**
 * 목업 모드에서는 첨부 메타데이터에 담긴 SAF URI를 그대로 연다.
 */
public final class MockCompanionChatAttachmentPreviewResolver
        implements CompanionChatAttachmentPreviewResolver {
    @Override
    public void resolvePreviewUri(
            CompanionChatAttachment attachment,
            RepositoryCallback<Uri> callback
    ) {
        if (attachment == null || TextUtils.isEmpty(attachment.getPreviewUri())) {
            callback.onError("다시 열 수 있는 첨부 파일 경로가 없습니다.");
            return;
        }
        callback.onSuccess(Uri.parse(attachment.getPreviewUri()));
    }
}
