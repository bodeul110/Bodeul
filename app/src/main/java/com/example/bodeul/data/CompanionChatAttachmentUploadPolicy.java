package com.example.bodeul.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * 안심 채팅 첨부 파일의 형식과 크기를 공통 기준으로 검사한다.
 */
public final class CompanionChatAttachmentUploadPolicy {
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

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

        long fileSize = resolveFileSize(resolver, fileUri);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return "안심 채팅 첨부는 10MB 이하 파일만 보낼 수 있습니다.";
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
        return normalizeText(resolver.getType(fileUri));
    }

    private static boolean isAllowedContentType(String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return false;
        }
        return "application/pdf".equals(contentType)
                || contentType.startsWith("image/");
    }

    private static long resolveFileSize(ContentResolver resolver, Uri fileUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(fileUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
            // SAF 메타데이터 조회가 실패하면 크기 검사를 생략한다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
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
