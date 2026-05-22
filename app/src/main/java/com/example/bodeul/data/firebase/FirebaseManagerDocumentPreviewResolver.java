package com.example.bodeul.data.firebase;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.google.firebase.storage.FirebaseStorage;

/**
 * Firebase Storage 경로를 실제 미리보기 가능한 다운로드 URI로 바꾼다.
 */
public final class FirebaseManagerDocumentPreviewResolver implements ManagerDocumentPreviewResolver {
    private final FirebaseStorage firebaseStorage;

    public FirebaseManagerDocumentPreviewResolver() {
        this.firebaseStorage = FirebaseStorage.getInstance();
    }

    @Override
    public void resolvePreviewUri(
            ManagerDocumentFileMetadata metadata,
            RepositoryCallback<Uri> callback
    ) {
        if (metadata == null || TextUtils.isEmpty(metadata.getFullPath())) {
            callback.onError("서류 파일 경로를 확인하지 못했습니다.");
            return;
        }

        firebaseStorage.getReference()
                .child(metadata.getFullPath())
                .getDownloadUrl()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(exception ->
                        callback.onError("서류 파일 미리보기를 불러오지 못했습니다."));
    }
}
