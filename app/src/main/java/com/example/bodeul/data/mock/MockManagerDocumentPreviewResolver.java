package com.example.bodeul.data.mock;

import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;

/**
 * 목업 모드에서는 저장해둔 SAF URI를 그대로 다시 연다.
 */
public final class MockManagerDocumentPreviewResolver implements ManagerDocumentPreviewResolver {
    @Override
    public void resolvePreviewUri(
            ManagerDocumentFileMetadata metadata,
            RepositoryCallback<Uri> callback
    ) {
        if (metadata == null || TextUtils.isEmpty(metadata.getPreviewUri())) {
            callback.onError("다시 열 수 있는 서류 원본 경로가 없습니다.");
            return;
        }
        callback.onSuccess(Uri.parse(metadata.getPreviewUri()));
    }
}
