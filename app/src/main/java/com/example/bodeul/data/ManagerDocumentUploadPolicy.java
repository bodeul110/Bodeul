package com.example.bodeul.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * 매니저 원본 서류 업로드 전에 형식과 크기를 같은 기준으로 검사한다.
 */
public final class ManagerDocumentUploadPolicy {
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private ManagerDocumentUploadPolicy() {
    }

    @Nullable
    public static String validate(ContentResolver resolver, @Nullable Uri fileUri) {
        if (resolver == null || fileUri == null) {
            return "업로드할 서류 정보가 올바르지 않습니다.";
        }

        String contentType = normalizeText(resolver.getType(fileUri));
        if (!isAllowedContentType(contentType)) {
            return "원본 서류는 PDF 또는 이미지 파일만 업로드할 수 있습니다.";
        }

        long fileSize = resolveFileSize(resolver, fileUri);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return "원본 서류는 10MB 이하 파일만 업로드할 수 있습니다.";
        }

        return null;
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
            // SAF 메타데이터 조회가 실패하면 크기 제한 검사는 생략한다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
