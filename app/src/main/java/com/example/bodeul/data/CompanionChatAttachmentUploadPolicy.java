package com.example.bodeul.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

/**
 * 안심 채팅 첨부 파일의 형식과 크기를 공통 기준으로 검사한다.
 */
public final class CompanionChatAttachmentUploadPolicy {
    public static final long MAX_FILE_SIZE_BYTES = UploadFileSizePolicy.MAX_FILE_SIZE_BYTES;

    private CompanionChatAttachmentUploadPolicy() {
    }

    @Nullable
    public static String validate(ContentResolver resolver, @Nullable Uri fileUri) {
        if (resolver == null || fileUri == null) {
            return "첨부할 파일 정보를 확인하지 못했습니다.";
        }

        String contentType = resolveContentType(resolver, fileUri);
        if (!isAllowedContentType(contentType)) {
            return "안심 채팅 첨부는 PDF 또는 이미지 파일만 보낼 수 있습니다.";
        }

        UploadFileSizePolicy.Result sizeResult = UploadFileSizePolicy.validate(
                resolver,
                fileUri,
                MAX_FILE_SIZE_BYTES
        );
        if (sizeResult.isTooLarge()) {
            return "안심 채팅 첨부는 10MB 이하 파일만 보낼 수 있습니다.";
        }
        if (sizeResult.isUnknown()) {
            return "안심 채팅 첨부 파일 크기를 확인할 수 없습니다. 다시 선택해주세요.";
        }

        return null;
    }

    public static String resolveFileName(ContentResolver resolver, Uri fileUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(fileUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String displayName = normalizeText(cursor.getString(nameIndex));
                    if (!displayName.isEmpty()) {
                        return sanitizeFileName(displayName);
                    }
                }
            }
        } catch (Exception ignored) {
            // SAF 메타데이터 조회가 실패하면 마지막 path segment로 대체한다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String lastSegment = fileUri.getLastPathSegment();
        if (TextUtils.isEmpty(lastSegment)) {
            return "companion-chat-attachment";
        }
        return sanitizeFileName(lastSegment);
    }

    public static String resolveContentType(ContentResolver resolver, Uri fileUri) {
        String resolverContentType = normalizeText(resolver.getType(fileUri));
        if (!resolverContentType.isEmpty()) {
            return resolverContentType;
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(fileUri.toString());
        return normalizeText(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
    }

    private static boolean isAllowedContentType(String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return false;
        }
        return "application/pdf".equals(contentType)
                || contentType.startsWith("image/");
    }

    private static String sanitizeFileName(String rawFileName) {
        String normalized = normalizeText(rawFileName);
        if (normalized.isEmpty()) {
            return "companion-chat-attachment";
        }
        return normalized
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .replace(" ", "_");
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
