package com.example.bodeul.data;

import android.net.Uri;

import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;

/**
 * 매니저가 선택한 원본 서류 파일을 Storage에 올리고 메타데이터를 돌려준다.
 */
public interface ManagerDocumentStorageUploader {
    void uploadDocument(
            String managerUserId,
            ManagerDocumentFileType fileType,
            Uri fileUri,
            RepositoryCallback<ManagerDocumentFileMetadata> callback
    );

    boolean isFirebaseBacked();
}
