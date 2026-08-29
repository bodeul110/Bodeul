package com.example.bodeul.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 매니저 원본 서류 업로드 전에 형식과 크기를 같은 기준으로 검사한다.
 */
public final class ManagerDocumentUploadPolicy {
    public static final long MAX_FILE_SIZE_BYTES = UploadFileSizePolicy.MAX_FILE_SIZE_BYTES;

    private ManagerDocumentUploadPolicy() {
    }

    @Nullable
    public static String validate(ContentResolver resolver, @Nullable Uri fileUri) {
        if (resolver == null || fileUri == null) {
            return "업로드할 서류 정보가 올바르지 않습니다.";
        }

        String contentType = resolveContentType(resolver, fileUri);
        String contentTypeError = validateContentType(contentType);
        if (contentTypeError != null) {
            return contentTypeError;
        }

        UploadFileSizePolicy.Result sizeResult = UploadFileSizePolicy.validate(
                resolver,
                fileUri,
                MAX_FILE_SIZE_BYTES
        );
        return validateFileSize(sizeResult);
    }

    @Nullable
    static String validateContentType(@Nullable String contentType) {
        if (!isAllowedContentType(contentType)) {
            return "원본 서류는 JPEG, PNG 또는 WebP 이미지로만 업로드할 수 있습니다.";
        }

        return null;
    }

    @Nullable
    static String validateFileSize(UploadFileSizePolicy.Result sizeResult) {
        if (sizeResult.isTooLarge()) {
            return "원본 서류는 10MB 이하 파일만 업로드할 수 있습니다.";
        }
        if (sizeResult.isUnknown()) {
            return "원본 서류 파일 크기를 확인할 수 없습니다. 다시 선택해주세요.";
        }

        return null;
    }

    public static String resolveContentType(ContentResolver resolver, @Nullable Uri fileUri) {
        if (resolver == null || fileUri == null) {
            return "";
        }

        String contentType = normalizeText(resolver.getType(fileUri));
        if (!contentType.isEmpty()) {
            return contentType;
        }

        // file:// URI처럼 MIME 조회가 비는 경우 확장자로 한 번 더 판별한다.
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileUri.toString());
        if (TextUtils.isEmpty(extension)) {
            String path = normalizeText(fileUri.getPath());
            int separatorIndex = path.lastIndexOf('.');
            if (separatorIndex >= 0 && separatorIndex < path.length() - 1) {
                extension = path.substring(separatorIndex + 1);
            }
        }

        if (TextUtils.isEmpty(extension)) {
            return "";
        }
        return normalizeText(
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        extension.toLowerCase(Locale.ROOT)
                )
        );
    }

    public static boolean isAllowedContentType(@Nullable String contentType) {
        String normalizedContentType = normalizeText(contentType);
        if (normalizedContentType.isEmpty()) {
            return false;
        }
        return "image/jpeg".equals(normalizedContentType)
                || "image/png".equals(normalizedContentType)
                || "image/webp".equals(normalizedContentType);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
