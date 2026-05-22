package com.example.bodeul.data;

import android.net.Uri;

import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;

/**
 * 저장된 매니저 서류 메타데이터로 다시 열 수 있는 미리보기 URI를 해석한다.
 */
public interface ManagerDocumentPreviewResolver {
    void resolvePreviewUri(
            ManagerDocumentFileMetadata metadata,
            RepositoryCallback<Uri> callback
    );
}
