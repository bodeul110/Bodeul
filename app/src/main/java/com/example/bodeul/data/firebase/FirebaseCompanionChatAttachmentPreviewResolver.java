package com.example.bodeul.data.firebase;

import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.CompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.google.firebase.storage.FirebaseStorage;

/**
 * 안심 채팅 첨부의 Storage 경로를 실제 미리보기 가능한 다운로드 URI로 변환한다.
 */
public final class FirebaseCompanionChatAttachmentPreviewResolver
        implements CompanionChatAttachmentPreviewResolver {
    private final FirebaseStorage firebaseStorage;

    public FirebaseCompanionChatAttachmentPreviewResolver() {
        this.firebaseStorage = FirebaseStorage.getInstance();
    }

    @Override
    public void resolvePreviewUri(
            CompanionChatAttachment attachment,
            RepositoryCallback<Uri> callback
    ) {
        if (attachment == null || TextUtils.isEmpty(attachment.getFullPath())) {
            callback.onError("첨부 파일 경로를 확인하지 못했습니다.");
            return;
        }

        firebaseStorage.getReference()
                .child(attachment.getFullPath())
                .getDownloadUrl()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(exception ->
                        callback.onError("첨부 파일을 불러오지 못했습니다."));
    }
}
