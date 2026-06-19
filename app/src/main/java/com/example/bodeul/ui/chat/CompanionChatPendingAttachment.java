package com.example.bodeul.ui.chat;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * 전송 전에 채팅 입력창에만 잠시 머무는 첨부 파일 선택 상태다.
 */
public final class CompanionChatPendingAttachment {
    private final Uri fileUri;
    private final String fileName;
    private final String contentType;

    public CompanionChatPendingAttachment(
            @NonNull Uri fileUri,
            @NonNull String fileName,
            @NonNull String contentType
    ) {
        this.fileUri = fileUri;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    @NonNull
    public Uri getFileUri() {
        return fileUri;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    public boolean isImageType() {
        return contentType.startsWith("image/");
    }

    public boolean isPdfType() {
        return "application/pdf".equals(contentType);
    }
}
